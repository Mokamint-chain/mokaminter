package io.mokamint.android.mokaminter.model

import android.util.Log
import android.util.Xml
import io.hotmoka.crypto.Entropies
import io.hotmoka.crypto.HashingAlgorithms
import io.hotmoka.crypto.SignatureAlgorithms
import io.mokamint.android.mokaminter.MVC
import io.mokamint.miner.MiningSpecifications
import org.xmlpull.v1.XmlPullParser
import java.io.FileNotFoundException
import java.net.URI
import java.util.TreeSet
import java.util.stream.Stream

/**
 * The set of miners. They are constrained to have distinct name.
 */
class Miners {

    private val mvc: MVC

    /**
     * The ordered set of miners contained in this container.
     */
    private val miners = TreeSet<Miner>()

    companion object {

        /**
         * The name of the file where the miners are stored, in the internal storage of the app.
         */
        private const val FILENAME = "miners.txt"
        private const val MINERS_TAG = "miners"
        private const val MINER_TAG = "miner"
        private val TAG = Miners::class.simpleName

        /**
         * Yields an empty set of miners.
         *
         * @return the empty set of miners
         */
        fun empty(mvc: MVC): Miners {
            return Miners(mvc)
        }

        /**
         * Loads the set of miners from the XML file on disk.
         *
         * @return the resulting set of miners
         */
        fun load(mvc: MVC): Miners {
            val miners = Miners(mvc)

            try {
                mvc.openFileInput(FILENAME).use {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(it, null)
                    parser.nextTag()
                    miners.readMiners(parser)
                    Log.i(TAG, "Loaded miners from file $FILENAME")
                }
            }
            catch (_: FileNotFoundException) {
                // this is fine: initially the file of the miners is missing
                Log.w(TAG, "Missing file $FILENAME: it will be created from scratch, using an empty set of miners for now")
            }

            val ed25519 = SignatureAlgorithms.ed25519()

            miners.add(Miner(MiningSpecifications.of(
                "Hotmoka",
                "The greatest blockchain in the world",
                "octopus",
                HashingAlgorithms.sha256(),
                ed25519,
                ed25519,
                ed25519.keyPair.public
            ), URI.create("ws://panarea.hotmoka.io:8025"), Entropies.random()))

            miners.add(Miner(MiningSpecifications.of(
                "Still Hotmoka",
                "Still the greatest blockchain in the world",
                "mryia",
                HashingAlgorithms.sha256(),
                ed25519,
                ed25519,
                ed25519.keyPair.public
            ), URI.create("ws://panarea.hotmoka.io:8026"), Entropies.random()))

            return miners
        }
    }

    /**
     * Loads the miners from the XML database on disk.
     *
     * @param mvc the MVC triple
     */
    private constructor(mvc: MVC) {
        this.mvc = mvc
    }

    private fun readMiners(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, null, MINERS_TAG)

        while (parser.next() != XmlPullParser.END_TAG)
            if (parser.eventType == XmlPullParser.START_TAG)
                if (parser.name == MINER_TAG)
                    add(Miner(parser))
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
     * Adds the given miner to this container.
     *
     * @param miner the miner to add
     * @return true if and only if {@code miner} has neen added; a miner will not
     *         be added if there is already another miner in this container, with the same name
     */
    fun add(miner: Miner): Boolean {
        val added = miners.add(miner)
        if (added)
            Log.i(TAG, "Added miner $miner")
        else
            Log.w(TAG, "Rejected addition of $miner since a miner with the same name already exists")

        return added
    }

    fun remove(miner: Miner) {
        miners.remove(miner)
    }

    /**
     * Yields the miners in this container, in increasing order.
     *
     * @return the miners, in increasing order
     */
    fun stream(): Stream<Miner> {
        return miners.stream()
    }
}