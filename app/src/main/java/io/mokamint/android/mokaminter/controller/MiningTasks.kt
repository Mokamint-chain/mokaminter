package io.mokamint.android.mokaminter.controller

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.GuardedBy
import io.mokamint.android.mokaminter.model.Miner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

class MiningTasks: Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * The set of miners that are currently actively mining.
     */
    @GuardedBy("itself")
    private val activeMiners: MutableSet<Miner> = HashSet()

    companion object {
        private val TAG = MiningTasks::class.simpleName
        private const val START_ACTION = "start"
        private const val STOP_ACTION = "stop"
        private const val EXACTLY_ACTION = "exactly"
        private const val MINER_EXTRA_KEY = "miner"
        private const val MINERS_EXTRA_KEY = "miners"

        fun startMiningWith(miner: Miner, context: Context) {
            val intent = Intent(START_ACTION, null, context, MiningTasks::class.java)
            intent.putExtra(MINER_EXTRA_KEY, miner)
            context.startService(intent)
        }

        fun stopMiningWith(miner: Miner, context: Context) {
            val intent = Intent(STOP_ACTION, null, context, MiningTasks::class.java)
            intent.putExtra(MINER_EXTRA_KEY, miner)
            context.startService(intent)
        }

        fun mineWithExactly(miners: Stream<Miner>, context: Context) {
            val intent = Intent(EXACTLY_ACTION, null, context, MiningTasks::class.java)
            val arrayListOfMiners = arrayListOf<Miner>()
            miners.forEach { miner -> arrayListOfMiners.add(miner) }
            intent.putParcelableArrayListExtra(MINERS_EXTRA_KEY, arrayListOfMiners)
            context.startService(intent)
        }
    }

    private suspend fun startMiningWith(miner: Miner) {
        synchronized(activeMiners) {
            activeMiners.add(miner)
        }

        Log.i(TAG, "Start $miner")
    }

    private suspend fun stopMiningWith(miner: Miner) {
        synchronized(activeMiners) {
            activeMiners.remove(miner)
        }

        Log.i(TAG, "Stop $miner")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                START_ACTION -> {
                    coroutineScope.launch(Dispatchers.Default) {
                        intent.getParcelableExtra<Miner>(MINER_EXTRA_KEY)?.let { it -> startMiningWith(it) }
                    }
                }
                STOP_ACTION -> {
                    coroutineScope.launch(Dispatchers.Default) {
                        intent.getParcelableExtra<Miner>(MINER_EXTRA_KEY)?.let { it -> stopMiningWith(it) }
                    }
                }
                EXACTLY_ACTION -> {
                    intent.getParcelableArrayListExtra<Miner>(MINERS_EXTRA_KEY)?.let { it ->
                        {
                            synchronized(activeMiners) {
                                it.forEach {
                                    miner ->
                                        if (!activeMiners.contains(miner))
                                            coroutineScope.launch(Dispatchers.Default) { startMiningWith(miner) }
                                }

                                activeMiners.forEach {
                                    active -> {
                                        if (!it.contains(active))
                                            coroutineScope.launch(Dispatchers.Default) { stopMiningWith(active) }
                                    }
                                }
                            }
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