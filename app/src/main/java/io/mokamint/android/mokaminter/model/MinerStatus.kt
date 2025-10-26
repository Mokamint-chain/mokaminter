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

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer
import java.math.BigInteger
import java.time.Instant

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
     * The moment of the last update of the balance of this miner,
     * in milliseconds from Unix epoch. A negative value means that
     * the balance has never been updated up to now.
     */
    var lastUpdated: Long
        private set

    /**
     * True if and only if the plot for the miner has been fully created and is
     * available in the internal storage of the app.
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
        private const val LAST_UPDATED_TAG = "last-updated"
    }

    constructor(balance: BigInteger, hasPlotReady: Boolean, isOn: Boolean, lastUpdated: Long) {
        this.balance = balance
        this.hasPlotReady = hasPlotReady
        this.isOn = isOn
        this.lastUpdated = lastUpdated
    }

    constructor(parser: XmlPullParser) {
        val tag = parser.name
        var balance: BigInteger?= null
        var hasPlotReady: Boolean? = null
        var isOn: Boolean? = null
        var lastUpdated: Long? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue

            when (parser.name) {
                HAS_PLOT_READY_TAG -> hasPlotReady = readBoolean(parser)
                IS_ON_TAG -> isOn = readBoolean(parser)
                BALANCE_TAG -> balance = readBigInteger(parser)
                LAST_UPDATED_TAG -> lastUpdated = readLong(parser)
                else -> skip(parser)
            }
        }

        parser.require(XmlPullParser.END_TAG, null, tag)

        this.balance = balance ?: throw XmlPullParserException("Missing $BALANCE_TAG in miner")
        this.hasPlotReady = hasPlotReady ?: throw XmlPullParserException("Missing $HAS_PLOT_READY_TAG field in miner")
        this.isOn = isOn ?: throw XmlPullParserException("Missing $IS_ON_TAG field in miner")
        this.lastUpdated = lastUpdated ?: throw XmlPullParserException("Missing $LAST_UPDATED_TAG field in miner")
    }

    fun writeWith(serializer: XmlSerializer, tag: String) {
        serializer.startTag(null, tag)

        serializer.startTag(null, BALANCE_TAG)
        serializer.text(balance.toString())
        serializer.endTag(null, BALANCE_TAG)

        serializer.startTag(null, LAST_UPDATED_TAG)
        serializer.text(lastUpdated.toString())
        serializer.endTag(null, LAST_UPDATED_TAG)

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

    fun setBalance(balance: BigInteger) {
        setLastUpdated()

        if (balance != this.balance)
            this.balance = balance
    }

    fun setLastUpdated() {
        this.lastUpdated = Instant.now().toEpochMilli() // UTC time
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
        return "isOn: $isOn, balance: $balance, lastUpdated: $lastUpdated, hasPlotReady: $hasPlotReady"
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

    private fun readLong(parser: XmlPullParser): Long {
        val value = readText(parser)

        try {
            return value.toLong()
        }
        catch (e: NumberFormatException) {
            throw XmlPullParserException("Illegal lastUpdated: ${e.message}")
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