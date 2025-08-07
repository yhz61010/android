package com.leovp.demo.basiccomponents

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import com.leovp.android.exts.packageUri
import com.leovp.android.exts.toast
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityAppSettingsBinding
import com.leovp.log.base.ITAG

class AppSettingsActivity : BaseDemonstrationActivity<ActivityAppSettingsBinding>(R.layout.activity_app_settings) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAppSettingsBinding =
        ActivityAppSettingsBinding.inflate(layoutInflater)

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
