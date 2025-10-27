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

package io.mokamint.android.mokaminter.model

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.annotation.GuardedBy
import io.hotmoka.annotations.ThreadSafe
import io.mokamint.android.mokaminter.MVC
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.math.BigInteger
import java.util.UUID
import java.util.function.BiConsumer

/**
 * The set of miners.
 *
 * @param mvc the MVC triple
 */
@ThreadSafe
class Miners(private val mvc: MVC) {

    /**
     * The miners contained in this container, with their status.
     * Access to this map is synchronized in order to guarantee its
     * consistency and its alignment with the XML file containing the
     * same information in the persistent internal storage of the app.
     */
    @GuardedBy("itself")
    private val miners = HashMap<Miner, MinerStatus>()

    companion object {

        /**
         * The name of the file where the miners are stored, in the internal storage of the app.
         */
        private const val FILENAME = "miners.txt"
        private const val MINERS_TAG = "miners"
        private const val MINER_SPECIFICATION_TAG = "miner-specification"
        private const val MINER_TAG = "miner"
        private const val STATUS_TAG = "status"
        private val TAG = Miners::class.simpleName
    }

    /**
     * Loads the set of miners from the XML file on persistent storage.
     */
    fun reload(): MinersSnapshot {
        synchronized (miners) {
            miners.clear()
            readFromInternalStorage()
            return snapshot()
        }
    }

    /**
     * Adds the given miner to this container. If it is already in this container,
     * this method does nothing.
     *
     * @param miner the miner to add
     * @param status the status of the miner to add
     */
    fun add(miner: Miner, status: MinerStatus) {
        synchronized (miners) {
            miners.put(miner, status)
            writeIntoInternalStorage()
            Log.i(TAG, "Added miner $miner")
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
        synchronized (miners) {
            if (miners.remove(miner) != null) {
                writeIntoInternalStorage()
                return true
            } else return false
        }
    }

    /**
     * Takes note that the given miner has a plot now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return true if and only if the miner belongs to this set and did not have the plot ready
     */
    fun markHasPlot(miner: Miner): Boolean {
        synchronized (miners) {
            val status = miners[miner]

            if (status != null && status.markPlotReady()) {
                writeIntoInternalStorage()
                Log.i(TAG, "Marked existence of plot for miner $miner")
                return true
            }

            return false
        }
    }

    /**
     * Takes note that the given miner is on now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return true if and only if the miner belongs to this set and was off
     */
    fun markAsOn(miner: Miner): Boolean {
        synchronized (miners) {
            val status = miners[miner]

            if (status != null && status.turnOn()) {
                writeIntoInternalStorage()
                Log.i(TAG, "Turned on miner $miner")
                return true
            }

            return false
        }
    }

    /**
     * Takes note that the given miner is off now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @return true if and only if the miner belongs to this set and was on
     */
    fun markAsOff(miner: Miner): Boolean {
        synchronized (miners) {
            val status = miners[miner]

            if (status != null && status.turnOff()) {
                writeIntoInternalStorage()
                Log.i(TAG, "Turned off miner $miner")
                return true
            }

            return false
        }
    }

    /**
     * Takes note that the given miner has the given balance now.
     *
     * @param miner the miner; if it does not belong to this set of miners,
     *              this method does nothing
     * @param balance the new balance to set for {@code miner}
     * @return true if and only if the miner belongs to this set
     */
    fun setBalance(miner: Miner, balance: BigInteger): Boolean {
        synchronized (miners) {
            val status = miners[miner]
            if (status != null) {
                if (status.balance != balance) {
                    status.setBalance(balance)
                    writeIntoInternalStorage()
                    Log.i(TAG, "Updated balance of miner $miner to $balance")
                }
                else {
                    status.setLastUpdated()
                    writeIntoInternalStorage()
                }

                return true
            }

            return false
        }
    }

    /**
     * Yields the miner in this container, having the given identifier.
     *
     * @param uuid the identifier of the requested miner
     * @return the miner, if it exists
     */
    fun get(uuid: UUID) : Miner? {
        return try {
            synchronized (miners) {
                miners.keys.first { miner -> miner.uuid == uuid }
            }
        } catch (_: NoSuchElementException) {
            null
        }
    }

    /**
     * Yields a snapshot of the miners in this container, in increasing order.
     *
     * @return the snapshot of the miners, in increasing order
     */
    fun snapshot(): MinersSnapshot {
        synchronized (miners) {
            return object: MinersSnapshot {
                private val sortedMiners = miners.keys.toSortedSet().toTypedArray()
                private val copy = HashMap(miners)

                override fun size(): Int {
                    return sortedMiners.size
                }

                override fun getMiner(pos: Int): Miner {
                    return sortedMiners[pos]
                }

                override fun getStatus(pos: Int): MinerStatus {
                    // we know that the map of miners does not bind miners to null
                    return copy[sortedMiners[pos]]!!
                }

                override fun indexOf(miner: Miner): Int {
                    return sortedMiners.indexOf(miner)
                }

                override fun forEach(action: BiConsumer<Miner, MinerStatus>) {
                    sortedMiners.forEach { miner ->  action.accept(miner, copy[miner]!!) }
                }
            }
        }
    }

    /**
     * Populates this container with a miner/status pair read from the given XML parser.
     *
     * @param parser the parser for reading from the XML file
     */
    private fun readMinerSpecification(parser: XmlPullParser) {
        val tag = parser.name
        var miner: Miner? = null
        var status: MinerStatus? = null

        while (parser.next() != XmlPullParser.END_TAG)
            if (parser.eventType == XmlPullParser.START_TAG)
                when (parser.name) {
                    MINER_TAG -> miner = Miner(parser)
                    STATUS_TAG -> status = MinerStatus(parser)
                    else -> skip(parser)
                }

        miners.put(
            miner ?: throw XmlPullParserException("Missing $MINER_TAG in miner specification"),
            status ?: throw XmlPullParserException("Missing $STATUS_TAG in miner specification")
        )

        parser.require(XmlPullParser.END_TAG, null, tag)
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
     * Reads this set of miners from the XML file in the internal storage of the app.
     */
    private fun readFromInternalStorage() {
        try {
            mvc.openFileInput(FILENAME).use {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(it, null)
                parser.nextTag()

                parser.require(XmlPullParser.START_TAG, null, MINERS_TAG)

                while (parser.next() != XmlPullParser.END_TAG)
                    if (parser.eventType == XmlPullParser.START_TAG)
                        if (parser.name == MINER_SPECIFICATION_TAG)
                            readMinerSpecification(parser)
                        else
                            skip(parser)

                parser.require(XmlPullParser.END_TAG, null, MINERS_TAG)
            }
        } catch (_: FileNotFoundException) {
            // this is fine: initially the file of the miners is missing
            Log.w(TAG, "Missing file $FILENAME: it will be created from scratch")
        }
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
            miners.forEach { miner, status ->
                serializer.startTag(null, MINER_SPECIFICATION_TAG)
                miner.writeWith(serializer, MINER_TAG)
                status.writeWith(serializer, STATUS_TAG)
                serializer.endTag(null, MINER_SPECIFICATION_TAG)
            }
            serializer.endTag(null, MINERS_TAG)

            serializer.endDocument()
            serializer.flush()
        }
    }
}