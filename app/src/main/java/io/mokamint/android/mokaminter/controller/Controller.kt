package io.mokamint.android.mokaminter.controller

import android.util.Log
import io.hotmoka.crypto.Base58
import io.hotmoka.crypto.Base58ConversionException
import io.hotmoka.crypto.api.Entropy
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.miner.api.MiningSpecification
import io.mokamint.miner.service.MinerServices
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.URI
import java.security.spec.InvalidKeySpecException
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile

/**
 * The controller of the MVC triple.
 *
 * @param mvc the MVC triple
 */
class Controller {
    private val mvc: MVC
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val defaultScope = CoroutineScope(Dispatchers.Default)
    private val working = AtomicInteger(0)

    @Volatile
    private var isRequestingBalances = false

    @Volatile
    private var isMiningPaused = false

    companion object {
        private val TAG = Controller::class.simpleName

        /**
         * The interval, in milliseconds, between successive sanity checks.
         */
        private const val SANITY_CHECK_INTERVAL = 600_000L // every 10 minutes
    }

    constructor(mvc: MVC) {
        this.mvc = mvc
        defaultScope.launch { sanityCheck() }
    }

    /**
     * Recreates services that have been closed and fetches the balances
     * of the miners, if currently needed by the controller.
     */
    private suspend fun sanityCheck() {
        while (true) {
            Log.d(TAG, "sanityCheck")
            delay(SANITY_CHECK_INTERVAL)

            if (!isMiningPaused)
                MiningServices.update(mvc)

            // we only request the balances if the controller asks so,
            // in order to reduce network congestion
            if (isRequestingBalances())
                MiningServices.fetchBalances(mvc)
        }
    }

    fun isWorking(): Boolean {
        return working.get() > 0
    }

    fun isMiningPaused(): Boolean {
        return isMiningPaused
    }

    fun pauseMining() {
        isMiningPaused = true
        mvc.view?.onMiningPaused()
    }

    fun requestPauseMining() {
        MiningServices.stop(mvc)
    }

    fun requestUnpauseMining() {
        isMiningPaused = false
        MiningServices.update(mvc)
        mvc.view?.onMiningUnpaused()
    }

    fun startRequestingBalances() {
        isRequestingBalances = true
    }

    fun stopRequestingBalances() {
        isRequestingBalances = false
    }

    fun isRequestingBalances(): Boolean {
        return isRequestingBalances
    }

    fun requestReloadOfMiners() {
        safeRunAsIO {
            mvc.model.miners.reload()
            mainScope.launch { mvc.view?.onMinersReloaded() }
            Log.i(TAG, "Reloaded the list of miners")
            MiningServices.update(mvc)
            MiningServices.fetchBalances(mvc)
        }
    }

    fun requestDelete(miner: Miner) {
        safeRunAsIO {
            val filename = "${miner.uuid}.plot"

            if (mvc.deleteFile(filename))
                Log.i(TAG, "Deleted $filename")
            else
                Log.w(TAG, "Failed deleting $filename")

            mvc.model.miners.remove(miner)
            mvc.model.miners.writeIntoInternalStorage()
            mainScope.launch { mvc.view?.onMinerDeleted(miner) }
            Log.i(TAG, "Removed miner ${miner.miningSpecification.name}")

            MiningServices.update(mvc)
        }
    }

    /**
     * Requests the creation of a miner.
     *
     * @return the identifier of the miner whose creation starts with this call
     */
    fun requestCreationOfMiner(uri: URI, size: Long, entropy: Entropy?, password: String?, publicKeyBase58: String?): UUID {
        val uuid = UUID.randomUUID()

        safeRunAsIO {
            val miningSpecification: MiningSpecification

            MinerServices.of(uri, 20_000).use {
                miningSpecification = it.miningSpecification
            }

            Log.i(TAG, "Fetched the mining specification of $uri:\n$miningSpecification")

            val miner: Miner
            if (publicKeyBase58 != null) {
                try {
                    miner = Miner(uuid, miningSpecification, uri, size, publicKeyBase58)
                    Log.i(TAG, "Ready to create plot file for miner ${miner.miningSpecification.name}")
                    mainScope.launch { mvc.view?.onReadyToCreatePlotFor(miner) }
                }
                catch (_: InvalidKeySpecException) {
                    mainScope.launch { mvc.view?.notifyUser(mvc.applicationContext.getString(R.string.add_miner_message_invalid_public_key)) }
                }
                catch (_: Base58ConversionException) {
                    mainScope.launch { mvc.view?.notifyUser(mvc.applicationContext.getString(R.string.add_miner_message_public_key_not_base58)) }
                }
            }
            else if (entropy != null && password != null) {
                val signatureForDeadlines = miningSpecification.signatureForDeadlines
                miner = Miner(uuid, miningSpecification, uri, size,
                    Base58.toBase58String(signatureForDeadlines.encodingOf(entropy.keys(password, signatureForDeadlines).public)))
                Log.i(TAG, "Ready to create plot file for miner ${miner.miningSpecification.name}")
                mainScope.launch { mvc.view?.onReadyToCreatePlotFor(miner) }
            }
            else {
                throw IllegalStateException("Nor a public key nor a mnemonic have been provided")
            }
        }

        return uuid
    }

    fun requestCreationOfPlotFor(miner: Miner) {
        safeRunAsIO {
            val filename = "${miner.uuid}.plot"
            val path = mvc.filesDir.toPath().resolve(filename)
            Log.i(TAG, "Started creation of $path for miner ${miner.miningSpecification.name}")
            mainScope.launch { mvc.view?.onPlotCreationStarted(miner) }

            mvc.model.miners.add(miner)
            mvc.model.miners.writeIntoInternalStorage()
            mainScope.launch { mvc.view?.onMinerAdded(miner) }
            Log.i(TAG, "Added miner ${miner.miningSpecification.name}")

            Plots.create(path, miner.getProlog(), 0, miner.size, miner.miningSpecification.hashingForDeadlines) { percent ->
                mainScope.launch { mvc.view?.onPlotCreationTick(miner, percent) }
                Log.i(TAG, "Created $percent% of plot $path")
            }

            // we replace the miner with an identical card, but whose "has plot" flag is true
            mvc.model.miners.remove(miner)
            mvc.model.miners.add(miner.withPlotReady())
            mvc.model.miners.writeIntoInternalStorage()

            Log.i(TAG, "Completed creation of $path for miner ${miner.miningSpecification.name}")
            mainScope.launch { mvc.view?.onPlotCreationCompleted(miner) }

            MiningServices.update(mvc)
        }
    }

    private fun safeRunAsIO(showProgressBar: Boolean = true, task: () -> Unit) {
        if (showProgressBar)
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
                if (showProgressBar && working.decrementAndGet() == 0)
                    mainScope.launch { mvc.view?.onBackgroundEnd() }
            }
        }
    }
}