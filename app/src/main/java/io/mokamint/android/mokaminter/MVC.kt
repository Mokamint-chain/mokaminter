package io.mokamint.android.mokaminter

import android.app.Application
import io.mokamint.android.mokaminter.controller.Controller
import io.mokamint.android.mokaminter.model.Model
import io.mokamint.android.mokaminter.view.View
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MVC: Application() {
    val model = Model(this)
    var view: View? = null // dynamically set to the current fragment
    val controller = Controller(this)

    companion object {
        init {
            // we remove the BC provider, since by default, in Android, it corresponds
            // to the old, internal BC provider
            Security.removeProvider("BC")
            // we register the current BC provider instead, from the BC dependency taken from Maven
            Security.addProvider(BouncyCastleProvider())

            // for more information, see
            // https://stackoverflow.com/questions/2584401/how-to-add-bouncy-castle-algorithm-to-android
            // answer by satur9nine
        }
    }
}
