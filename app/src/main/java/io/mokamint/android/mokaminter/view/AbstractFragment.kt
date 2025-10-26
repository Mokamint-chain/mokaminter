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

import android.app.Activity
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.controller.Controller
import io.mokamint.android.mokaminter.model.Model

/**
 * Shared implementation of the fragments of this application.
 */
abstract class AbstractFragment<V: ViewBinding> : Fragment(), View {
    private var _binding: V? = null
    private var progressBar: ProgressBar? = null
    protected val binding get() = _binding!!

    @UiThread protected fun setBinding(binding: V) {
        _binding = binding
        progressBar = binding.root.findViewById(R.id.progress_bar)
        progressBar?.visibility = if (getController().isWorking())
            android.view.View.VISIBLE else android.view.View.GONE
    }

    @UiThread override fun onStart() {
        super.onStart()
        // we are the view
        context.applicationContext.view = this
        setSubtitle("")
    }

    @UiThread override fun onStop() {
        // there is no view anymore
        context.applicationContext.view = null
        closeKeyboard()
        super.onStop()
    }

    @UiThread override fun onBackgroundStart() {
        progressBar?.visibility = android.view.View.VISIBLE
    }

    @UiThread override fun onBackgroundEnd() {
        progressBar?.visibility = android.view.View.GONE
    }

    @UiThread protected fun closeKeyboard() {
        val inputMethodManager: InputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager

        if (inputMethodManager.isAcceptingText)
            context.currentFocus?.let {
                inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
            }
    }

    @UiThread override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getContext(): Mokaminter {
        return super.getContext() as Mokaminter
    }

    protected fun navigate(where: NavDirections) {
        findNavController().navigate(where)
    }

    protected fun popBackStack() {
        findNavController().popBackStack()
    }

    protected fun setSubtitle(subtitle: String) {
        context.supportActionBar!!.subtitle = subtitle
    }

    protected fun getController(): Controller {
        return context.applicationContext.controller
    }

    protected fun getModel(): Model {
        return context.applicationContext.model
    }

    @UiThread override fun notifyUser(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}