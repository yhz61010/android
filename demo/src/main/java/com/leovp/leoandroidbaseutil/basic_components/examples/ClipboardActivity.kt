package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.kotlin.ITAG
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.system.ClipboardUtil
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityClipboardBinding

class ClipboardActivity : BaseDemonstrationActivity() {
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

    fun onClearClipboardClick(view: View) {
        ClipboardUtil.clear(this)
        ClipboardUtil.getClipboardText(this) {
            binding.edTxt.setText(it)
            LogContext.log.i(ITAG, "ClipboardText=$it")
        }
    }

    fun onSetTextToClipboardClick(view: View) {
        ClipboardUtil.setTextToClipboard(this, "Welcome Leo")
        ClipboardUtil.getClipboardText(this) {
            binding.edTxt.setText(it)
            LogContext.log.i(ITAG, "ClipboardText=$it")
        }
    }
}