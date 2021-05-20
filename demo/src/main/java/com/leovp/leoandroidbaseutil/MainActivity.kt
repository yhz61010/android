package com.leovp.leoandroidbaseutil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hjq.permissions.OnPermission
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_common, R.id.navigation_jetpack
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        XXPermissions.with(this)
            .permission(
                Permission.MANAGE_EXTERNAL_STORAGE, Permission.CAMERA, Permission.RECORD_AUDIO, Permission.ACCESS_COARSE_LOCATION,
                Permission.SYSTEM_ALERT_WINDOW
            )
            .request(object : OnPermission {
                override fun hasPermission(granted: MutableList<String>?, all: Boolean) {
                }

                override fun noPermission(denied: MutableList<String>?, never: Boolean) {
                }
            })
    }
}