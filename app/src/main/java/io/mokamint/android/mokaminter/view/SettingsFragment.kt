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

import android.os.Bundle
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.mokamint.android.mokaminter.R

class SettingsFragment: PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener, View {

    @UiThread override fun onStart() {
        super.onStart()
        context.applicationContext.view = this
        context.supportActionBar!!.subtitle = ""
    }

    @UiThread override fun onStop() {
        context.applicationContext.view = null
        super.onStop()
    }

    override fun getContext(): Mokaminter {
        return super.getContext() as Mokaminter
    }

    @UiThread override fun notifyUser(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    @UiThread override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        findPreference<Preference>("max_plot_size")?.onPreferenceChangeListener = this
    }

    @UiThread override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (preference.key == "max_plot_size") {
            if (newValue is String) {
                try {
                    if (newValue.toLong() >= 1)
                        return true
                }
                catch (_: NumberFormatException) {
                }

                notifyUser(getString(R.string.settings_plot_size_must_be_a_positive_integer))
            }
        }

        return false
    }
}