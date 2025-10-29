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
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Log
import androidx.annotation.UiThread
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
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
import io.mokamint.miner.service.MinerServices
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    /**
     * The object that keeps, starts and stops the mining services for the miners.
     */
    val miningServices = MiningServices(mvc)

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    /**
     * The number of ongoing backgrounds task for which the
     * progress bar activity of the view has been requested.
     */
    private val ongoingWorks = AtomicInteger(0)

    /**
     * A thread that redraws the miners periodically. This only exists if the view
     * requires such machinery.
     */
    private var minersRedrawer: MinersRedrawer? = null

    companion object {
        private val TAG = Controller::class.simpleName

        /**
         * The interval, in milliseconds, between successive redraws of the miners.
         */
        const val MINERS_REDRAW_INTERVAL = 10_000L // ten seconds
    }

    /**
     * Determines if this controller is holding a connected mining service for the given miner.
     *
     * @param miner the miner
     * @return true if and only if that condition holds
     */
    @UiThread
    fun hasConnectedServiceFor(miner: Miner): Boolean {
        return miningServices.hasConnectedServiceFor(miner)
    }

    @UiThread
    fun isWorking(): Boolean {
        return ongoingWorks.get() > 0
    }

    /**
     * Called when the current view lets the miners be visible, so that they
     * must be updated and their services must be opened, if not already open.
     */
    @UiThread
    fun onMinersVisible() {
        val minersRedrawer = MinersRedrawer()
        this.minersRedrawer = minersRedrawer
        minersRedrawer.start()

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

    /**
     * Called when the current view hides the miners, so that there is no need to keep
     * their information updated on the screen.
     */
    @UiThread
    fun onMinersInvisible() {
        minersRedrawer?.interrupt()
    }

    /**
     * Called when the update of the balances of all miners has been requested.
     * It will return immediately and successively fetch and update the balances.
     * It uses a progress bar during such side operation.
     */
    @UiThread
    fun onUpdateOfAllBalancesRequested() {
        safeRunAsIO {
            val snapshot = mvc.model.miners.reload()
            snapshot.forEach { miner, status ->
                if (status.isOn && status.hasPlotReady)
                    miningServices.fetchBalanceOf(miner)
            }
        }
    }

    /**
     * Called when the update of the balance of the given miner has been requested.
     *
     * @param miner the miner
     */
    @UiThread
    fun onUpdateOfBalanceRequested(miner: Miner) {
        safeRunAsIO(false) {
            miningServices.fetchBalanceOf(miner)
        }
    }

    /**
     * Called when the deletion of the given miner has been requested, including
     * that of its plot file.
     *
     * @param miner the miner
     */
    @UiThread
    fun onDeleteRequested(miner: Miner) {
        mainScope.launch {
            val removed = ioScope.async { mvc.model.miners.remove(miner) }.await()
            if (removed) {
                miningServices.stopServiceFor(miner)
                mvc.view?.onDeleted(miner)
                ioScope.launch { deletePlotFor(miner) }
            }
        }
    }

    /**
     * Called when the given miner has been requested to be turned on.
     *
     * @param miner the miner
     */
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

    /**
     * Called when the given miner has been requested to be turned off.
     *
     * @param miner the miner
     */
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

    /**
     * Called when the information of given miner has been requested to be shared.
     *
     * @param miner the miner
     */
    @UiThread
    fun onShareRequested(miner: Miner, context: Context) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, miner.toXML())
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    /**
     * Called when the creation of a new miner has been requested. It will return the
     * identifier of the miner immediately and start creating the miner on the background,
     * from its mining specification fetched from its remote endpoint.
     *
     * @param uri the remote endpoint of the remote miner for which the new miner will work
     * @param plotSize the plot size of the new miner
     * @param entropy the entropy of the key pair of the new miner; if this is present, also
     *                the password must be present; otherwise, the public key must be present
     * @param password the password of the entropy of the private key of the new miner
     * @param publicKeyBase58 the Base58-encoded public key of the new miner; this is only
     *                        specified if {@code entropy} and {@code password} are not specified
     * @return the identifier of the miner whose creation starts with this call
     */
    @UiThread
    fun onMinerCreationRequested(uri: URI, plotSize: Long, entropy: Entropy?, password: String?, publicKeyBase58: String?): UUID {
        val uuid = UUID.randomUUID()

        safeRunAsIO {
            try {
                val miningSpecification = MinerServices.of(uri, 20_000).use { it.miningSpecification }
                Log.i(TAG, "Fetched the mining specification of $uri:\n$miningSpecification")

                val miner: Miner
                if (publicKeyBase58 != null) {
                    try {
                        miner = Miner(uuid, miningSpecification, uri, plotSize, publicKeyBase58)
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
                        uuid, miningSpecification, uri, plotSize,
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

    /**
     * Called when the creation of the plot file for the given miner has been requested.
     * It will return immediately and the creation of the plot file will subsequently
     * be performed as a side work.
     *
     * @param miner the miner
     */
    @UiThread
    fun onPlotCreationRequested(miner: Miner) {
        mvc.view?.onPlotCreationConfirmed(miner)

        mainScope.launch {
            val status = MinerStatus(BigInteger.ZERO, hasPlotReady = false, isOn = true, -1L)
            ioScope.async { mvc.model.miners.add(miner, status) }.await()
            mvc.view?.onAdded(miner)
            PlotCreationWorker.spawn(miner, mvc)
        }
    }

    /**
     * A thread that redraws the miners, periodically, thus updating their last update information.
     */
    private inner class MinersRedrawer: Thread() {

        override fun run() {
            Log.d(TAG, "Starting redrawing the miners periodically")

            try {
                while (true) {
                    sleep(MINERS_REDRAW_INTERVAL)
                    mainScope.launch { mvc.view?.onRedrawMiners() }
                }
            }
            catch (_: InterruptedException) {
                Log.d(TAG, "Stopping redrawing the miners")
            }
        }
    }

    /**
     * A background work for the creation of the plot of a miner.
     */
    class PlotCreationWorker(mvc: Context, workerParams: WorkerParameters): CoroutineWorker(mvc, workerParams) {

        companion object {
            private const val MINER_UUID = "miner_uuid"

            fun spawn(miner: Miner, mvc: MVC) {
                val plotCreationRequest =
                    OneTimeWorkRequestBuilder<PlotCreationWorker>()
                        .setInputData(workDataOf(Pair(MINER_UUID, miner.uuid.toString())))
                        .build()

                WorkManager.getInstance(mvc).enqueue(plotCreationRequest)
            }
        }

        override suspend fun doWork(): Result {
            val mvc = applicationContext as MVC

            try {
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

                // we are creating a plot for a miner, therefore there is no notification with
                // the same id, since the miner cannot have a mining notification already
                val id = "plot creation".hashCode() xor uuid.hashCode()
                val notification = createNotification(miner, 0, mvc)
                publishForegroundNotification(notification, id)

                val filename = "$uuid.plot"
                val path = mvc.filesDir.toPath().resolve(filename)

                Log.i(TAG, "Started creating plot $path for miner $miner")

                try {
                    Plots.create(
                        path, miner.getProlog(), 0, miner.plotSize,
                        miner.miningSpecification.hashingForDeadlines
                    ) { percent ->
                        val notification = createNotification(miner, percent, mvc)
                        controller.mainScope.launch {
                            mvc.view?.onPlotCreationTick(miner, percent)
                            publishForegroundNotification(notification, id)
                        }
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

                Log.i(TAG, "Completed the creation of plot $path for miner $miner")

                if (mvc.model.miners.markHasPlot(miner)) {
                    controller.mainScope.launch {
                        controller.miningServices.ensureServiceFor(miner)
                        mvc.view?.onPlotCreationCompleted(miner)
                    }
                } else controller.deletePlotFor(miner)

                return Result.success()
            } catch (_: CancellationException) {
                Log.w(TAG, "Plot creation cancelled")
                return Result.success()
            } catch (t: Throwable) {
                Log.w(TAG, "Plot creation failed", t)
                return Result.failure()
            }
        }

        private fun createNotification(miner: Miner, percent: Int, mvc: MVC): Notification {
            val title = mvc.getString(
                R.string.notification_plot_creation_title,
                miner.miningSpecification.name,
            )

            val description = mvc.getString(R.string.notification_tap_to_show_with_progress, percent)

            val showActivityIntent = Intent(mvc, Mokaminter::class.java)
            val showActivityPendingIntent = TaskStackBuilder.create(mvc).run {
                addNextIntentWithParentStack(showActivityIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

            return NotificationCompat.Builder(mvc, Mokaminter.NOTIFICATION_CHANNEL)
                .setContentTitle(title)
                .setContentText(description)
                .setSmallIcon(R.drawable.ic_plot_file)
                .setOngoing(false)
                .setAutoCancel(false)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setContentIntent(showActivityPendingIntent)
                .build()
        }

        private suspend fun publishForegroundNotification(notification: Notification, id: Int) {
            val foregroundInfo = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                ForegroundInfo(id, notification)
            else
            // more recent Android versions require a finer-grained specification
            // of the foreground service
                ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)

            setForeground(foregroundInfo)
        }
    }

    private fun deletePlotFor(miner: Miner) {
        val filename = "${miner.uuid}.plot"
        if (mvc.deleteFile(filename))
            Log.i(TAG, "Deleted $filename")
        else
            Log.w(TAG, "Failed deleting $filename")
    }

    @UiThread
    private fun safeRunAsIO(showProgressBar: Boolean = true, task: () -> Unit) {
        if (showProgressBar) {
            ongoingWorks.incrementAndGet()
            mvc.view?.onBackgroundStart()
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
                if (showProgressBar && ongoingWorks.decrementAndGet() == 0)
                    mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }
    }
}