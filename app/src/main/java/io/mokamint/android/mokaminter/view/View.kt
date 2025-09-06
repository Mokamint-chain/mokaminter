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
    @UiThread
    fun notifyUser(message: String)
}