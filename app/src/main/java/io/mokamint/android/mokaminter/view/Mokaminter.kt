package io.mokamint.android.mokaminter.view

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import io.mokamint.android.mokaminter.MVC

class Mokaminter : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    companion object {
        private val TAG = Mokaminter::class.simpleName
    }

    override fun getApplicationContext(): MVC {
        return super.getApplicationContext() as MVC
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "${this::class.simpleName} started")
    }
}