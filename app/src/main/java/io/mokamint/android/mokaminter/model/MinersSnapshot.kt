package io.mokamint.android.mokaminter.model

import java.lang.IndexOutOfBoundsException

/**
 * A snapshot of the set of miners with their associated status.
 */
interface MinersSnapshot {

    companion object {
        fun empty(): MinersSnapshot {
            return object: MinersSnapshot {
                override fun size(): Int {
                    return 0
                }

                override fun getMiner(pos: Int): Miner {
                    throw IndexOutOfBoundsException("$pos")
                }

                override fun getStatus(pos: Int): MinerStatus {
                    throw IndexOutOfBoundsException("$pos")
                }

                override fun indexOf(miner: Miner): Int {
                    return -1
                }
            }
        }
    }

    /**
     * Yields the number of miners in this snapshot.
     */
    fun size(): Int

    /**
     * Yields the miner with the given progressive position in this snapshot.
     * The position refers to the non-decreasing order of the miners.
     */
    fun getMiner(pos: Int): Miner

    /**
     * Yields the status of the miner with the given progressive position in this snapshot.
     * The position refers to the non-decreasing order of the miners.
     */
    fun getStatus(pos: Int): MinerStatus

    /**
     * Yields the position of the given miner in this snapshot.
     *
     * @param miner the miner
     * @return the position of {@code miner} in this snapshot, or -1 if it does not
     *         belong to this snapshot
     */
    fun indexOf(miner: Miner): Int
}