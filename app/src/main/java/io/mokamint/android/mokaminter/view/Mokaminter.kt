package io.mokamint.android.mokaminter.view

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import io.mokamint.android.mokaminter.MVC
import io.mokamint.android.mokaminter.R
import io.mokamint.android.mokaminter.databinding.MokaminterBinding

class Mokaminter : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    companion object {
        private val TAG = Mokaminter::class.simpleName
    }

    override fun getApplicationContext(): MVC {
        return super.getApplicationContext() as MVC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = MokaminterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMokaminter.toolbar)

        // passing each menu ID as a set of Ids because each
        // menu should be considered as top level destination
        appBarConfiguration = AppBarConfiguration(
            setOf(
                // R.id.nav_accounts, R.id.settings
            ), binding.drawerLayout
        )

        val navController = findNavController(R.id.nav_host_fragment_content_mokaminter)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "$TAG started")
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_mokaminter)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}