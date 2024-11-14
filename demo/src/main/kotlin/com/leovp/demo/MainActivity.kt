package com.leovp.demo

import android.Manifest
import android.app.Application
import android.content.pm.ConfigurationInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.android.exts.getAndroidId
import com.leovp.android.exts.getApplicationSignatures
import com.leovp.android.exts.getPackageInfo
import com.leovp.android.exts.getUniqueID
import com.leovp.android.exts.getUniqueIdByMediaDrm
import com.leovp.android.exts.navigationBarHeight
import com.leovp.android.exts.screenAvailableResolution
import com.leovp.android.exts.screenRealResolution
import com.leovp.android.exts.statusBarHeight
import com.leovp.android.exts.toast
import com.leovp.android.exts.xdpi
import com.leovp.android.exts.ydpi
import com.leovp.android.utils.ApplicationManager
import com.leovp.android.utils.NetworkUtil
import com.leovp.androidbase.exts.android.getMetaData
import com.leovp.androidbase.utils.media.CodecUtil.printMediaCodecsList
import com.leovp.androidbase.utils.network.ConnectionLiveData
import com.leovp.bytes.toHexString
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityMainBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import java.net.Proxy
import kotlin.concurrent.thread
import kotlin.math.pow

class MainActivity : BaseDemonstrationActivity<ActivityMainBinding>(init = {
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
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment

        // val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val navController = navHostFragment.navController

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_common, R.id.navigation_jetpack))
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
                Permission.BLUETOOTH_CONNECT,
                Permission.BLUETOOTH_SCAN
            )
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>, all: Boolean) {
                }

                override fun onDenied(denied: MutableList<String>, never: Boolean) {
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
                LogContext.log.i(ITAG, "signatures:" + i + "=" + signatures[i].toHexString(true, ""))
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

        connectionLiveData.observe(this) { (online, changed, type) ->
            LogContext.log.w(ITAG, "online=$online $type changed=$changed")
            toast("online=$online $type changed=$changed")
        }

        thread {
            val proxyInfo = NetworkUtil.ProxyInfo(Proxy.Type.SOCKS, "10.10.10.142", 8889)
            val reachable = NetworkUtil.isHostReachable("172.217.175.238", 443, 3000, proxyInfo)
            LogContext.log.e(ITAG, "Reachable=$reachable")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.CHANGE_NETWORK_STATE, Manifest.permission.ACCESS_NETWORK_STATE])
    override fun onResume() {
        super.onResume()
        startTrafficNetwork("leovp.com")

        val actMetaData: String? = getMetaData("activity_meta_data")
        LogContext.log.w(ITAG, "actMetaData=$actMetaData")
        val actMetaDataInt: Int? = getMetaData("activity_meta_data_int")
        LogContext.log.w(ITAG, "actMetaDataInt=$actMetaDataInt")

        val appMetaData1: String? = getMetaData("app_meta_data")
        LogContext.log.w(ITAG, "appMetaData1=$appMetaData1")

        val appMetaData2: String? = application.getMetaData("app_meta_data")
        LogContext.log.w(ITAG, "appMetaData2=$appMetaData2")

        val leoCustomKey: String = application.getMetaData("com.leovp.custom.key") ?: ""
        LogContext.log.w(ITAG, "com.leovp.custom.key=$leoCustomKey")

        val actApplication = application
        val actApplicationCtx = applicationContext as Application
        val amApplication = ApplicationManager.application
        LogContext.log.e(ITAG, "actApplication=$actApplication")
        LogContext.log.e(ITAG, "actApplicationCtx=$actApplicationCtx")
        LogContext.log.e(ITAG, "amApplication=$amApplication")

        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LogContext.log.i(ITAG, "Android ID=${getAndroidId()}")
        // }

        LogContext.log.i(ITAG, "uid by drm=${getUniqueIdByMediaDrm()?.toHexString(true, "")}")
        LogContext.log.i(ITAG, "uid=${getUniqueID()}")

        // LogContext.log.i(ITAG, "Security Providers=${Security.getProviders().toJsonString()}", fullOutput = true)

        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
        LogContext.log.i(ITAG, "refresh rate=${display?.refreshRate}")

        LogContext.log.i(ITAG, "xdpi=$xdpi")
        LogContext.log.i(ITAG, "ydpi=$ydpi")
        LogContext.log.i(ITAG, "width=${screenRealResolution.width}")
        LogContext.log.i(ITAG, "height=${screenRealResolution.height}")
        val xInch = screenRealResolution.width * 1.0 / xdpi
        val yInch = screenRealResolution.height * 1.0 / ydpi
        LogContext.log.i(ITAG, "inch=${kotlin.math.sqrt(xInch.pow(2) + yInch.pow(2))}")

        LogContext.log.i(ITAG, "===================================")
        printMediaCodecsList()
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
