package io.mokamint.android.mokaminter.view

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
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
        private val TAG = Mokaminter::class.simpleName!!
        val NOTIFICATION_CHANNEL = TAG
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
            setOf(R.id.nav_miners, R.id.nav_settings),
            binding.drawerLayout
        )

        val navController = findNavController(R.id.nav_host_fragment_content_mokaminter)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(NOTIFICATION_CHANNEL, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.notification_channel_description)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_mokaminter)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}