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
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.view.Mokaminter.Companion.NOTIFICATION_CHANNEL
import io.mokamint.miner.api.ClosedMinerException
import io.mokamint.miner.service.api.MinerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.jvm.optionals.getOrDefault

class MiningServices: Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * A map from active miners to their servicing object.
     */
    private val activeServices = ConcurrentHashMap<Miner, MinerService?>()

    companion object {
        private val TAG = MiningServices::class.simpleName
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
            }
            catch (_: TimeoutException) {
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /*override fun onDestroy() {
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
    }*/

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}