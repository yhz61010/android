package com.leovp.demo

import android.content.pm.ConfigurationInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.network.ConnectionLiveData
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityMainBinding
import com.leovp.bytes.toHexString
import com.leovp.android.exts.*
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class MainActivity : BaseDemonstrationActivity<ActivityMainBinding>({
    trafficConfig.run {
        allowToOutputDefaultWifiTrafficInfo = true
        frequencyInSecond = 3
    }
}) {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    private val connectionLiveData by lazy { ConnectionLiveData(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration =
            AppBarConfiguration(setOf(R.id.navigation_common, R.id.navigation_jetpack))
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        XXPermissions.with(this)
            .permission(
                Permission.MANAGE_EXTERNAL_STORAGE,
                Permission.CAMERA,
                Permission.RECORD_AUDIO,
                Permission.ACCESS_FINE_LOCATION,
                Permission.ACCESS_COARSE_LOCATION,
                Permission.SYSTEM_ALERT_WINDOW,
                Permission.BLUETOOTH_ADVERTISE,
                Permission.BLUETOOTH_CONNECT, Permission.BLUETOOTH_SCAN
            )
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>?, all: Boolean) {
                }

                override fun onDenied(denied: MutableList<String>?, never: Boolean) {
                }
            })

        LogContext.log.i(
            "real=${screenRealResolution.toJsonString()} available=${screenAvailableResolution.toJsonString()} " +
                "status_bar=$statusBarHeight navigation_bar=$navigationBarHeight"
        )

        LogContext.log.i(ITAG, "===================================")
        val info = getPackageInfo()
        val activities = info.activities
        if (activities?.isNotEmpty() == true) {
            for (i in activities.indices) {
                LogContext.log.i(ITAG, "activities:" + i + "=" + activities[i])
            }
        }
        val providers = info.providers
        if (providers?.isNotEmpty() == true) {
            for (i in providers.indices) {
                LogContext.log.i(ITAG, "providers:" + i + "=" + providers[i])
            }
        }
        val permissions = info.permissions
        if (permissions?.isNotEmpty() == true) {
            for (i in permissions.indices) {
                LogContext.log.i(ITAG, "permissions:" + i + "=" + permissions[i])
            }
        }

        val reqFeatures = info.reqFeatures
        if (reqFeatures?.isNotEmpty() == true) {
            for (i in reqFeatures.indices) {
                LogContext.log.i(ITAG, "reqFeatures:" + i + "=" + reqFeatures[i])
            }
        }
        val configs: Array<ConfigurationInfo>? = info.configPreferences
        if (configs?.isNotEmpty() == true) {
            for (i in configs.indices) {
                LogContext.log.i(ITAG, "configs:" + i + "=" + configs[i])
            }
        }

        val receivers = info.receivers
        if (receivers?.isNotEmpty() == true) {
            for (i in receivers.indices) {
                LogContext.log.i(ITAG, "receivers:" + i + "=" + receivers[i])
            }
        }
        val instrumentations = info.instrumentation
        if (instrumentations?.isNotEmpty() == true) {
            for (i in instrumentations.indices) {
                LogContext.log.i(ITAG, "instrumentations:" + i + "=" + instrumentations[i])
            }
        }
        val requestedPermissions = info.requestedPermissions
        if (requestedPermissions?.isNotEmpty() == true) {
            for (i in requestedPermissions.indices) {
                LogContext.log.i(ITAG, "requestedPermissions:" + i + "=" + requestedPermissions[i])
            }
        }
        val services: Array<ServiceInfo>? = info.services
        if (services?.isNotEmpty() == true) {
            for (i in services.indices) {
                LogContext.log.i(ITAG, "services:" + i + "=" + services[i])
            }
        }
        val signatures: List<ByteArray> = getApplicationSignatures()
        if (signatures.isNotEmpty()) {
            for (i in signatures.indices) {
                LogContext.log.i(
                    ITAG,
                    "signatures:" + i + "=" + signatures[i].toHexString(true, "")
                )
            }
        }

        val gids = info.gids
        if (gids?.isNotEmpty() == true) {
            for (i in gids.indices) {
                LogContext.log.i(ITAG, "gids:" + i + "=" + gids[i])
            }
        }
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        LogContext.log.i(ITAG, "versionCode: $versionCode")
        val versionName = info.versionName
        LogContext.log.i(ITAG, "versionName: $versionName")
        LogContext.log.i(ITAG, "===================================")

        connectionLiveData.observe(this) { isConnected ->
            LogContext.log.w(ITAG, "online=$isConnected")
            toast("online=$isConnected")
        }
    }

    override fun onResume() {
        super.onResume()
        startTrafficNetwork("leovp.com")
    }

    override fun onPause() {
        stopTrafficMonitor()
        super.onPause()
    }

    override fun onDestroy() {
        connectionLiveData.removeObservers(this)
        super.onDestroy()
    }
}
