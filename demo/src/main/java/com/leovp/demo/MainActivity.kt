package com.leovp.demo

import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.lib_common_android.exts.getAvailableResolution
import com.leovp.lib_common_android.exts.getRealResolution
import com.leovp.lib_common_android.exts.navigationBarHeight
import com.leovp.lib_common_android.exts.statusBarHeight
import com.leovp.lib_json.toJsonString
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class MainActivity : BaseDemonstrationActivity() {

    override fun getTagName(): String = ITAG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration =
                AppBarConfiguration(setOf(R.id.navigation_common, R.id.navigation_jetpack))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        XXPermissions.with(this)
            .permission(Permission.MANAGE_EXTERNAL_STORAGE,
                Permission.CAMERA,
                Permission.RECORD_AUDIO,
                Permission.ACCESS_FINE_LOCATION,
                Permission.ACCESS_COARSE_LOCATION,
                Permission.SYSTEM_ALERT_WINDOW,
                Permission.BLUETOOTH_ADVERTISE,
                Permission.BLUETOOTH_CONNECT,
                Permission.BLUETOOTH_SCAN)
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>?, all: Boolean) {
                }

                override fun onDenied(denied: MutableList<String>?, never: Boolean) {
                }
            })

        LogContext.log.i("real=${getRealResolution().toJsonString()} available=${getAvailableResolution().toJsonString()} status_bar=$statusBarHeight navigation_bar=$navigationBarHeight")
    }
}