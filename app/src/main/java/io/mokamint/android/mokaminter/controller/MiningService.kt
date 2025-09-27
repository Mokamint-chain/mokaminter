package io.mokamint.android.mokaminter.controller

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.miner.local.LocalMiners
import io.mokamint.miner.service.MinerServices
import io.mokamint.miner.service.api.MinerService
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class MiningService: Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * A map from active miners to their servicing object.
     */
    private val activeMiners = ConcurrentHashMap<Miner, MinerService>()

    companion object {
        private val TAG = MiningService::class.simpleName
        private const val UPDATE = "update"
        private const val MINERS_EXTRA_KEY = "miners"

        fun update(mvc: MVC) {
            val intent = Intent(UPDATE, null, mvc, MiningService::class.java)

            val minersToStart = arrayListOf<Miner>()
            // we require to keep (or start) mining with exactly all reloaded miners
            // that have their plot available
            mvc.model.miners.stream()
                .filter({ miner -> miner.hasPlotReady })
                .forEach { miner -> minersToStart.add(miner) }

            intent.putParcelableArrayListExtra(MINERS_EXTRA_KEY, minersToStart)

            mvc.startService(intent)
        }
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                UPDATE -> {
                    intent.getParcelableArrayListExtra<Miner>(MINERS_EXTRA_KEY)?.let { minersToStart ->
                        minersToStart.forEach {
                            minerToStart -> coroutineScope.launch(Dispatchers.Default) { startMiningWith(minerToStart) }
                        }

                        activeMiners.keys.forEach {
                            activeMiner -> if (!minersToStart.contains(activeMiner))
                                coroutineScope.launch(Dispatchers.Default) { stopMiningWith(activeMiner) }
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "Unexpected intent action ${intent.action}")
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}