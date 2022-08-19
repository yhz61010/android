package com.leovp.demo.basic_components

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.annotation.RequiresApi
import com.leovp.lib_common_android.exts.toast
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityAppSettingsBinding
import com.leovp.lib_common_android.exts.packageUri
import com.leovp.log_sdk.base.ITAG

class AppSettingsActivity : BaseDemonstrationActivity<ActivityAppSettingsBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAppSettingsBinding {
        return ActivityAppSettingsBinding.inflate(layoutInflater)
    }

    fun onOpenStorageClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            simpleActivityLauncher.launch(
                Intent(
                    Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                    this.packageUri
                )
            )
        }
    }

    fun onOpenAppDetailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            simpleActivityLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    this.packageUri
                )
            ) { result ->
                toast("onOpenAppDetailClick result=${result.resultCode}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun onOverlayPermissionClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            simpleActivityLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    this.packageUri
                )
            ) { result ->
                toast("onOverlayPermissionClick result=${result.resultCode}")
            }
        }
    }

    fun onUnitySettingClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                simpleActivityLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        this.packageUri
                    )
                ) { result ->
                    toast("Result in AppSettingsActivity: ${result.resultCode}")
                }
            }
        }
    }
}
