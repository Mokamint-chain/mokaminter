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

package io.mokamint.android.mokaminter.view

import android.widget.Toast
import androidx.annotation.UiThread
import androidx.fragment.app.DialogFragment
import io.mokamint.android.mokaminter.controller.Controller
import io.mokamint.android.mokaminter.model.Model

/**
 * Shared implementation of the dialog fragments of this application.
 */
abstract class AbstractDialogFragment: DialogFragment() {

    override fun getContext(): Mokaminter {
        return super.getContext() as Mokaminter
    }

    protected fun getController(): Controller {
        return context.applicationContext.controller
    }

    protected fun getModel(): Model {
        return context.applicationContext.model
    }

    @UiThread
    protected fun notifyUser(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}