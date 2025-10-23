package io.mokamint.android.mokaminter.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mokamint.android.mokaminter.MVC

class StopMiningReceiver: BroadcastReceiver() {

    companion object {
        const val UUID = "uuid"
        private val TAG = StopMiningReceiver::class.simpleName
    }

    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(UUID)?.let { it ->
            val uuid = java.util.UUID.fromString(it)
            val mvc = context.applicationContext as MVC
            val controller = mvc.controller
            mvc.model.miners.get(uuid)?.let { miner ->
                Log.i(TAG, "Received user request to stop miner $miner")
                controller.onTurnOffRequested(miner)
            }
        }
    }
}