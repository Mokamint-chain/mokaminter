package io.mokamint.android.mokaminter.view

import android.widget.Toast
import androidx.annotation.UiThread
import androidx.fragment.app.DialogFragment
import io.mokamint.android.mokaminter.controller.Controller
import io.mokamint.android.mokaminter.model.Model

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