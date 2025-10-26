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

package io.mokamint.android.mokaminter

import android.app.Application
import io.mokamint.android.mokaminter.controller.Controller
import io.mokamint.android.mokaminter.model.Model
import io.mokamint.android.mokaminter.view.View
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * The application, as a model/view/controller triple.
 */
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
