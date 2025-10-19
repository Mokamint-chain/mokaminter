package io.mokamint.android.mokaminter.model

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
        const val BALANCE_TAG = "balance"
        const val HAS_PLOT_READY_TAG = "hasPlotReady"
        const val IS_ON_TAG = "isOn"
    }

    constructor(balance: BigInteger, hasPlotReady: Boolean, isOn: Boolean) {
        this.balance = balance
        this.hasPlotReady = hasPlotReady
        this.isOn = isOn
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
}