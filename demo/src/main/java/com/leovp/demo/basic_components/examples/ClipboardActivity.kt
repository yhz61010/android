package com.leovp.demo.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.utils.system.ClipboardUtil
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityClipboardBinding
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class ClipboardActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    private lateinit var binding: ActivityClipboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClipboardBinding.inflate(layoutInflater).apply { setContentView(root) }
    }

    override fun onResume() {
        super.onResume()
        ClipboardUtil.getClipboardText(this) {
            binding.edTxt.setText(it)
            LogContext.log.i(ITAG, "ClipboardText=$it")
        }
    }

    fun onClearClipboardClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ClipboardUtil.clear(this)
        ClipboardUtil.getClipboardText(this) {
            binding.edTxt.setText(it)
            LogContext.log.i(ITAG, "ClipboardText=$it")
        }
    }

    fun onSetTextToClipboardClick(@Suppress("UNUSED_PARAMETER") view: View) {
        ClipboardUtil.setTextToClipboard(this, "Welcome Leo")
        ClipboardUtil.getClipboardText(this) {
            binding.edTxt.setText(it)
            LogContext.log.i(ITAG, "ClipboardText=$it")
        }
    }
}