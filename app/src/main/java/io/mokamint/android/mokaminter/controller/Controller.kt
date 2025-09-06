package io.mokamint.android.mokaminter.controller

import io.mokamint.android.mokaminter.MVC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class Controller(private val mvc: MVC) {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mainScope = CoroutineScope(Dispatchers.Main)
}