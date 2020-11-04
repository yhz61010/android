package com.leovp.leoandroidbaseutil.basic_components.examples

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.utils.Watermark
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_watermark.*


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

        sbRotation.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val usedRotation = progress - 180
                LogContext.log.i(ITAG, "rotation=$usedRotation")

                Watermark.remove(this@WatermarkActivity)

                // Custom watermark before using it
                Watermark.with(this@WatermarkActivity).init {
                    text = "Michael Leo"
                    textColor = Color.parseColor("#3000FFFF")
                    textSize = 14F
                    rotation = usedRotation.toFloat()
                }.show()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
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
            textColor = Color.parseColor("#40FF00FF")
            textSize = 20f
            wordSpacerMultiple = 1.1f
        }.show()
    }
}