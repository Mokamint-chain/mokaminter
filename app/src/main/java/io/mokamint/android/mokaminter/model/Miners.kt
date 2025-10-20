package io.mokamint.android.mokaminter.model

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.annotation.GuardedBy
import io.hotmoka.crypto.Base58
import io.hotmoka.crypto.HashingAlgorithms
import io.hotmoka.crypto.SignatureAlgorithms
import io.hotmoka.crypto.api.HashingAlgorithm
import io.hotmoka.crypto.api.SignatureAlgorithm
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.model.Miner.Companion.CHAIN_ID_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.DESCRIPTION_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.HASHING_FOR_DEADLINES_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.MINING_SPECIFICATION_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.NAME_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.SIGNATURE_FOR_BLOCKS_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.SIGNATURE_FOR_DEADLINES_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.SIZE_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.URI_TAG
import io.mokamint.android.mokaminter.model.Miner.Companion.UUID_TAG
import io.mokamint.android.mokaminter.model.MinerStatus.Companion.BALANCE_TAG
import io.mokamint.android.mokaminter.model.MinerStatus.Companion.HAS_PLOT_READY_TAG
import io.mokamint.android.mokaminter.model.MinerStatus.Companion.IS_ON_TAG
import io.mokamint.miner.MiningSpecifications
import io.mokamint.miner.api.MiningSpecification
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.io.FileNotFoundException
import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.util.UUID

/**
 * The set of miners. They are constrained to have distinct name.
 *
 * @param mvc the MVC triple
 */
class Miners(private val mvc: MVC) {

    /**
     * The miners contained in this container, with their status.
     * Access to this map is synchronized in order to guarantee its
     * consistency and its alignment with the file containing the
     * same information on persistent storage.
     */
    @GuardedBy("itself")
    private val miners = HashMap<Miner, MinerStatus>()

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
    fun reload(): MinersSnapshot {
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
                Log.i(TAG, "Removed miner $miner")
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
            var status = miners[miner]

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
            var status = miners[miner]

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
            var status = miners[miner]

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
     * @return true if and only if the miner belongs to this set and has its balance has been changed
     */
    fun setBalance(miner: Miner, balance: BigInteger): Boolean {
        synchronized (miners) {
            var status = miners[miner]
            if (status != null && status.setBalance(balance)) {
                writeIntoInternalStorage()
                // mainScope.launch { mvc.view?.onBalanceChanged(miner) } // TODO
                Log.i(TAG, "Updated balance of miner $miner to $balance")
                return true
            }

            return false
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
            }
        }
    }

    private fun readMiners(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, null, MINERS_TAG)

        while (parser.next() != XmlPullParser.END_TAG)
            if (parser.eventType == XmlPullParser.START_TAG)
                if (parser.name == MINER_TAG)
                    readMinerAndStatus(parser)
                else
                    skip(parser)

        parser.require(XmlPullParser.END_TAG, null, MINERS_TAG)
    }

    private fun readMinerAndStatus(parser: XmlPullParser) {
        val tag = parser.name
        var uuid: UUID? = null
        var miningSpecification: MiningSpecification? = null
        var uri: URI? = null
        var size: Long? = null
        var publicKeyBase58: String? = null
        var balance: BigInteger?= null
        var hasPlotReady: Boolean? = null
        var isOn: Boolean? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            when (parser.name) {
                UUID_TAG -> uuid = readUUID(parser)
                MINING_SPECIFICATION_TAG -> miningSpecification = readMiningSpecification(parser)
                URI_TAG -> uri = readURI(parser)
                SIZE_TAG -> size = readSize(parser)
                PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG -> publicKeyBase58 = readText(parser)
                HAS_PLOT_READY_TAG -> hasPlotReady = readBoolean(parser)
                IS_ON_TAG -> isOn = readBoolean(parser)
                BALANCE_TAG -> balance = readBigInteger(parser)
                else -> skip(parser)
            }
        }

        parser.require(XmlPullParser.END_TAG, null, tag)

        val miner: Miner

        try {
            miner = Miner(
                uuid ?: throw XmlPullParserException("Missing UUID in miner"),
                miningSpecification ?: throw XmlPullParserException("Missing mining specification in miner"),
                uri ?: throw XmlPullParserException("Missing URI in miner"),
                size ?: throw XmlPullParserException("The plot size must be provided and be positive"),
                Base58.requireBase58(publicKeyBase58, ::XmlPullParserException),
            )
        } catch (e: InvalidKeySpecException) {
            throw XmlPullParserException(e.message)
        }

        val status = MinerStatus(
            balance ?: throw XmlPullParserException("Missing balance in miner"),
            hasPlotReady ?: throw XmlPullParserException("Missing hasPlotReady field in miner"),
            isOn ?: throw XmlPullParserException("Missing isOn field in miner")
        )

        miners.put(miner, status)
    }

    private fun readMiningSpecification(parser: XmlPullParser): MiningSpecification {
        val tag = parser.name
        var name: String? = null
        var description: String? = null
        var chainId: String? = null
        var hashingForDeadlines: HashingAlgorithm? = null
        var signatureForBlocks: SignatureAlgorithm? = null
        var signatureForDeadlines: SignatureAlgorithm? = null
        var publicKeyForSigningBlocksBase58: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            when (parser.name) {
                NAME_TAG -> name = readText(parser)
                DESCRIPTION_TAG -> description = readText(parser)
                CHAIN_ID_TAG -> chainId = readText(parser)
                HASHING_FOR_DEADLINES_TAG -> hashingForDeadlines = readHashingAlgorithm(parser)
                SIGNATURE_FOR_BLOCKS_TAG -> signatureForBlocks = readSignatureAlgorithm(parser)
                SIGNATURE_FOR_DEADLINES_TAG -> signatureForDeadlines = readSignatureAlgorithm(parser)
                PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG -> publicKeyForSigningBlocksBase58 = readText(parser)
                else -> skip(parser)
            }
        }

        if (signatureForBlocks == null)
            throw XmlPullParserException("Missing signatureForBlocks tag in miner")

        if (publicKeyForSigningBlocksBase58 == null)
            throw XmlPullParserException("Missing publicKeyForSigningBlocksBase58 tag in miner")

        parser.require(XmlPullParser.END_TAG, null, tag)

        try {
            return MiningSpecifications.of(
                name ?: throw XmlPullParserException("Missing name tag in miner"),
                description ?: throw XmlPullParserException("Missing description tag in miner"),
                chainId ?: throw XmlPullParserException("Missing chainId tag in miner"),
                hashingForDeadlines
                    ?: throw XmlPullParserException("Missing hashingForDeadlines tag in miner"),
                signatureForBlocks,
                signatureForDeadlines
                    ?: throw XmlPullParserException("Missing signatureForDeadlines tag in miner"),
                signatureForBlocks.publicKeyFromEncoding(
                    Base58.fromBase58String(
                        publicKeyForSigningBlocksBase58,
                        ::XmlPullParserException
                    )
                )
            )
        }
        catch (e: InvalidKeySpecException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readURI(parser: XmlPullParser): URI {
        val entropy = readText(parser)

        try {
            return URI(entropy)
        }
        catch (e: URISyntaxException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readSize(parser: XmlPullParser): Long {
        val size = readText(parser)

        try {
            val result = size.toLong()
            if (result > 0)
                return result
            else
                throw XmlPullParserException("The plot size must be strictly positive")
        }
        catch (_: NumberFormatException) {
            throw XmlPullParserException("The plot size must be a strictly positive number")
        }
    }

    private fun readUUID(parser: XmlPullParser): UUID {
        val uuid = readText(parser)

        try {
            return UUID.fromString(uuid)
        }
        catch (e: IllegalArgumentException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readBoolean(parser: XmlPullParser): Boolean {
        val value = readText(parser)

        return when (value) {
            "true" -> true
            "false" -> false
            else -> throw XmlPullParserException("Expected Boolean")
        }
    }

    private fun readBigInteger(parser: XmlPullParser): BigInteger {
        val value = readText(parser)

        try {
            return BigInteger(value)
        }
        catch (e: NumberFormatException) {
            throw XmlPullParserException("Illegal balance: ${e.message}")
        }
    }

    private fun readHashingAlgorithm(parser: XmlPullParser): HashingAlgorithm {
        val signature = readText(parser)

        try {
            return HashingAlgorithms.of(signature)
        }
        catch (e: NoSuchAlgorithmException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readSignatureAlgorithm(parser: XmlPullParser): SignatureAlgorithm {
        val signature = readText(parser)

        try {
            return SignatureAlgorithms.of(signature)
        }
        catch (e: NoSuchAlgorithmException) {
            throw XmlPullParserException(e.message)
        }
    }

    private fun readText(parser: XmlPullParser): String {
        val tag = parser.name

        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }

        parser.require(XmlPullParser.END_TAG, null, tag)

        return result
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
            miners.forEach { miner, status -> writeWith(serializer, miner, status) }
            serializer.endTag(null, MINERS_TAG)

            serializer.endDocument()
            serializer.flush()
        }
    }

    private fun writeWith(serializer: XmlSerializer, miner: Miner, status: MinerStatus) {
        serializer.startTag(null, MINER_TAG)

        serializer.startTag(null, UUID_TAG)
        serializer.text(miner.uuid.toString())
        serializer.endTag(null, UUID_TAG)
        serializer.startTag(null, MINING_SPECIFICATION_TAG)
        serializer.startTag(null, NAME_TAG)
        serializer.text(miner.miningSpecification.name)
        serializer.endTag(null, NAME_TAG)
        serializer.startTag(null, DESCRIPTION_TAG)
        serializer.text(miner.miningSpecification.description)
        serializer.endTag(null, DESCRIPTION_TAG)
        serializer.startTag(null, CHAIN_ID_TAG)
        serializer.text(miner.miningSpecification.chainId)
        serializer.endTag(null, CHAIN_ID_TAG)
        serializer.startTag(null, HASHING_FOR_DEADLINES_TAG)
        serializer.text(miner.miningSpecification.hashingForDeadlines.name)
        serializer.endTag(null, HASHING_FOR_DEADLINES_TAG)
        serializer.startTag(null, SIGNATURE_FOR_BLOCKS_TAG)
        serializer.text(miner.miningSpecification.signatureForBlocks.name)
        serializer.endTag(null, SIGNATURE_FOR_BLOCKS_TAG)
        serializer.startTag(null, SIGNATURE_FOR_DEADLINES_TAG)
        serializer.text(miner.miningSpecification.signatureForDeadlines.name)
        serializer.endTag(null, SIGNATURE_FOR_DEADLINES_TAG)
        serializer.startTag(null, PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG)
        serializer.text(miner.miningSpecification.publicKeyForSigningBlocksBase58)
        serializer.endTag(null, PUBLIC_KEY_FOR_SIGNING_BLOCKS_BASE58_TAG)
        serializer.endTag(null, MINING_SPECIFICATION_TAG)

        serializer.startTag(null, URI_TAG)
        serializer.text(miner.uri.toString())
        serializer.endTag(null, URI_TAG)

        serializer.startTag(null, SIZE_TAG)
        serializer.text(miner.size.toString())
        serializer.endTag(null, SIZE_TAG)

        serializer.startTag(null, PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG)
        serializer.text(miner.publicKeyBase58)
        serializer.endTag(null, PUBLIC_KEY_FOR_SIGNING_DEADLINES_BASE58_TAG)

        serializer.startTag(null, BALANCE_TAG)
        serializer.text(status.balance.toString())
        serializer.endTag(null, BALANCE_TAG)

        serializer.startTag(null, HAS_PLOT_READY_TAG)
        serializer.text(status.hasPlotReady.toString())
        serializer.endTag(null, HAS_PLOT_READY_TAG)

        serializer.startTag(null, IS_ON_TAG)
        serializer.text(status.isOn.toString())
        serializer.endTag(null, IS_ON_TAG)

        serializer.endTag(null, MINER_TAG)
    }
}