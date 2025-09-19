package io.mokamint.android.mokaminter.model

import android.util.Log
import io.mokamint.android.mokaminter.MVC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Model(private val mvc: MVC) {
    private val mainScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private val TAG = Model::class.simpleName
    }

    /**
     * The miners of the user.
     */
    private var miners: Miners = Miners.empty(mvc)

    fun getMiners(): Miners = miners

    fun set(miners: Miners) {
        this.miners = miners;
        mainScope.launch { mvc.view?.onMinersChanged(miners) }
        Log.i(TAG, "miners have been reset")
    }

    fun remove(miner: Miner) {
        miners.remove(miner)
        mainScope.launch { mvc.view?.onMinerDeleted(miner) }
        Log.i(TAG, "removed miner ${miner.miningSpecification.name}")
    }
}