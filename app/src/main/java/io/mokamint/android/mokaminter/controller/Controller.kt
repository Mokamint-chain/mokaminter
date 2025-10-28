/*
Copyright 2025 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.mokamint.android.mokaminter.controller

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Log
import androidx.annotation.UiThread
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.hotmoka.crypto.Base58
import io.hotmoka.crypto.Base58ConversionException
import io.hotmoka.crypto.api.Entropy
import io.hotmoka.websockets.api.FailedDeploymentException
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.model.MinerStatus
import io.mokamint.android.mokaminter.view.Mokaminter
import io.mokamint.miner.api.MiningSpecification
import io.mokamint.miner.service.MinerServices
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.math.BigInteger
import java.net.URI
import java.security.spec.InvalidKeySpecException
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * The controller of the MVC triple.
 *
 * @param mvc the MVC triple
 */
class Controller(private val mvc: MVC) {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    /**
     * True if and only if the controller is executing a background task for which the
     * progress bar activity has been requested.
     */
    private val working = AtomicInteger(0)

    val miningServices = MiningServices(mvc)

    companion object {
        private val TAG = Controller::class.simpleName

        /**
         * The interval, in milliseconds, between successive
         * updates of the last updated information of the miners.
         */
        private const val MINERS_REDRAW_INTERVAL = 10_000L // ten seconds

        private val nextId = AtomicInteger(100)
    }

    /**
     * Called when the updater thread of the miners must be scheduled.
     */
    @UiThread
    fun onMinersUpdaterRequested() {
        val minersUpdateRequest =
            OneTimeWorkRequestBuilder<MinersRedrawWorker>()
                .setInputData(workDataOf(Pair(ControllerWorker.VISIBLE_TO_USER, false)))
                .build()

        WorkManager.getInstance(mvc)
            .enqueueUniqueWork("update", ExistingWorkPolicy.REPLACE, minersUpdateRequest)
    }

    @UiThread
    fun hasConnectedServiceFor(miner: Miner): Boolean {
        return miningServices.hasConnectedServiceFor(miner)
    }

    @UiThread
    fun isWorking(): Boolean {
        return working.get() > 0
    }

    @UiThread
    fun onMinersReloadRequested() {
        mainScope.launch {
            val snapshot = ioScope.async { mvc.model.miners.reload() }.await()
            snapshot.forEach { miner, status ->
                if (status.isOn && status.hasPlotReady)
                    miningServices.ensureServiceFor(miner)
            }

            Log.i(TAG, "Reloaded the list of miners")
            mvc.view?.onMinersReloaded()
        }
    }

    @UiThread
    fun onBalancesReloadRequested() {
        mainScope.launch {
            val snapshot = ioScope.async { mvc.model.miners.reload() }.await()
            snapshot.forEach { miner, status ->
                if (status.isOn && status.hasPlotReady)
                    ioScope.launch { fetchBalanceOf(miner) }
            }
        }
    }

    fun fetchBalanceOf(miner: Miner) {
        miningServices.fetchBalanceOf(miner)
    }

    @UiThread
    fun onDeleteRequested(miner: Miner) {
        mainScope.launch {
            val removed = ioScope.async { mvc.model.miners.remove(miner) }.await()
            if (removed) {
                miningServices.stopServiceFor(miner)
                mvc.view?.onDeleted(miner)
                deletePlotOf(miner)
            }
        }
    }

    private fun deletePlotOf(miner: Miner) {
        val filename = "${miner.uuid}.plot"
        if (mvc.deleteFile(filename))
            Log.i(TAG, "Deleted $filename")
        else
            Log.w(TAG, "Failed deleting $filename")
    }

    @UiThread
    fun onTurnOnRequested(miner: Miner) {
        mainScope.launch {
            val done = ioScope.async { mvc.model.miners.markAsOn(miner) }.await()
            if (done) {
                miningServices.ensureServiceFor(miner)
                mvc.view?.onTurnedOn(miner)
            }
        }
    }

    @UiThread
    fun onTurnOffRequested(miner: Miner) {
        mainScope.launch {
            val done = ioScope.async { mvc.model.miners.markAsOff(miner) }.await()
            if (done) {
                miningServices.stopServiceFor(miner)
                mvc.view?.onTurnedOff(miner)
            }
        }
    }

    private fun createNotification(title: String, description: String): Notification {
        return NotificationCompat.Builder(mvc, Mokaminter.NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(R.drawable.ic_active_miner)
            .setOngoing(false)
            .setAutoCancel(false)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .build()
    }

    /**
     * Requests the creation of a miner.
     *
     * @return the identifier of the miner whose creation starts with this call
     */
    @UiThread
    fun onMinerCreationRequested(uri: URI, size: Long, entropy: Entropy?, password: String?, publicKeyBase58: String?): UUID {
        val uuid = UUID.randomUUID()

        safeRunAsIO {
            try {
                val miningSpecification: MiningSpecification

                MinerServices.of(uri, 20_000).use {
                    miningSpecification = it.miningSpecification
                }

                Log.i(TAG, "Fetched the mining specification of $uri:\n$miningSpecification")

                val miner: Miner
                if (publicKeyBase58 != null) {
                    try {
                        miner = Miner(uuid, miningSpecification, uri, size, publicKeyBase58)
                        Log.i(TAG, "Ready to create plot file for miner $miner")
                        mainScope.launch { mvc.view?.onReadyToCreatePlotFor(miner) }
                    } catch (_: InvalidKeySpecException) {
                        mainScope.launch { mvc.view?.notifyUser(mvc.applicationContext.getString(R.string.add_miner_message_invalid_public_key)) }
                    } catch (_: Base58ConversionException) {
                        mainScope.launch { mvc.view?.notifyUser(mvc.applicationContext.getString(R.string.add_miner_message_public_key_not_base58)) }
                    }
                } else if (entropy != null && password != null) {
                    val signatureForDeadlines = miningSpecification.signatureForDeadlines
                    miner = Miner(
                        uuid, miningSpecification, uri, size,
                        Base58.toBase58String(
                            signatureForDeadlines.encodingOf
                                (entropy.keys(password, signatureForDeadlines).public)
                        )
                    )
                    Log.i(TAG, "Ready to create plot file for miner $miner")
                    mainScope.launch { mvc.view?.onReadyToCreatePlotFor(miner) }
                } else {
                    throw IllegalStateException("Nor a public key nor a mnemonic have been provided")
                }
            } catch (_: FailedDeploymentException) {
                mainScope.launch { mvc.view?.notifyUser(mvc.getString(R.string.cannot_contact, uri)) }
            }
        }

        return uuid
    }

    @UiThread
    fun onPlotCreationRequested(miner: Miner) {
        mainScope.launch {
            mvc.view?.onPlotCreationConfirmed(miner)

            val status = MinerStatus(
                BigInteger.ZERO,
                hasPlotReady = false,
                isOn = true,
                -1L
            )

            ioScope.async { mvc.model.miners.add(miner, status) }.await()

            mvc.view?.onAdded(miner)

            val plotCreationRequest =
                OneTimeWorkRequestBuilder<PlotCreationWorker>()
                    .setInputData(
                        workDataOf(
                            Pair(
                                PlotCreationWorker.MINER_UUID,
                                miner.uuid.toString()
                            )
                        )
                    )
                    .build()

            WorkManager.getInstance(mvc)
                .enqueue(plotCreationRequest)
        }
    }

    /**
     * Shared implementation of WorkManager works for this controller.
     */
    abstract class ControllerWorker(mvc: Context, workerParams: WorkerParameters): CoroutineWorker(mvc, workerParams) {

        companion object {
            const val VISIBLE_TO_USER = "visibleToTheUser"
        }

        override suspend fun doWork(): Result {
            val mvc = applicationContext as MVC
            val controller = mvc.controller
            val visibleToUser = inputData.getBoolean(VISIBLE_TO_USER, true)
            if (visibleToUser) {
                controller.working.incrementAndGet()
                controller.mainScope.launch { mvc.view?.onBackgroundStart() }
            }

            try {
                return doWork(mvc)
            }
            catch (_: TimeoutException) {
                Log.w(TAG, "A background work timed-out")
                if (visibleToUser)
                    controller.mainScope.launch { mvc.view?.notifyUser(mvc.getString(R.string.operation_timeout)) }
                return Result.failure()
            } catch (_: CancellationException) {
                return Result.success()
            } catch (t: Throwable) {
                Log.w(TAG, "A background work failed", t)
                if (visibleToUser)
                    controller.mainScope.launch { mvc.view?.notifyUser(t.toString()) }
                return Result.failure()
            }
            finally {
                if (visibleToUser && controller.working.decrementAndGet() == 0)
                    controller.mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }

        protected suspend fun publishForegroundNotification(notification: Notification) {
            val id = nextId.getAndIncrement()

            val foregroundInfo = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                ForegroundInfo(id, notification)
            else
                // more recent Android versions require a finer-grained specification
                // of the foreground service
                ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)

            setForeground(foregroundInfo)
        }

        protected abstract suspend fun doWork(mvc: MVC): Result
    }

    /**
     * This background work redraws the current view periodically,
     * in order, for instance, to keep the last update field updated in real time.
     * Note that this work has only graphical relevance and is consequently not
     * a foreground worker, that would survive also when the activity gets destroyed.
     */
    class MinersRedrawWorker(mvc: Context, workerParams: WorkerParameters): ControllerWorker(mvc, workerParams) {
        override suspend fun doWork(mvc: MVC): Result {
            while (true) {
                delay(MINERS_REDRAW_INTERVAL)
                mvc.controller.mainScope.launch { mvc.view?.onRedrawMiners() }
            }
        }
    }

    /**
     * A background work for the creation of the plot of a miner.
     */
    class PlotCreationWorker(mvc: Context, workerParams: WorkerParameters): ControllerWorker(mvc, workerParams) {

        companion object {
            const val MINER_UUID = "miner_uuid"
        }

        override suspend fun doWork(mvc: MVC): Result {
            val controller = mvc.controller
            val uuid = UUID.fromString(inputData.getString(MINER_UUID))
            val miner = mvc.model.miners.get(uuid)
            if (miner == null) {
                Log.w(
                    TAG,
                    "The miner with uuid $uuid has been deleted before the creation of its plot!"
                )
                return Result.failure()
            }

            val notification = controller.createNotification(
                mvc.getString(R.string.notification_plot_creation_title),
                mvc.getString(
                    R.string.notification_plot_creation_description,
                    miner.miningSpecification.name
                )
            )

            publishForegroundNotification(notification)

            val filename = "$uuid.plot"
            val path = mvc.filesDir.toPath().resolve(filename)

            Log.i(TAG, "Started creation of $path for miner $miner")

            try {
                Plots.create(
                    path, miner.getProlog(), 0, miner.size,
                    miner.miningSpecification.hashingForDeadlines
                ) { percent ->
                    controller.mainScope.launch { mvc.view?.onPlotCreationTick(miner, percent) }
                    Log.i(TAG, "Created $percent% of plot $path")
                }
            } catch (_: FileNotFoundException) {
                // the miner has been deleted before the complete creation of its plot
                Log.w(
                    TAG,
                    "Miner $miner has been deleted before the full creation of its plot!"
                )
                return Result.failure()
            }

            Log.i(TAG, "Completed creation of $path for miner $miner")

            if (mvc.model.miners.markHasPlot(miner)) {
                controller.mainScope.launch {
                    controller.miningServices.ensureServiceFor(miner)
                    mvc.view?.onPlotCreationCompleted(miner)
                }
            } else controller.deletePlotOf(miner)

            return Result.success()
        }
    }

    private fun safeRunAsIO(showProgressBar: Boolean = true, task: () -> Unit) {
        if (showProgressBar) {
            working.incrementAndGet()
            mainScope.launch { mvc.view?.onBackgroundStart() }
        }

        ioScope.launch {
            try {
                task.invoke()
            } catch (_: TimeoutException) {
                Log.w(TAG, "The operation timed-out")
                if (showProgressBar)
                    mainScope.launch { mvc.view?.notifyUser(mvc.getString(R.string.operation_timeout)) }
            } catch (t: Throwable) {
                Log.w(TAG, "A background IO action failed", t)
                if (showProgressBar)
                    mainScope.launch { mvc.view?.notifyUser(t.toString()) }
            } finally {
                if (showProgressBar && working.decrementAndGet() == 0)
                    mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }
    }
}