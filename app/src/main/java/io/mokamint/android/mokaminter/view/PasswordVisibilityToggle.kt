package io.mokamint.android.mokaminter.view

import android.content.Context
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import io.mokamint.android.mokaminter.R

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