package io.mokamint.android.mokaminter.model

import android.content.Context
import android.util.Log
import android.util.Xml
import io.mokamint.android.mokaminter.MVC
import org.xmlpull.v1.XmlPullParser
import java.io.FileNotFoundException
import java.util.TreeSet
import java.util.stream.Stream

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
    fun reload() {
        miners.clear()

        try {
            mvc.openFileInput(FILENAME).use {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(it, null)
                parser.nextTag()
                readMiners(parser)
            }
        }
        catch (_: FileNotFoundException) {
            // this is fine: initially the file of the miners is missing
            Log.w(TAG, "Missing file $FILENAME: it will be created from scratch")
        }
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
     * Writes this set of miners in the internal storage of the app, as an XML file.
     */
    fun writeIntoInternalStorage() {
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

    /**
     * Adds the given miner to this container.
     *
     * @param miner the miner to add
     */
    fun add(miner: Miner) {
        miners.add(miner)
    }

    /**
     * Removes the given miner from this container.
     *
     * @param miner the miner to remove
     */
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