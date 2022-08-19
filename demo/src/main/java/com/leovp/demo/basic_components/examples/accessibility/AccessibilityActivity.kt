package com.leovp.demo.basic_components.examples.accessibility

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ArrayAdapter
import com.leovp.lib_common_android.exts.toast
import com.leovp.androidbase.utils.system.AccessibilityUtil
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basic_components.examples.sharescreen.client.ScreenShareClientActivity
import com.leovp.demo.databinding.ActivityAccessibilityBinding
import com.leovp.lib_common_android.exts.id
import com.leovp.log_sdk.base.ITAG
import org.greenrobot.eventbus.EventBus

class AccessibilityActivity : BaseDemonstrationActivity<ActivityAccessibilityBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAccessibilityBinding {
        return ActivityAccessibilityBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val items = mutableListOf<String>()
        for (i in 0 until 50) {
            items.add("Item$i")
        }
        binding.list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)

        if (!AccessibilityUtil.isAccessibilityEnabled()) {
            AccessibilityUtil.jumpToSettingPage(this)
        }
    }

    fun onStartAccessibilityClick(@Suppress("UNUSED_PARAMETER") view: View) {
        AccessibilityUtil.setTextById(SystemClock.elapsedRealtime().toString(), "$id:id/etDemo1")
        AccessibilityUtil.clickById("$id:id/btnDemo1")
        AccessibilityUtil.clickById("$id:id/cb1")
        AccessibilityUtil.clickById("$id:id/sw1")
        AccessibilityUtil.scrollForwardById("$id:id/list")
        Handler(Looper.getMainLooper()).postDelayed({
            val touchBean = ScreenShareClientActivity.TouchBean(ScreenShareClientActivity.TouchType.DRAG, 500F, 1400F, 500F, 800F, 500L)
            EventBus.getDefault().post(touchBean)
        }, 1000)
        Handler(Looper.getMainLooper()).postDelayed({
            AccessibilityUtil.openNotification()
            AccessibilityUtil.clickBackKey()
        }, 3000)
        Handler(Looper.getMainLooper()).postDelayed({
            AccessibilityUtil.clickHomeKey()
            AccessibilityUtil.clickRecentKey()
        }, 5000)
    }

    fun onButtonClick(@Suppress("UNUSED_PARAMETER") view: View) {
        toast("You click button")
    }
}
