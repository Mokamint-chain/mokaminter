package io.mokamint.android.mokaminter.view

import androidx.annotation.UiThread
import io.mokamint.android.mokaminter.model.Miner
import io.mokamint.android.mokaminter.model.Miners

interface View {

    /**
     * Yields the Android context of the view.
     */
    fun getContext(): Mokaminter

    /**
     * Reports a notification message to the screen.
     *
     * @param message the message to report
     */
    @UiThread fun notifyUser(message: String)

    /**
     * Called whenever a background task has started.
     */
    @UiThread fun onBackgroundStart() {}

    /**
     * Called whenever a background task has completed.
     */
    @UiThread fun onBackgroundEnd() {}

    /**
     * Called when the set of miners has changed.
     *
     * @param newMiners the new set of miners
     */
    @UiThread fun onMinersChanged(newMiners: Miners) {}

    /**
     * Called when a miner has been deleted.
     *
     * @param deleted the deleted miner
     */
    @UiThread fun onMinerDeleted(deleted: Miner) {}
}