package io.mokamint.android.mokaminter.model

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.math.BigInteger

/**
 * The state of a miner.
 */
class MinerStatus {

    /**
     * The balance of the miner.
     */
    var balance: BigInteger
        private set

    /**
     * True if and only if the plot for the miner has been fully created and is
     * available in the local storage of the app.
     */
    var hasPlotReady: Boolean
        private set

    /**
     * True if and only if mining with the miner is turned on.
     */
    var isOn: Boolean
        private set

    companion object {
        private const val BALANCE_TAG = "balance"
        private const val HAS_PLOT_READY_TAG = "has-plot-ready"
        private const val IS_ON_TAG = "is-on"
    }

    constructor(balance: BigInteger, hasPlotReady: Boolean, isOn: Boolean) {
        this.balance = balance
        this.hasPlotReady = hasPlotReady
        this.isOn = isOn
    }

    constructor(parser: XmlPullParser) {
        val tag = parser.name
        var balance: BigInteger?= null
        var hasPlotReady: Boolean? = null
        var isOn: Boolean? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            when (parser.name) {
                HAS_PLOT_READY_TAG -> hasPlotReady = readBoolean(parser)
                IS_ON_TAG -> isOn = readBoolean(parser)
                BALANCE_TAG -> balance = readBigInteger(parser)
                else -> skip(parser)
            }
        }

        parser.require(XmlPullParser.END_TAG, null, tag)

        this.balance = balance ?: throw XmlPullParserException("Missing balance in miner")
        this.hasPlotReady = hasPlotReady ?: throw XmlPullParserException("Missing hasPlotReady field in miner")
        this.isOn = isOn ?: throw XmlPullParserException("Missing isOn field in miner")
    }

    fun writeWith(serializer: XmlSerializer, tag: String) {
        serializer.startTag(null, tag)

        serializer.startTag(null, BALANCE_TAG)
        serializer.text(balance.toString())
        serializer.endTag(null, BALANCE_TAG)

        serializer.startTag(null, HAS_PLOT_READY_TAG)
        serializer.text(hasPlotReady.toString())
        serializer.endTag(null, HAS_PLOT_READY_TAG)

        serializer.startTag(null, IS_ON_TAG)
        serializer.text(isOn.toString())
        serializer.endTag(null, IS_ON_TAG)

        serializer.endTag(null, tag)
    }

    fun markPlotReady(): Boolean {
        if (hasPlotReady)
            return false
        else {
            hasPlotReady = true
            return true
        }
    }

    fun setBalance(balance: BigInteger): Boolean {
        if (balance != this.balance) {
            this.balance = balance
            return true
        }
        else return false
    }

    fun turnOn(): Boolean {
        if (isOn)
            return false
        else {
            isOn = true
            return true
        }
    }

    fun turnOff(): Boolean {
        if (isOn) {
            isOn = false
            return true
        }
        else return false
    }

    override fun toString(): String {
        return "isOn: $isOn, balance: $balance, hasPlotReady: $hasPlotReady"
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
}