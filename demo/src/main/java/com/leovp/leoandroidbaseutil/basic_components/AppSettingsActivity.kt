package com.leovp.leoandroidbaseutil.basic_components

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import com.leovp.androidbase.exts.android.startAppDetailSetting
import com.leovp.androidbase.exts.android.startAppStorageSettings
import com.leovp.androidbase.exts.android.startManageDrawOverlaysPermission
import com.leovp.androidbase.exts.android.startUnitySettingPage
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityAppSettingsBinding

class AppSettingsActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityAppSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater).apply { setContentView(root) }
    }

    fun onOpenStorageClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching { startAppStorageSettings() }
    }

    fun onOpenAppDetailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching { startAppDetailSetting() }
    }

    fun onOverlayPermissionClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching { startManageDrawOverlaysPermission() }
    }

    fun onUnitySettingClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startUnitySettingPage(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            }
        }
    }
}