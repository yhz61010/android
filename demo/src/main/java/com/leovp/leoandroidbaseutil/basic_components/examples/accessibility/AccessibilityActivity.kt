package com.leovp.leoandroidbaseutil.basic_components.examples.accessibility

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import com.leovp.androidbase.exts.android.id
import com.leovp.androidbase.utils.system.AccessibilityUtil
import com.leovp.androidbase.utils.ui.ToastUtil
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityAccessibilityBinding

class AccessibilityActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityAccessibilityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessibilityBinding.inflate(layoutInflater).apply { setContentView(root) }

        if (!AccessibilityUtil.isAccessibilityEnabled()) {
            AccessibilityUtil.jumpToSettingPage(this)
        }
    }

    fun onStartAccessibilityClick(@Suppress("UNUSED_PARAMETER") view: View) {
        AccessibilityUtil.setTextById(SystemClock.elapsedRealtime().toString(), "$id:id/etDemo1")
        AccessibilityUtil.clickById("$id:id/btnDemo1")
        AccessibilityUtil.clickById("$id:id/cb1")
        AccessibilityUtil.clickById("$id:id/sw1")
    }

    fun onButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ToastUtil.showToast("You click button")
    }
}