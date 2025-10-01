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