package io.mokamint.android.mokaminter.controller

import android.util.Log
import androidx.annotation.UiThread
import io.hotmoka.crypto.Base58
import io.hotmoka.crypto.Base58ConversionException
import io.hotmoka.crypto.api.Entropy
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.miner.api.MiningSpecification
import io.mokamint.miner.local.LocalMiners
import io.mokamint.miner.service.AbstractReconnectingMinerService
import io.mokamint.miner.service.MinerServices
import io.mokamint.miner.service.api.ReconnectingMinerService
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.security.spec.InvalidKeySpecException
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The controller of the MVC triple.
 *
 * @param mvc the MVC triple
 */
class Controller {
    private val mvc: MVC
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val working = AtomicInteger(0)

    /**
     * A map from active miners to their servicing object.
     */
    private val services = ConcurrentHashMap<Miner, ReconnectingMinerService>()

    companion object {
        private val TAG = Controller::class.simpleName

        /**
         * The interval, in milliseconds, between successive
         * background fetches of the balances of the miners.
         */
        private const val BALANCE_FETCH_INTERVAL = 60 * 60 * 1000L // every hour
    }

    constructor(mvc: MVC) {
        this.mvc = mvc
    }

    fun startServiceFor(miner: Miner) {
        services.computeIfAbsent(miner) { m -> createService(m) };
    }

    fun stopServiceFor(miner: Miner) {
        // TODO: close it in a work manager because it might be slow
        services.remove(miner)?.close()
    }

    fun hasConnectedServiceFor(miner: Miner): Boolean {
        return services.get(miner)?.isConnected == true
    }

    private fun createService(miner: Miner): ReconnectingMinerService {
        val filename = "${miner.uuid}.plot"
        val path = mvc.filesDir.toPath().resolve(filename)
        val plot = Plots.load(path)

        val localMiner = LocalMiners.of(
            "Local miner for ${miner.miningSpecification.name}",
            "A miner working for ${miner.uri}",
            { signature, publicKey -> Optional.empty() },
            plot
        )

        return object :
            AbstractReconnectingMinerService(Optional.of(localMiner), miner.uri, 30000, 30000) {

            override fun onConnected() {
                super.onConnected()
                mainScope.launch { mvc.view?.onConnected(miner) }
            }

            override fun onDisconnected() {
                super.onDisconnected()
                mainScope.launch { mvc.view?.onDisconnected(miner) }
            }
        }
    }

    fun isWorking(): Boolean {
        return working.get() > 0
    }

    @UiThread
    fun startServiceForAllMiners() {
        mvc.model.miners.reload()
            .filter { miner ->  miner.isOn && miner.hasPlotReady }
            .forEach { miner ->  startServiceFor(miner) }
        mainScope.launch { mvc.view?.onMinersReloaded() }
        Log.i(TAG, "Reloaded the list of miners")
    }

    fun requestDelete(miner: Miner) {
        safeRunAsIO {
            stopServiceFor(miner)

            val filename = "${miner.uuid}.plot"

            if (mvc.deleteFile(filename))
                Log.i(TAG, "Deleted $filename")
            else
                Log.w(TAG, "Failed deleting $filename")

            mvc.model.miners.remove(miner)
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

            Plots.create(path, miner.getProlog(), 0, miner.size, miner.miningSpecification.hashingForDeadlines) { percent ->
                mainScope.launch { mvc.view?.onPlotCreationTick(miner, percent) }
                Log.i(TAG, "Created $percent% of plot $path")
            }

            // we replace the miner with an identical card, but whose "has plot" flag is true
            val miner = mvc.model.miners.markHasPlot(miner)
            Log.i(TAG, "Completed creation of $path for miner ${miner.miningSpecification.name}")
            mainScope.launch { mvc.view?.onPlotCreationCompleted(miner) }
            startServiceFor(miner)
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