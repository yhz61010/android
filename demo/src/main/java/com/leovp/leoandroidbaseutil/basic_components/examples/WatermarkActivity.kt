package com.leovp.leoandroidbaseutil.basic_components.examples

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.utils.Watermark
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

class WatermarkActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watermark)

        // You can config global setting if you want
        Watermark.defaultConfig {
            textColor = Color.parseColor("#60FF0000")
            rotation = -30F
        }

        Watermark.with(this).show()
    }

    fun onRemoveWatermark(@Suppress("UNUSED_PARAMETER") view: View) {
        Watermark.remove(this)
    }

    fun onDefaultWatermarkClick(@Suppress("UNUSED_PARAMETER") view: View) {
        Watermark.remove(this)

        // Show default watermark with timestamp
        Watermark.with(this).show()
    }

    fun onDemo1WatermarkClick(@Suppress("UNUSED_PARAMETER") view: View) {
        Watermark.remove(this)

        // Show watermark with specified text
        Watermark.with(this).show("Leo Hello World")
    }

    fun onDemo2WatermarkClick(@Suppress("UNUSED_PARAMETER") view: View) {
        Watermark.remove(this)

        // Custom watermark before using it
        Watermark.with(this).init {
            text = "Custom your text here"
            textSize = 20F
        }.show()
    }
}