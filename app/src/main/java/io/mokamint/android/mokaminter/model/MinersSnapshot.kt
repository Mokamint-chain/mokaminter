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

import java.lang.IndexOutOfBoundsException
import java.util.function.BiConsumer

/**
 * A snapshot of the set of miners with their associated status.
 */
interface MinersSnapshot {

    companion object {

        /**
         * Yields an empty snapshot.
         */
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

                override fun forEach(action: BiConsumer<Miner, MinerStatus>) {
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

    /**
     * Performs the given action for each miner in this container.
     *
     * @param action the action to perform
     */
    fun forEach(action: BiConsumer<Miner, MinerStatus>)
}