package io.mokamint.android.mokaminter.view

import androidx.annotation.UiThread

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
     * Called on the main thread whenever a background task has completed.
     */
    @UiThread fun onBackgroundEnd() {}

}