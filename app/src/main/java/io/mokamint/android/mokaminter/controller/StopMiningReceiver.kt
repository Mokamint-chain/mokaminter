package io.mokamint.android.mokaminter.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mokamint.android.mokaminter.MVC

class StopMiningReceiver: BroadcastReceiver() {

    companion object {
        private val TAG = StopMiningReceiver::class.simpleName
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "User stop request received")
    }
}