package io.mokamint.android.mokaminter.controller

import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.UiThread
import io.hotmoka.crypto.Base58
import io.hotmoka.crypto.Base58ConversionException
import io.hotmoka.crypto.api.Entropy
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.model.MinerStatus
import io.mokamint.miner.api.MiningSpecification
import io.mokamint.miner.local.LocalMiners
import io.mokamint.miner.service.AbstractReconnectingMinerService
import io.mokamint.miner.service.MinerServices
import io.mokamint.miner.service.api.ReconnectingMinerService
import io.mokamint.plotter.Plots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.math.BigInteger
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
class Controller(val mvc: MVC) {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val working = AtomicInteger(0)

    /**
     * A lock used to serialize modifications to the set of miners.
     */
    private val minersLock = Object()

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

    @GuardedBy("this.minersLock")
    private fun startServiceFor(miner: Miner) {
        services.computeIfAbsent(miner) { m -> createService(m) };
    }

    @GuardedBy("this.minersLock")
    private fun stopServiceFor(miner: Miner) {
        services.remove(miner)?.close()
    }

    @UiThread
    fun hasConnectedServiceFor(miner: Miner): Boolean {
        return services.get(miner)?.isConnected == true
    }

    @GuardedBy("this.minersLock")
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

    @UiThread
    fun isWorking(): Boolean {
        return working.get() > 0
    }

    @UiThread
    fun onResumeRequested() {
        safeRunAsIO {
            synchronized (minersLock) {
                val snapshot = mvc.model.miners.reload()
                for (pos in 0..<snapshot.size()) {
                    val status = snapshot.getStatus(pos)
                    if (status.isOn && status.hasPlotReady)
                        startServiceFor(snapshot.getMiner(pos))
                }

                Log.i(TAG, "Reloaded the list of miners")
                mainScope.launch { mvc.view?.onMinersReloaded() }
            }
        }
    }

    @UiThread
    fun onDeleteRequested(miner: Miner) {
        safeRunAsIO {
            synchronized (minersLock) {
                if (mvc.model.miners.remove(miner)) {
                    stopServiceFor(miner)
                    deletePlotOf(miner)
                    mainScope.launch { mvc.view?.onDeleted(miner) }
                }
            }
        }
    }

    private fun deletePlotOf(miner: Miner) {
        val filename = "${miner.uuid}.plot"
        if (mvc.deleteFile(filename))
            Log.i(TAG, "Deleted $filename")
        else
            Log.w(TAG, "Failed deleting $filename")
    }

    @UiThread
    fun onTurnOnRequested(miner: Miner) {
        safeRunAsIO {
            synchronized (minersLock) {
                if (mvc.model.miners.markAsOn(miner)) {
                    startServiceFor(miner)
                    mainScope.launch { mvc.view?.onTurnedOn(miner) }
                }
            }
        }
    }

    @UiThread
    fun onTurnOffRequested(miner: Miner) {
        safeRunAsIO {
            synchronized (minersLock) {
                if (mvc.model.miners.markAsOff(miner)) {
                    stopServiceFor(miner)
                    mainScope.launch { mvc.view?.onTurnedOff(miner) }
                }
            }
        }
    }

    /**
     * Requests the creation of a miner.
     *
     * @return the identifier of the miner whose creation starts with this call
     */
    @UiThread
    fun onMinerCreationRequested(uri: URI, size: Long, entropy: Entropy?, password: String?, publicKeyBase58: String?): UUID {
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
                    Base58.toBase58String(signatureForDeadlines.encodingOf
                        (entropy.keys(password, signatureForDeadlines).public)))
                Log.i(TAG, "Ready to create plot file for miner ${miner.miningSpecification.name}")
                mainScope.launch { mvc.view?.onReadyToCreatePlotFor(miner) }
            }
            else {
                throw IllegalStateException("Nor a public key nor a mnemonic have been provided")
            }
        }

        return uuid
    }

    @UiThread
    fun onPlotCreationRequested(miner: Miner) {
        safeRunAsIO {
            val filename = "${miner.uuid}.plot"
            val path = mvc.filesDir.toPath().resolve(filename)
            mainScope.launch { mvc.view?.onPlotCreationStarted(miner) }
            var status = MinerStatus(BigInteger.ZERO,
                hasPlotReady = false,
                isOn = true
            )

            synchronized (minersLock) {
                mvc.model.miners.add(miner, status)
            }

            mainScope.launch { mvc.view?.onAdded(miner) }

            // we split the synchronization on minersLock in order to allow operating
            // on the miners during the (potentially slow) creation of the plot

            Log.i(TAG, "Started creation of $path for miner $miner")

            try {
                Plots.create(path, miner.getProlog(), 0, miner.size,
                    miner.miningSpecification.hashingForDeadlines
                ) { percent ->
                    mainScope.launch { mvc.view?.onPlotCreationTick(miner, percent) }
                    Log.i(TAG, "Created $percent% of plot $path")
                }
            }
            catch (_: FileNotFoundException) {
                // the miner has been deleted before the complete creation of its plot
                Log.w(TAG, "Miner $miner has been deleted before the full creation of its plot!")
            }

            Log.i(TAG, "Completed creation of $path for miner $miner")

            synchronized (minersLock) {
                if (mvc.model.miners.markHasPlot(miner)) {
                    mainScope.launch { mvc.view?.onPlotCreationCompleted(miner) }
                    startServiceFor(miner)
                }
                else deletePlotOf(miner)
            }
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