package com.ho1ho.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.system.ClipboardUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_clipboard.*

class ClipboardActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard)
    }

    override fun onResume() {
        super.onResume()
        ClipboardUtil.getClipboardText(this) {
            edTxt.setText(it)
            LLog.i(ITAG, "ClipboardText=$it")
        }
    }

    fun onClearClipboardClick(view: View) {
        ClipboardUtil.clear(this)
        ClipboardUtil.getClipboardText(this) {
            edTxt.setText(it)
            LLog.i(ITAG, "ClipboardText=$it")
        }
    }

    fun onSetTextToClipboardClick(view: View) {
        ClipboardUtil.setTextToClipboard(this, "Welcome Leo")
        ClipboardUtil.getClipboardText(this) {
            edTxt.setText(it)
            LLog.i(ITAG, "ClipboardText=$it")
        }
    }
}