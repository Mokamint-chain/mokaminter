package io.mokamint.android.mokaminter.controller

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
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
import android.os.PersistableBundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.view.Mokaminter
import io.mokamint.miner.api.ClosedMinerException
import io.mokamint.miner.local.LocalMiners
import io.mokamint.miner.service.AbstractReconnectingMinerService
import io.mokamint.miner.service.api.ReconnectingMinerService
import io.mokamint.nonce.api.Deadline
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

class MiningServices(private val mvc: MVC) {

    /**
     * A map from active miners to their servicing object.
     */
    private val services = ConcurrentHashMap<UUID, ReconnectingMinerService>()

    companion object {
        private val TAG = MiningServices::class.simpleName

        private const val MINER_UUID = "miner_uuid"

        /**
         * The number of deadlines that must be computed before triggering a new
         * fetch of the balance of a miner.
         */
        private const val DEADLINES_FOR_BALANCE_FETCH = 200

        private val ioScope = CoroutineScope(Dispatchers.IO)
        private val mainScope = CoroutineScope(Dispatchers.Main)

        private fun createActiveMiningNotification(miner: Miner, mvc: MVC): Notification {
            val stopMiningIntent = Intent(mvc, StopMiningReceiver::class.java)
            stopMiningIntent.putExtra(StopMiningReceiver.UUID, miner.uuid.toString())
            val stopMiningPendingIntent = PendingIntent.getBroadcast(
                mvc, miner.uuid.hashCode(), stopMiningIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val showActivityIntent = Intent(mvc, Mokaminter::class.java)
            val showActivityPendingIntent = TaskStackBuilder.create(mvc).run {
                addNextIntentWithParentStack(showActivityIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

            val title = mvc.getString(
                R.string.notification_mining_active,
                miner.miningSpecification.name
            )

            val description = mvc.getString(R.string.notification_tap_to_show_miners)

            return NotificationCompat.Builder(mvc, Mokaminter.NOTIFICATION_CHANNEL)
                .setContentTitle(title)
                .setContentText(description)
                .setSmallIcon(R.drawable.ic_active_miner)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .addAction(R.drawable.ic_delete, mvc.getString(R.string.turnOff), stopMiningPendingIntent)
                .setContentIntent(showActivityPendingIntent)
                .build()
        }

        private fun createService(miner: Miner, mvc: MVC): ReconnectingMinerService {
            val filename = "${miner.uuid}.plot"
            val path = mvc.filesDir.toPath().resolve(filename)
            val plot = Plots.load(path)

            val localMiner = LocalMiners.of(
                "Local miner for ${miner.miningSpecification.name}",
                "A miner working for ${miner.uri}",
                { signature, publicKey -> Optional.empty() },
                plot
            )

            val service = object :
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

                    mainScope.launch {
                        mvc.view?.onDeadlineComputed(miner)
                        if (deadlines++ % DEADLINES_FOR_BALANCE_FETCH == 0)
                            ioScope.launch { mvc.controller.fetchBalanceOf(miner) }
                    }
                }
            }

            mvc.controller.miningServices.services[miner.uuid] = service
            Log.i(TAG, "Started mining service for miner $miner.uuid")

            return service
        }
    }

    fun ensureServiceFor(miner: Miner) {
        // older Android versions will use the WorkManager, while newer
        // versions have a quota limit on the WorkManager and therefore will rather
        // use the JobScheduler; which, however, is not available in previous versions,
        // so that both possibilities must be taken into account...
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            MiningWatcherWorker.ensureServiceFor(miner, mvc)
        else
            MiningWatcherJobService.ensureServiceFor(miner, mvc)
    }

    @UiThread
    fun stopServiceFor(miner: Miner) {
        stopServiceFor(miner.uuid)
    }

    @UiThread
    fun hasConnectedServiceFor(miner: Miner): Boolean {
        return services[miner.uuid]?.isConnected == true
    }

    fun fetchBalanceOf(miner: Miner) {
        services[miner.uuid]?.let { service ->
            if (service.isConnected) {
                try {
                    Log.d(TAG, "Asking balance of $miner")
                    val balance = service.getBalance(
                        miner.miningSpecification.signatureForDeadlines,
                        miner.publicKey
                    )

                    balance?.let { it ->
                        Log.d(TAG, "Fetched balance $it of $miner")
                        val done = mvc.model.miners.setBalance(miner, it.orElse(BigInteger.ZERO))
                        if (done)
                            mainScope.launch { mvc.view?.onBalanceChanged(miner) }
                    }
                } catch (e: ClosedMinerException) {
                    Log.w(TAG, "Failed to fetch the balance of $miner: ${e.message}")
                } catch (e: TimeoutException) {
                    Log.w(TAG, "Failed to fetch the balance of $miner: ${e.message}")
                }
            }
        }
    }

    @UiThread
    private fun stopServiceFor(uuid: UUID) {
        services.remove(uuid)?.let { service -> ioScope.launch {
            service.close()
            Log.i(TAG, "Stopped mining service for miner $uuid")
        }}
    }

    /**
     * A background work that waits for the closure of a mining service, so that the
     * application remains in foreground until the last miner is turned off.
     */
    class MiningWatcherWorker(mvc: Context, workerParams: WorkerParameters):
        CoroutineWorker(mvc, workerParams) {

            companion object {
                internal fun ensureServiceFor(miner: Miner, mvc: MVC) {
                    val workManager = WorkManager.getInstance(mvc)
                    val id = miner.uuid.toString()
                    val serviceWatcherRequest = OneTimeWorkRequestBuilder<MiningWatcherWorker>()
                        .setInputData(workDataOf(Pair(MINER_UUID, id)))
                        .build()

                    workManager.enqueueUniqueWork(
                        id,
                        ExistingWorkPolicy.KEEP,
                        serviceWatcherRequest
                    )
                }
            }

        override suspend fun doWork(): Result {
            val mvc = applicationContext as MVC
            val uuid = UUID.fromString(inputData.getString(MINER_UUID))
            val miner = mvc.model.miners.get(uuid)
            if (miner == null) {
                Log.w(TAG, "The miner with uuid $uuid cannot be found!")
                return Result.failure()
            }

            val service = createService(miner, mvc)
            val notification = createActiveMiningNotification(miner, mvc)
            publishForegroundNotification(notification, uuid.hashCode())
            service.waitUntilClosed()

            return Result.success()
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

    /**
     * A background job that waits for the closure of a mining service, so that the
     * application remains in foreground until the last miner is turned off.
     */
    class MiningWatcherJobService: JobService() {

        companion object {

            @UiThread
            @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            internal fun ensureServiceFor(miner: Miner, mvc: MVC) {
                val uuid = miner.uuid
                val id = uuid.hashCode()
                val jobScheduler = mvc.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

                if (jobScheduler.getPendingJob(id) == null) {
                    val networkRequest = NetworkRequest.Builder().build()
                    val args = PersistableBundle()
                    args.putString(MINER_UUID, uuid.toString())

                    val jobInfo =
                        JobInfo.Builder(id, ComponentName(mvc, MiningWatcherJobService::class.java))
                            .setUserInitiated(true)
                            .setPriority(JobInfo.PRIORITY_MAX)
                            .setRequiredNetwork(networkRequest)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setEstimatedNetworkBytes(1024L * 1024 * 1024, 1024L * 1024 * 1024)
                            .setExtras(args)
                            .build()

                    val result = jobScheduler.schedule(jobInfo)
                    if (result != JobScheduler.RESULT_SUCCESS)
                        Log.w(TAG, "Could not schedule job: $result")
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @UiThread
        override fun onStartJob(params: JobParameters): Boolean {
            val mvc = applicationContext as MVC
            val uuid = UUID.fromString(params.extras.getString(MINER_UUID))
            val miner = mvc.model.miners.get(uuid)
            if (miner == null) {
                Log.w(TAG, "The miner with uuid $uuid cannot be found!")
                jobFinished(params, false)
                return false
            }

            val service = createService(miner, mvc)
            val notification = createActiveMiningNotification(miner, mvc)

            setNotification(
                params, uuid.hashCode(), notification, JOB_END_NOTIFICATION_POLICY_REMOVE
            )

            ioScope.launch {
                service.waitUntilClosed()
                jobFinished(params, false)
            }

            return true // keep the thread running, the job has not finished yet
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onStopJob(params: JobParameters): Boolean {
            val mvc = applicationContext as MVC
            val uuid = UUID.fromString(params.extras.getString(MINER_UUID))
            mvc.controller.miningServices.stopServiceFor(uuid)
            Log.d(TAG, "The system stopped the mining watcher job for miner $uuid with reason: ${params.stopReason}")
            return true // reschedule the job as soon as possible
        }

        override fun onNetworkChanged(params: JobParameters) {
            // nothing, just avoid the log line in the super implementation
        }
    }
}