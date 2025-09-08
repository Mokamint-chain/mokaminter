package io.mokamint.android.mokaminter.model

import android.util.Log
import android.util.Xml
import io.mokamint.android.mokaminter.MVC
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalStateException
import java.math.BigInteger
import java.util.TreeSet

/**
 * The set of miners.
 */
class Miners(mvc: MVC) {

    /**
     * The name of the file where the miners are stored, in the internal storage of the app.
     */
    private val minersFilename = "miners.txt"

    /**
     * The ordered set of miners contained in this container.
     */
    private val miners = TreeSet<Miner>()

    companion object {
        private val TAG = Miners::class.simpleName
    }

    init {
        try {
            mvc.openFileInput(minersFilename).use {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(it, null)
                parser.nextTag()
                readMiners(parser)
            }
        } catch (e: FileNotFoundException) {
            // this is fine: initially the file of the miners is missing
            Log.w(TAG, "Missing file $minersFilename: it will be created from scratch")
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readMiners(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, null, "miners")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            // starts by looking for the miner tag
            if (parser.name == "miner")
                add(Miner(parser))
            else
                skip(parser)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG)
            throw IllegalStateException("skip() was meant to be called on a START XML tag")

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
     * Adds the given miner to this object.
     *
     * @param miner the miner to add
     */
    fun add(miner: Miner) {
        miners.add(miner)
        Log.i(TAG, "Added miner $miner")
    }
}