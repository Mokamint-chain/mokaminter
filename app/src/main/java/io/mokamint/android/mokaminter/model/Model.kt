package io.mokamint.android.mokaminter.model

import io.mokamint.android.mokaminter.MVC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class Model(private val mvc: MVC) {
    private val mainScope = CoroutineScope(Dispatchers.Main)

}