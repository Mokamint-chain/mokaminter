/*
Copyright 2025 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.mokamint.android.mokaminter.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.UiThread
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.model.Miner

/**
 * A receiver of a request to stop a miner. This is used to stop a miner
 * when clicking on an action button in the foreground notification of the miner.
 */
class StopMiningReceiver: BroadcastReceiver() {

    companion object {
        private const val UUID = "uuid"
        private val TAG = StopMiningReceiver::class.simpleName

        /**
         * Creates an intent that can be used to trigger this receiver and stop a miner.
         *
         * @param miner the miner to stop
         * @param mvc te MVC triple of the application
         */
        fun createIntent(miner: Miner, mvc: MVC): Intent {
            val stopMiningIntent = Intent(mvc, StopMiningReceiver::class.java)
            stopMiningIntent.putExtra(UUID, miner.uuid.toString())
            return stopMiningIntent
        }
    }

    @UiThread
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