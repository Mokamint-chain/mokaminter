package io.mokamint.android.mokaminter.model

import io.mokamint.android.mokaminter.MVC

/**
 * The model of the MVC triple.
 *
 * @param mvc the MVC triple
 */
class Model(mvc: MVC) {

    /**
     * The miners of the user.
     */
    val miners: Miners = Miners(mvc)
}