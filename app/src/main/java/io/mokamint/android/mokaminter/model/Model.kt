package io.mokamint.android.mokaminter.model

import io.mokamint.android.mokaminter.MVC

class Model(mvc: MVC) {

    /**
     * The miners of the user.
     */
    val miners: Miners = Miners(mvc)
}