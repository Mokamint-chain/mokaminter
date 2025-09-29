package io.mokamint.android.mokaminter.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mokamint.android.mokaminter.MVC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StopMiningReceiver: BroadcastReceiver() {

    companion object {
        private val TAG = StopMiningReceiver::class.simpleName
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "User stop request received")

        val mvc = context.applicationContext as MVC
        // we stop the service
        MiningServices.stop(mvc)
        // we also stop the view, if any
        //mvc.view?.let {
          //  CoroutineScope(Dispatchers.Main).launch { it.stop() }
        //}

        //This is used to close the notification tray
        //context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
}