package com.leovp.leoandroidbaseutil.basic_components

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.annotation.RequiresApi
import com.leovp.androidbase.exts.android.toast
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityAppSettingsBinding
import com.leovp.lib_common_android.exts.packageUri

class AppSettingsActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityAppSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater).apply { setContentView(root) }
    }

    fun onOpenStorageClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            simpleActivityLauncher.launch(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, this.packageUri))
        }
    }

    fun onOpenAppDetailClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            simpleActivityLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, this.packageUri)) { result ->
                toast("onOpenAppDetailClick result=${result.resultCode}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun onOverlayPermissionClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            simpleActivityLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, this.packageUri)) { result ->
                toast("onOverlayPermissionClick result=${result.resultCode}")
            }
        }
    }

    fun onUnitySettingClick(@Suppress("UNUSED_PARAMETER") view: View) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                simpleActivityLauncher.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, this.packageUri))
            }
        }
    }
}