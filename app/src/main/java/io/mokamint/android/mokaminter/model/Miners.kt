package io.mokamint.android.mokaminter.model

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.annotation.GuardedBy
import io.mokamint.android.mokaminter.MVC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import java.io.FileNotFoundException
import java.math.BigInteger
import java.util.TreeSet

/**
 * The set of miners. They are constrained to have distinct name.
 *
 * @param mvc the MVC triple
 */
class Miners(private val mvc: MVC) {

    /**
     * The ordered set of miners contained in this container.
     */
    @GuardedBy("itself")
    private val miners = TreeSet<Miner>()

    private val mainScope = CoroutineScope(Dispatchers.Main)

    companion object {

        /**
         * The name of the file where the miners are stored, in the internal storage of the app.
         */
        private const val FILENAME = "miners.txt"
        private const val MINERS_TAG = "miners"
        private const val MINER_TAG = "miner"
        private val TAG = Miners::class.simpleName
    }

    /**
     * Loads the set of miners from the XML file on disk.
     */
    fun reload(): Array<Miner> {
        synchronized (miners) {
            miners.clear()

            try {
                mvc.openFileInput(FILENAME).use {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(it, null)
                    parser.nextTag()
                    readMiners(parser)
                }
            } catch (_: FileNotFoundException) {
                // this is fine: initially the file of the miners is missing
                Log.w(TAG, "Missing file $FILENAME: it will be created from scratch")
            }

            return miners.toTypedArray()
        }
    }

    /**
     * Adds the given miner to this container. If it is already in this container,
     * this method does nothing.
     *
     * @param miner the miner to add
     */
    fun add(miner: Miner) {
        synchronized (miners) {
            if (miners.add(miner)) {
                writeIntoInternalStorage()
                mainScope.launch { mvc.view?.onAdded(miner) }
                Log.i(TAG, "Added miner ${miner.miningSpecification.name}")
            }
        }
    }

    /**
     * Removes the given miner from this container.
     * If it is not contained in this container, this method does nothing.
     *
     * @param miner the miner to remove
     */
    fun remove(miner: Miner) {
        synchronized (miners) {
            if (miners.remove(miner)) {
                writeIntoInternalStorage()
                mainScope.launch { mvc.view?.onDeleted(miner) }
                Log.i(TAG, "Removed miner ${miner.miningSpecification.name}")
            }
        }
    }

    /**
     * Takes note that the given miner has a plot now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return the miner information derived from {@code miner}, but where
     *         it has been taken note that the miner has a plot now
     */
    fun markHasPlot(miner: Miner): Miner {
        synchronized (miners) {
            if (!miner.hasPlotReady && miners.remove(miner)) {
                val result = miner.withPlotReady()
                miners.add(result)
                writeIntoInternalStorage()
                return result
            }
            else
                return miner
        }
    }

    /**
     * Takes note that the given miner is on now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return the miner information derived from {@code miner}, but where
     *         it has been taken note that the miner is on now
     */
    fun markAsOn(miner: Miner): Miner {
        synchronized (miners) {
            if (!miner.isOn && miners.remove(miner)) {
                val result = miner.turnedOn()
                miners.add(result)
                writeIntoInternalStorage()
                mainScope.launch { mvc.view?.onTurnedOn(result) }
                Log.i(TAG, "Turned on miner ${miner.miningSpecification.name}")
                return result
            }
            else
                return miner
        }
    }

    /**
     * Takes note that the given miner is off now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return the miner information derived from {@code miner}, but where
     *         it has been taken note that the miner is off now
     */
    fun markAsOff(miner: Miner): Miner {
        synchronized (miners) {
            if (miner.isOn && miners.remove(miner)) {
                val result = miner.turnedOff()
                miners.add(result)
                writeIntoInternalStorage()
                mainScope.launch { mvc.view?.onTurnedOff(result) }
                Log.i(TAG, "Turned off miner ${miner.miningSpecification.name}")
                return result
            }
            else
                return miner
        }
    }

    /**
     * Takes note that the given miner has the given balance now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return the miner information derived from {@code miner}, but where
     *         the new balance of the miner has been taken note of
     */
    fun markHasBalance(miner: Miner, balance: BigInteger): Miner {
        synchronized (miners) {
            if (miner.balance != balance && miners.remove(miner)) {
                val result = miner.withBalance(balance)
                miners.add(result)
                writeIntoInternalStorage()

                mainScope.launch {
                    mvc.view?.onBalanceChanged(result)
                }

                return result
            }
            else
                return miner
        }
    }

    /**
     * Yields a snapshot of the miners in this container, in increasing order.
     *
     * @return the snapshot of the miners, in increasing order
     */
    fun snapshot(): Array<Miner> {
        synchronized (miners) {
            return miners.toTypedArray()
        }
    }

    @GuardedBy("this.miners")
    private fun readMiners(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, null, MINERS_TAG)

        while (parser.next() != XmlPullParser.END_TAG)
            if (parser.eventType == XmlPullParser.START_TAG)
                if (parser.name == MINER_TAG)
                    miners.add(Miner(parser))
                else
                    skip(parser)

        parser.require(XmlPullParser.END_TAG, null, MINERS_TAG)
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        do {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
        while (depth != 0)
    }

    /**
     * Writes this set of miners in the internal storage of the app, as an XML file.
     */
    @GuardedBy("this.miners")
    private fun writeIntoInternalStorage() {
        mvc.openFileOutput(FILENAME, Context.MODE_PRIVATE).use {
            val serializer = Xml.newSerializer()
            serializer.setOutput(it, "UTF-8")
            serializer.startDocument(null, true)
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

            serializer.startTag(null, MINERS_TAG)
            miners.forEach { miner -> miner.writeWith(serializer, MINER_TAG) }
            serializer.endTag(null, MINERS_TAG)

            serializer.endDocument()
            serializer.flush()
        }
    }
}