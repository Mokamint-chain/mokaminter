package io.mokamint.android.mokaminter.controller

import android.util.Log
import io.hotmoka.crypto.Entropies
import io.hotmoka.crypto.api.BIP39Mnemonic
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.miner.api.MiningSpecification
import io.mokamint.miner.service.MinerServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The controller of the MVC triple.
 *
 * @param mvc the MVC triple
 */
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
            mvc.model.miners.reload()
            mainScope.launch { mvc.view?.onMinersReloaded() }
            Log.i(TAG, "Reloaded the list of miners")
        }
    }

    fun requestDelete(miner: Miner) {
        safeRunAsIO {
            mvc.model.miners.remove(miner)
            mvc.model.miners.writeIntoInternalStorage()
            mainScope.launch { mvc.view?.onMinerDeleted(miner) }
            Log.i(TAG, "Removed miner ${miner.miningSpecification.name}")
        }
    }

    fun requestCreationOfMiner(uri: URI, size: Int, bip39: BIP39Mnemonic, password: String) {
        safeRunAsIO {
            val miningSpecification: MiningSpecification

            MinerServices.of(uri, 20_000).use {
                miningSpecification = it.miningSpecification
            }

            Log.i(TAG, "Fetched the mining specification of $uri:\n$miningSpecification")

            val miner = Miner(miningSpecification, uri, size, Entropies.of(bip39.bytes), password)
            mvc.model.miners.add(miner)
            mvc.model.miners.writeIntoInternalStorage()
            mainScope.launch { mvc.view?.onMinerAdded(miner) }
            Log.i(TAG, "Added miner ${miner.miningSpecification.name}")
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
                Log.w(TAG, "A background IO action failed", t)
                mainScope.launch { mvc.view?.notifyUser(t.toString()) }
            }
            finally {
                working.decrementAndGet()
                mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }
    }

}