package io.mokamint.android.mokaminter.controller

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.miner.local.LocalMiners
import io.mokamint.miner.service.MinerServices
import io.mokamint.miner.service.api.MinerService
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import kotlin.jvm.optionals.getOrDefault

class MiningService: Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val mainScope = CoroutineScope(Dispatchers.Main)

    /**
     * A map from active miners to their servicing object.
     */
    private val activeMiners = ConcurrentHashMap<Miner, MinerService>()

    companion object {
        private val TAG = MiningService::class.simpleName
        private const val UPDATE = "update"
        private const val FETCH_BALANCES = "fetch_balances"

        fun update(mvc: MVC) {
            val intent = Intent(UPDATE, null, mvc, MiningService::class.java)
            mvc.startService(intent)
        }

        fun fetchBalances(mvc: MVC) {
            val intent = Intent(FETCH_BALANCES, null, mvc, MiningService::class.java)
            mvc.startService(intent)
        }
    }

    override fun getApplicationContext(): MVC {
        return super.getApplicationContext() as MVC
    }

    private fun startMiningWith(miner: Miner) {
        activeMiners.computeIfAbsent(miner) { x ->
            val service: MinerService
            val filename = "${miner.uuid}.plot"
            val path = applicationContext.filesDir.toPath().resolve(filename)
            val plot = Plots.load(path)

            val localMiner = LocalMiners.of(
                "Local miner for ${miner.miningSpecification.name}",
                "A miner working for ${miner.uri}",
                { signature, publicKey -> Optional.empty() },
                plot
            )

            service = MinerServices.of(localMiner, miner.uri, 30_000)
            Log.i(TAG, "Started mining with $miner")
            service
        }
    }

    private fun stopMiningWith(miner: Miner) {
        activeMiners.remove(miner)?.let {
            it.close()
            Log.i(TAG, "Stopped mining with $miner")
        }
    }

    private fun fetchBalanceOf(miner: Miner) {
        val balance = activeMiners.get(miner)
            ?.getBalance(miner.miningSpecification.signatureForDeadlines, miner.publicKey)
            ?.getOrDefault(BigInteger.ZERO)

        if (balance != null) {
            Log.i(TAG, "Fetched balance of $miner: $balance")

            if (miner.balance != balance) {
                applicationContext.model.miners.remove(miner)
                applicationContext.model.miners.add(miner.withBalance(balance))
                applicationContext.model.miners.writeIntoInternalStorage()
                mainScope.launch { applicationContext.view?.onBalanceChanged(miner, balance) }
            }
        }
    }

    private fun safeRunInBackground(task: () -> Unit) {
        coroutineScope.launch(Dispatchers.Default) {
            try {
                task.invoke()
            }
            catch (_: TimeoutException) {
                Log.w(TAG, "A mining service operation timed-out")
                mainScope.launch { applicationContext.view?.notifyUser(applicationContext.getString(R.string.operation_timeout)) }
            }
            catch (t: Throwable) {
                Log.w(TAG, "A mining service operation failed", t)
                mainScope.launch { applicationContext.view?.notifyUser(t.toString()) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                UPDATE -> {
                    val minersToStart = arrayListOf<Miner>()
                    // we require to keep (or start) mining with exactly all reloaded miners
                    // that have their plot available
                    applicationContext.model.miners.elements()
                        .filter { miner -> miner.hasPlotReady }
                        .forEach { miner -> minersToStart.add(miner) }

                    minersToStart.forEach {
                        minerToStart -> safeRunInBackground { startMiningWith(minerToStart) }
                    }

                    activeMiners.keys.forEach {
                        activeMiner -> if (!minersToStart.contains(activeMiner))
                            safeRunInBackground { stopMiningWith(activeMiner) }
                    }
                }
                FETCH_BALANCES -> {
                    applicationContext.model.miners.elements()
                        .filter { miner -> miner.hasPlotReady }
                        .forEach { miner -> safeRunInBackground { fetchBalanceOf(miner) } }
                }
                else -> {
                    Log.w(TAG, "Unexpected intent action ${intent.action}")
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        val semaphore = Semaphore(0)

        safeRunInBackground {
            try {
                for (miner in activeMiners.keys)
                    stopMiningWith(miner)
            }
            finally {
                semaphore.release()
            }
        }

        semaphore.acquire()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}