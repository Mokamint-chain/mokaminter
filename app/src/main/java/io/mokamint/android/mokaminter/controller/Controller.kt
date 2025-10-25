package io.mokamint.android.mokaminter.controller

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
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
import io.mokamint.android.mokaminter.model.MinersSnapshot
import io.mokamint.android.mokaminter.view.Mokaminter
import io.mokamint.miner.api.ClosedMinerException
import io.mokamint.miner.api.MiningSpecification
import io.mokamint.miner.local.LocalMiners
import io.mokamint.miner.service.AbstractReconnectingMinerService
import io.mokamint.miner.service.MinerServices
import io.mokamint.miner.service.api.ReconnectingMinerService
import io.mokamint.nonce.api.Deadline
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.math.BigInteger
import java.net.URI
import java.security.spec.InvalidKeySpecException
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    private val working = AtomicInteger(0)

    /**
     * A lock used to serialize modifications to the set of miners.
     */
    private val minersLock = Object()

    /**
     * A map from active miners to their servicing object.
     */
    private val services = ConcurrentHashMap<Miner, ReconnectingMinerService>()

    companion object {
        private val TAG = Controller::class.simpleName

        /**
         * The interval, in milliseconds, between successive
         * updates of the last updated information of the miners.
         */
        private const val MINERS_REDRAW_INTERVAL = 10_000L // ten seconds

        /**
         * The number of deadlines that must be computed before triggering a new
         * fetch of the balance of a miner.
         */
        private const val DEADLINES_FOR_BALANCE_FETCH = 100

        private val nextId = AtomicInteger(100)
    }

    fun onMinersUpdaterRequested() {
        val minersUpdateRequest: OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<MinersRedrawWorker>()
                .setInputData(workDataOf(Pair(ControllerWorker.REQUIRES_PROGRESS_BAR, false)))
                .build()

        WorkManager.getInstance(mvc)
            .enqueueUniqueWork("update", ExistingWorkPolicy.REPLACE, minersUpdateRequest)
    }

    @GuardedBy("this.minersLock")
    private fun startServiceFor(miner: Miner) {
        val old = services[miner]
        val new = services.computeIfAbsent(miner) { m -> createService(m) }
        if (old != new) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Log.i(TAG, "using WorkManager")
                val serviceWatcherRequest: OneTimeWorkRequest =
                    OneTimeWorkRequestBuilder<ServiceWatcherWorker>()
                        .setInputData(
                            workDataOf(
                                Pair(ControllerWorker.REQUIRES_PROGRESS_BAR, false),
                                Pair(ServiceWatcherWorker.MINER_UUID, miner.uuid.toString())
                            )
                        )
                        .build()

                WorkManager.getInstance(mvc)
                    .enqueueUniqueWork(
                        miner.uuid.toString(),
                        ExistingWorkPolicy.REPLACE,
                        serviceWatcherRequest
                    )
            }
            else {
                Log.i(TAG, "using JobScheduler")
                val networkRequest = NetworkRequest.Builder().build()

                val args = PersistableBundle()
                args.putString(ServiceWatcherJobService.MINER_UUID, miner.uuid.toString())

                val jobInfo = JobInfo.Builder(miner.uuid.hashCode(),
                    ComponentName(mvc, ServiceWatcherJobService::class.java))
                    .setUserInitiated(true)
                    .setRequiredNetwork(networkRequest)
                    .setEstimatedNetworkBytes(1024L * 1024 * 1024, 1024L * 1024 * 1024)
                    .setExtras(args)
                    .build()

                val jobScheduler: JobScheduler =
                    mvc.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

                jobScheduler.schedule(jobInfo)
            }
        }
    }

    @UiThread
    fun hasConnectedServiceFor(miner: Miner): Boolean {
        return services[miner]?.isConnected == true
    }

    @GuardedBy("this.minersLock")
    private fun createService(miner: Miner): ReconnectingMinerService {
        val filename = "${miner.uuid}.plot"
        val path = mvc.filesDir.toPath().resolve(filename)
        val plot = Plots.load(path)

        val localMiner = LocalMiners.of(
            "Local miner for ${miner.miningSpecification.name}",
            "A miner working for ${miner.uri}",
            { signature, publicKey -> Optional.empty() },
            plot
        )

        return object :
            AbstractReconnectingMinerService(Optional.of(localMiner), miner.uri, 30000, 30000) {

            /**
             * A counter of the deadlines computed with this service, used
             * to decide when to reload the balance of the corresponding miner.
             */
            private var deadlines: Int = 0

            override fun onConnected() {
                super.onConnected()
                mainScope.launch { mvc.view?.onConnected(miner) }
            }

            override fun onDisconnected() {
                super.onDisconnected()
                mainScope.launch { mvc.view?.onDisconnected(miner) }
            }

            override fun onDeadlineComputed(deadline: Deadline?) {
                super.onDeadlineComputed(deadline)
                mainScope.launch { mvc.view?.onDeadlineComputed(miner) }
                if (deadlines++ % DEADLINES_FOR_BALANCE_FETCH == 0) {
                    safeRunAsIO(false) {
                        fetchBalanceOf(miner)
                    }
                }
            }
        }
    }

    @UiThread
    fun isWorking(): Boolean {
        return working.get() > 0
    }

    @UiThread
    fun onMinersReloadRequested() {
        safeRunAsIO {
            var snapshot: MinersSnapshot

            synchronized (minersLock) {
                snapshot = mvc.model.miners.reload()
                snapshot.forEach { miner, status ->
                    if (status.isOn && status.hasPlotReady)
                        startServiceFor(miner)
                }
            }

            Log.i(TAG, "Reloaded the list of miners")
            mainScope.launch { mvc.view?.onMinersReloaded() }
        }
    }

    @UiThread
    fun onBalancesReloadRequested() {
        safeRunAsIO {
            mvc.model.miners.snapshot().forEach { miner, status ->
                if (status.isOn && status.hasPlotReady)
                    fetchBalanceOf(miner)
            }

            Log.i(TAG, "Fetched the balances of all miners")
        }
    }

    private fun fetchBalanceOf(miner: Miner) {
        if (hasConnectedServiceFor(miner)) {
            try {
                val balance = services[miner]?.getBalance(
                    miner.miningSpecification.signatureForDeadlines,
                    miner.publicKey
                )

                Log.d(TAG, "Fetched balance $balance of $miner")

                if (balance != null) {
                    var done = false

                    synchronized(minersLock) {
                        done = mvc.model.miners.setBalance(miner, balance.orElse(BigInteger.ZERO))
                    }

                    if (done)
                        mainScope.launch { mvc.view?.onBalanceChanged(miner) }
                }
            }
            catch (e: ClosedMinerException) {
                Log.w(TAG, "Failed while reading the balance of $miner: ${e.message}")
            }
            catch (e: TimeoutException) {
                Log.w(TAG, "Failed while reading the balance of $miner: ${e.message}")
            }
        }
    }

    @UiThread
    fun onDeleteRequested(miner: Miner) {
        safeRunAsIO(false) {
            var service: ReconnectingMinerService? = null

            synchronized (minersLock) {
                if (mvc.model.miners.remove(miner))
                    service = services.remove(miner)
            }

            if (service != null) {
                mainScope.launch { mvc.view?.onDeleted(miner) }
                service.close()
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
        safeRunAsIO {
            var done = false

            synchronized (minersLock) {
                done = mvc.model.miners.markAsOn(miner)
                if (done)
                    startServiceFor(miner)
            }

            if (done)
                mainScope.launch { mvc.view?.onTurnedOn(miner) }
        }
    }

    @UiThread
    fun onTurnOffRequested(miner: Miner) {
        safeRunAsIO(false) {
            var service: ReconnectingMinerService? = null

            synchronized (minersLock) {
                if (mvc.model.miners.markAsOff(miner))
                    service = services.remove(miner)
            }

            if (service != null) {
                mainScope.launch { mvc.view?.onTurnedOff(miner) }
                service.close()
            }
        }
    }

    private fun createNotification(title: String, description: String, stopIntent: PendingIntent?, isOngoing: Boolean): Notification {
        val builder = NotificationCompat.Builder(mvc, Mokaminter.NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(R.drawable.ic_active_miner)
            .setOngoing(isOngoing)
            .setAutoCancel(false)
            .setPriority(NotificationManager.IMPORTANCE_MIN)

        stopIntent?.let { it ->
            builder.addAction(R.drawable.ic_delete, mvc.getString(R.string.turnOff), it)
        }

        return builder.build()
    }

    private fun createActiveMiningNotification(miner: Miner): Notification {
        val stopIntent = Intent(mvc, StopMiningReceiver::class.java)
        stopIntent.putExtra(StopMiningReceiver.UUID, miner.uuid.toString())
        val stopMiningIntent = PendingIntent.getBroadcast(
            mvc, nextId.getAndIncrement(), stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return createNotification(
            mvc.getString(R.string.notification_miner_is_active),
            mvc.getString(
                R.string.notification_miner_is_active_description,
                miner.miningSpecification.name
            ),
            stopMiningIntent,
            true
        )
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

    abstract class ControllerWorker(mvc: Context, workerParams: WorkerParameters): CoroutineWorker(mvc, workerParams) {

        companion object {
            const val REQUIRES_PROGRESS_BAR = "requiresProgressbar"
        }

        override suspend fun doWork(): Result {
            val mvc = applicationContext as MVC
            val controller = mvc.controller
            val requiresProgressBar = inputData.getBoolean(REQUIRES_PROGRESS_BAR, true)
            if (requiresProgressBar) {
                controller.working.incrementAndGet()
                controller.mainScope.launch { mvc.view?.onBackgroundStart() }
            }

            try {
                return doWork(mvc)
            }
            catch (_: TimeoutException) {
                Log.w(TAG, "A background work timed-out")
                controller.mainScope.launch { mvc.view?.notifyUser(mvc.getString(R.string.operation_timeout)) }
                return Result.failure()
            } catch (_: CancellationException) {
                return Result.success()
            } catch (t: Throwable) {
                Log.w(TAG, "A background work failed", t)
                controller.mainScope.launch { mvc.view?.notifyUser(t.toString()) }
                return Result.failure()
            }
            finally {
                if (requiresProgressBar && controller.working.decrementAndGet() == 0)
                    controller.mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }

        protected suspend fun publishForegroundNotification(notification: Notification) {
            val foregroundInfo = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                ForegroundInfo(nextId.getAndIncrement(), notification)
            } else {
                ForegroundInfo(nextId.getAndIncrement(), notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }

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

    class ServiceWatcherJobService: JobService() {
        private val scope = CoroutineScope(Dispatchers.IO)

        companion object {
            const val MINER_UUID = "miner_uuid"
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onStartJob(params: JobParameters): Boolean {
            val mvc = applicationContext as MVC
            val controller = mvc.controller

            val uuid = UUID.fromString(params.extras.getString(MINER_UUID))
            val miner = mvc.model.miners.get(uuid)
            if (miner == null) {
                Log.w(TAG, "The miner with uuid $uuid cannot be found!")
                jobFinished(params, false)
                return false
            }

            val service = controller.services.get(miner)
            if (service == null) {
                Log.w(TAG, "The service of the miner with uuid $uuid cannot be found!")
                jobFinished(params, false)
                return false
            }

            val notification = controller.createActiveMiningNotification(miner)

            setNotification(
                params, nextId.getAndIncrement(), notification,
                JobService.JOB_END_NOTIFICATION_POLICY_REMOVE
            )

            scope.launch {
                Log.d(TAG, "Started waiting job for $uuid")
                service.waitUntilClosed()
                jobFinished(params, false)
            }

            return true
        }

        override fun onStopJob(params: JobParameters): Boolean {
            val uuid = UUID.fromString(params.extras.getString(MINER_UUID))
            Log.d(TAG, "System stopped job for miner $uuid")
            return false
        }
    }

    class ServiceWatcherWorker(mvc: Context, workerParams: WorkerParameters): ControllerWorker(mvc, workerParams) {
        companion object {
            const val MINER_UUID = "miner_uuid"
        }

        override suspend fun doWork(mvc: MVC): Result {
            val controller = mvc.controller
            val uuid = UUID.fromString(inputData.getString(MINER_UUID))
            val miner = mvc.model.miners.get(uuid)
            if (miner == null) {
                Log.w(TAG, "The miner with uuid $uuid cannot be found!")
                return Result.failure()
            }

            val service = controller.services.get(miner)
            if (service == null) {
                Log.w(TAG, "The service of the miner with uuid $uuid cannot be found!")
                return Result.failure()
            }

            val notification = controller.createActiveMiningNotification(miner)
            publishForegroundNotification(notification)
            Log.d(TAG, "Started waiting work for $uuid")
            service.waitUntilClosed()

            return Result.success()
        }
    }

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
                return Result.success()
            }

            val notification = controller.createNotification(
                mvc.getString(R.string.notification_plot_creation_title),
                mvc.getString(
                    R.string.notification_plot_creation_description,
                    miner.miningSpecification.name
                ),
                null,
                false
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
                return Result.success()
            }

            Log.i(TAG, "Completed creation of $path for miner $miner")

            synchronized(controller.minersLock) {
                if (mvc.model.miners.markHasPlot(miner)) {
                    controller.mainScope.launch { mvc.view?.onPlotCreationCompleted(miner) }
                    controller.startServiceFor(miner)
                } else controller.deletePlotOf(miner)
            }

            return Result.success()
        }
    }

    @UiThread
    fun onPlotCreationRequested(miner: Miner) {
        mvc.view?.onPlotCreationConfirmed(miner)

        var status = MinerStatus(BigInteger.ZERO,
            hasPlotReady = false,
            isOn = true,
            -1L
        )

        synchronized (minersLock) {
            mvc.model.miners.add(miner, status)
        }

        mvc.view?.onAdded(miner)

        val plotCreationRequest: WorkRequest =
            OneTimeWorkRequestBuilder<PlotCreationWorker>()
                .setInputData(workDataOf(Pair(PlotCreationWorker.MINER_UUID, miner.uuid.toString())))
                .build()

        WorkManager.getInstance(mvc)
            .enqueue(plotCreationRequest)
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
                mainScope.launch { mvc.view?.notifyUser(mvc.getString(R.string.operation_timeout)) }
            } catch (t: Throwable) {
                Log.w(TAG, "A background IO action failed", t)
                mainScope.launch { mvc.view?.notifyUser(t.toString()) }
            } finally {
                if (showProgressBar && working.decrementAndGet() == 0)
                    mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }
    }
}