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

import android.content.Context
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import io.mokamint.android.mokaminter.R

/**
 * An image button that controls the visibility of a text view for entering a password.
 */
class PasswordVisibilityToggle(context: Context, attrs: AttributeSet) : AppCompatImageButton(context, attrs) {

    private val controls: Int

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.PasswordVisibilityToggle,
            0, 0).apply {

            try {
                controls = getResourceId(R.styleable.PasswordVisibilityToggle_controls, -1)
            }
            finally {
                recycle()
            }
        }

        setOnClickListener { togglePassword() }
    }

    private fun togglePassword() {
        if (controls >= 0) {
            var parent = this.parent
            while (parent.parent is View)
                parent = parent.parent

            if (parent is View) {
                val target: TextView = parent.findViewById(controls)
                target.let {
                    isActivated = !isActivated
                    if (isActivated)
                        it.transformationMethod = HideReturnsTransformationMethod.getInstance()
                    else
                        it.transformationMethod = PasswordTransformationMethod.getInstance()
                }
            }
        }
    }
}