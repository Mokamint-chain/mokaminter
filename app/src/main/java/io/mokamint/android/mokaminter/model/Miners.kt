package io.mokamint.android.mokaminter.model

import android.content.Context
import android.util.Log
import android.util.Xml
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

    /**
     * Adds the given miner to this container. If it is already in this container,
     * this method does nothing.
     *
     * @param miner the miner to add
     */
    fun add(miner: Miner) {
        if (miners.add(miner)) {
            writeIntoInternalStorage()
            Log.i(TAG, "Added miner ${miner.miningSpecification.name}")
        }
    }

    /**
     * Removes the given miner from this container.
     * If it is not contained in this container, this method does nothing.
     *
     * @param miner the miner to remove
     * @return true if and only if the miner was in this set of miners
     */
    fun remove(miner: Miner): Boolean {
        if (miners.remove(miner)) {
            writeIntoInternalStorage()
            Log.i(TAG, "Removed miner ${miner.miningSpecification.name}")
            return true
        }
        else return false
    }

    /**
     * Takes note that the given miner has a plot now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return the miner in this set marked as having a plot; yields {@code null} if
     *         the miner is not contained in this set of miners
     */
    fun markHasPlot(miner: Miner): Miner? {
        try {
            var miner = miners.first { m -> m == miner }

            if (!miner.hasPlotReady) {
                miners.remove(miner)
                miner = miner.withPlotReady()
                miners.add(miner)
                writeIntoInternalStorage()
            }

            return miner
        }
        catch (_: NoSuchElementException) {
            return null
        }
    }

    /**
     * Takes note that the given miner is on now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return the miner in this set turned on; yields {@code null} if
     *         the miner is not contained in this set of miners
     */
    fun markAsOn(miner: Miner): Miner? {
        try {
            var miner = miners.first { m -> m == miner }

            if (!miner.isOn) {
                miners.remove(miner)
                miner = miner.turnedOn()
                miners.add(miner)
                writeIntoInternalStorage()
                mainScope.launch { mvc.view?.onTurnedOn(miner) }
                Log.i(TAG, "Turned on miner $miner")
            }

            return miner
        }
        catch (_: NoSuchElementException) {
            return null
        }
    }

    /**
     * Takes note that the given miner is off now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return the miner in this set turned off; yields {@code null} if
     *         the miner is not contained in this set of miners
     */
    fun markAsOff(miner: Miner): Miner? {
        try {
            var miner = miners.first { m -> m == miner }

            if (miner.isOn) {
                miners.remove(miner)
                miner = miner.turnedOff()
                miners.add(miner)
                writeIntoInternalStorage()
                mainScope.launch { mvc.view?.onTurnedOff(miner) }
                Log.i(TAG, "Turned off miner $miner")
            }

            return miner
        }
        catch (_: NoSuchElementException) {
            return null
        }
    }

    /**
     * Takes note that the given miner has the given balance now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @param balance the new balance to set for {@code miner}
     * @return the miner in this set with the updated balance; yields {@code null} if
     *         the miner is not contained in this set of miners
     */
    fun setBalance(miner: Miner, balance: BigInteger): Miner? {
        try {
            var miner = miners.first { m -> m == miner }

            if (miner.balance != balance) {
                miners.remove(miner)
                miner = miner.withBalance(balance)
                miners.add(miner)
                writeIntoInternalStorage()
                mainScope.launch { mvc.view?.onBalanceChanged(miner) }
                Log.i(TAG, "Updated balance of miner $miner to $balance")
            }

            return miner
        }
        catch (_: NoSuchElementException) {
            return null
        }
    }

    /**
     * Yields a snapshot of the miners in this container, in increasing order.
     *
     * @return the snapshot of the miners, in increasing order
     */
    fun snapshot(): Array<Miner> {
        return miners.toTypedArray()
    }

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