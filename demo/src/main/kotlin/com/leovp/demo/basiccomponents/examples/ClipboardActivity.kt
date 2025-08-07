package com.leovp.demo.basiccomponents.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.utils.system.ClipboardUtil
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityClipboardBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class ClipboardActivity : BaseDemonstrationActivity<ActivityClipboardBinding>(R.layout.activity_clipboard) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityClipboardBinding =
        ActivityClipboardBinding.inflate(layoutInflater)

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
