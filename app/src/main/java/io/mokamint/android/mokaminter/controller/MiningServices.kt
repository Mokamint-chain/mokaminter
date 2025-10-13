package io.mokamint.android.mokaminter.controller

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import io.hotmoka.websockets.api.FailedDeploymentException
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.view.Mokaminter.Companion.NOTIFICATION_CHANNEL
import io.mokamint.miner.api.ClosedMinerException
import io.mokamint.miner.local.AbstractLocalMiner;
import io.mokamint.miner.service.MinerServices
import io.mokamint.miner.service.api.MinerService
import io.mokamint.nonce.api.Challenge
import io.mokamint.nonce.api.Deadline
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrDefault

class MiningServices: Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val mainScope = CoroutineScope(Dispatchers.Main)

    /**
     * A map from active miners to their servicing object.
     */
    private val activeServices = ConcurrentHashMap<Miner, MinerService?>()

    companion object {
        private val TAG = MiningServices::class.simpleName
        private const val UPDATE = "update"
        private const val FETCH_BALANCES = "fetch_balances"

        fun update(mvc: MVC) {
            /*
            if (!mvc.controller.isMiningPaused()) {
                val intent = Intent(UPDATE, null, mvc, MiningServices::class.java)
                mvc.startForegroundService(intent)
            }
            */
        }

        fun fetchBalances(mvc: MVC) {
            /*
            val intent = Intent(FETCH_BALANCES, null, mvc, MiningServices::class.java)
            mvc.startService(intent)
            */
        }

        fun stop(mvc: MVC) {
            /*
            mvc.controller.pauseMining()
            val intent = Intent(mvc, MiningServices::class.java)
            mvc.stopService(intent)
            */
        }
    }

    private fun ensureForeground() {
        val stopIntent = Intent(applicationContext, StopMiningReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(applicationContext, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_pause_mining), stopPendingIntent)
            .build()

        val notification = Notification.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setContentTitle(getString(R.string.notification_mining_activity))
            .setContentText(getString(R.string.notification_miner_is_active))
            .setSmallIcon(R.drawable.ic_active_miner)
            .addAction(action)
            .build()

        ServiceCompat.startForeground(this, 101, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    override fun getApplicationContext(): MVC {
        return super.getApplicationContext() as MVC
    }

    private fun startMiningWith(miner: Miner) {
        if (applicationContext.controller.isMiningPaused())
            return

        activeServices.computeIfAbsent(miner) { x ->
            var service: MinerService?
            val filename = "${miner.uuid}.plot"
            val path = applicationContext.filesDir.toPath().resolve(filename)
            val plot = Plots.load(path)

            val localMiner = object: AbstractLocalMiner(
                "Local miner for ${miner.miningSpecification.name}",
                "A miner working for ${miner.uri}",
                { signature, publicKey -> Optional.empty() },
                plot
            ) {
                override fun requestDeadline(challenge: Challenge, onDeadlineComputed: Consumer<Deadline>) {
                    Log.d(TAG, "beat")
                    super.requestDeadline(challenge, onDeadlineComputed)
                }
            }

            try {
                service = MinerServices.of(localMiner, miner.uri, 30_000)
                // if the service gets closed (for instance, because of a network disconnection)
                // we unlink it from the map of active services
                service.addOnCloseHandler { stopMiningWith(miner) }
                applicationContext.model.miners.startedMiningWith(miner)
                Log.i(TAG, "Started mining with $miner")
            }
            catch (e: FailedDeploymentException) {
                Log.w(TAG, "Cannot connect to ${miner.uri}: ${e.message}")
                service = null
            }

            service
        }
    }

    private fun stopMiningWith(miner: Miner) {
        activeServices.remove(miner)?.let {
            it.close()
            applicationContext.model.miners.stoppedMiningWith(miner)
            Log.i(TAG, "Stopped mining with $miner")
        }
    }

    private fun fetchBalanceOf(miner: Miner) {
        activeServices[miner]?.let { service ->
            try {
                val balance = service.getBalance(
                    miner.miningSpecification.signatureForDeadlines,
                    miner.publicKey
                )
                    ?.getOrDefault(BigInteger.ZERO)

                if (balance != null) {
                    Log.i(TAG, "Fetched balance of $miner: $balance")
                    applicationContext.model.miners.markHasBalance(miner, balance)
                }
            }
            catch (_: ClosedMinerException) {
                // if the service is closed, we unlink it
                stopMiningWith(miner)
            }
            catch (_: TimeoutException) {
                // if the connection is unresponsive, we closed and unlink the service
                stopMiningWith(miner)
            }
        }
    }

    private fun update() {
        val minersToStart = arrayListOf<Miner>()
        // we require to keep (or start) mining with exactly all reloaded miners
        // that have their plot available
        applicationContext.model.miners.snapshot()
            .filter { miner -> miner.hasPlotReady }
            .forEach { miner -> minersToStart.add(miner) }

        minersToStart.forEach { minerToStart ->
            safeRunInBackground { startMiningWith(minerToStart) }
        }

        activeServices.keys.forEach { activeMiner ->
            if (!minersToStart.contains(activeMiner))
                safeRunInBackground { stopMiningWith(activeMiner) }
        }
    }

    private fun fetchBalances() {
        applicationContext.model.miners.snapshot()
            .forEach { miner -> safeRunInBackground { fetchBalanceOf(miner) } }
    }

    private fun safeRunInBackground(task: () -> Unit) {
        coroutineScope.launch(Dispatchers.Default) {
            try {
                task.invoke()
            }
            catch (_: TimeoutException) {
                Log.w(TAG, "A mining service operation timed-out")
                // mainScope.launch { applicationContext.view?.notifyUser(applicationContext.getString(R.string.operation_timeout)) }
            }
            catch (t: Throwable) {
                Log.w(TAG, "A mining service operation failed", t)
                // mainScope.launch { applicationContext.view?.notifyUser(t.toString()) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            Log.d(TAG, "onStartCommand ${intent.action}")

            when (intent.action) {
                UPDATE -> {
                    ensureForeground()
                    update()
                }
                FETCH_BALANCES -> {
                    fetchBalances()
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        val semaphore = Semaphore(0)

        safeRunInBackground {
            try {
                for (miner in activeServices.keys)
                    stopMiningWith(miner)
            }
            finally {
                semaphore.release()
            }
        }

        semaphore.acquire()

        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}