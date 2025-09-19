package io.mokamint.android.mokaminter.controller

import android.util.Log
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.model.Miners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class Controller(private val mvc: MVC) {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val working = AtomicInteger(0)

    companion object {
        private val TAG = Controller::class.simpleName
    }

    fun isWorking(): Boolean {
        return working.get() > 0
    }

    fun requestReloadOfMiners() {
        safeRunAsIO {
            mvc.model.set(Miners.load(mvc))
        }
    }

    fun requestDelete(miner: Miner) {
        safeRunAsIO {
            mvc.model.remove(miner)
        }
    }

    private fun safeRunAsIO(task: () -> Unit) {
        working.incrementAndGet()
        mainScope.launch { mvc.view?.onBackgroundStart() }

        ioScope.launch {
            try {
                task.invoke()
            }
            catch (_: TimeoutException) {
                Log.w(TAG, "The operation timed-out")
                mainScope.launch { mvc.view?.notifyUser(mvc.getString(R.string.operation_timeout)) }
            }
            catch (t: Throwable) {
                Log.w(TAG, "Background IO action failed", t)
                mainScope.launch { mvc.view?.notifyUser(t.toString()) }
            }
            finally {
                working.decrementAndGet()
                mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }
    }

}