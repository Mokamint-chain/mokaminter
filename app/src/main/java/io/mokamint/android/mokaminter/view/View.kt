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
     *
     * @param miners the set of miners that have been reloaded
     */
    @UiThread fun onMinersReloaded(miners: Array<Miner>) {}

    /**
     * Called when a miner has been deleted.
     *
     * @param deleted the deleted miner
     * @param miners the remaining set of miners
     */
    @UiThread fun onDeleted(deleted: Miner, miners: Array<Miner>) {}

    /**
     * Called when a miner has been added.
     *
     * @param added the added miner
     * @param miners the total set of miners
     */
    @UiThread fun onAdded(added: Miner, miners: Array<Miner>) {}

    /**
     * Called when the balance of a miner has changed.
     *
     * @param miner the miner whose balance has changed
     */
    @UiThread fun onBalanceChanged(miner: Miner) {}

    /**
     * Called when the app is ready to create the plot for the
     * given miner but needs the authorization of the user.
     *
     * @param miner the miner for which the plot should be created
     */
    @UiThread fun onReadyToCreatePlotFor(miner: Miner) {}

    /**
     * Called when the creation of the plot for the given miner starts.
     *
     * @param miner the miner for which the plot is being created
     */
    @UiThread fun onPlotCreationStarted(miner: Miner) {}

    /**
     * Called when the creation of the plot for the given miner has
     * reached the given percent. This can be used to provide some feedback
     * to the user about the progress of the creation.
     *
     * @param miner the miner for which the plot is being created
     * @param percent the percent of creation reached (from 0 to 100 inclusive)
     */
    @UiThread fun onPlotCreationTick(miner: Miner, percent: Int) {}

    /**
     * Called when the creation of the plot for the given miner completes.
     *
     * @param miner the miner for which the plot is being created
     */
    @UiThread fun onPlotCreationCompleted(miner: Miner) {}

    /**
     * Called when the given miner has been turned on.
     *
     * @param miner the miner that has been turned on
     */
    @UiThread fun onTurnedOn(miner: Miner) {}

    /**
     * Called when the given miner has been turned off.
     *
     * @param miner the miner that has been turned off
     */
    @UiThread fun onTurnedOff(miner: Miner) {}

    /**
     * Called when the given miner has been connected.
     *
     * @param miner the miner that has been connected
     */
    @UiThread fun onConnected(miner: Miner) {}

    /**
     * Called when the given miner has been disconnected.
     *
     * @param miner the miner that has been disconnected
     */
    @UiThread fun onDisconnected(miner: Miner) {}
}