package io.mokamint.android.mokaminter.view

import androidx.annotation.UiThread
import io.mokamint.android.mokaminter.model.Miner

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
     * Called when the set of miners has been reloaded.
     */
    @UiThread fun onMinersReloaded() {}

    /**
     * Called when a miner has been deleted.
     *
     * @param deleted the deleted miner
     */
    @UiThread fun onMinerDeleted(deleted: Miner) {}

    /**
     * Called when a miner has been added.
     *
     * @param added the added miner
     */
    @UiThread fun onMinerAdded(added: Miner) {}

    /**
     * Called when the app is ready to create the plot for the
     * given miner but needs the authorization of the user.
     *
     * @param miner the miner for which the plot should be created
     */
    @UiThread fun onReadyToCreatePlotFor(miner: Miner) {}
}