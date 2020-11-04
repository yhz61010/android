package com.leovp.androidbase.utils

import android.app.Activity
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IntRange
import com.leovp.androidbase.R
import com.leovp.androidbase.exts.getToday
import java.util.*
import kotlin.math.sqrt

/**
 * Author: Michael Leo
 * Date: 20-11-4 上午11:23
 */
object Watermark {
    internal val defaultConfig = WatermarkConfig()

    fun defaultConfig(init: WatermarkConfig.() -> Unit) {
        defaultConfig.init()
    }

    fun with(activity: Activity): WatermarkCreator {
        val layout = FrameLayout(activity)
        layout.setTag(R.id.TAG_WATERMARK_LAYOUT, true)
        layout.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView.setTag(R.id.TAG_WATERMARK_IN_USE, true)
        rootView.addView(layout)
        return WatermarkCreator(layout)
    }

    fun remove(activity: Activity) {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val useWatermark = rootView.getTag(R.id.TAG_WATERMARK_IN_USE) as? Boolean == true
        if (useWatermark) {
            for (i in rootView.childCount - 1 downTo 0) {
                if (rootView.getChildAt(i).getTag(R.id.TAG_WATERMARK_LAYOUT) as? Boolean == true) {
                    rootView.removeViewAt(i)
                    break
                }
            }
        }
    }

    data class WatermarkConfig(
        /**
         * Watermark text
         */
        var text: String = Date().getToday("yyyy-MM-dd HH:mm:ss"),

        /**
         * Watermark text color.
         * Example: 0xAEAEAEAE
         */
        var textColor: Int = Color.parseColor("#AEAEAEAE"),

        /**
         * Watermark text size in sp
         */
        var textSize: Float = 16F,

        /**
         * Watermark text rotation
         */
        var rotation: Float = -25f
    )
}

class WatermarkCreator internal constructor(private val layout: FrameLayout) {
    private var default = Watermark.defaultConfig.copy()

    fun init(init: Watermark.WatermarkConfig.() -> Unit): WatermarkCreator {
        default.init()
        return this
    }

    fun show(text: String? = null) {
        val drawable = WatermarkDrawable()
        drawable.text = text ?: default.text
        drawable.textColor = default.textColor
        drawable.textSize = default.textSize
        drawable.rotation = default.rotation
        layout.background = drawable
    }

    internal class WatermarkDrawable : Drawable() {
        private val paint: Paint = Paint()
        var text: String = ""
        var textColor = 0

        /**
         * Text font size in sp
         */
        var textSize: Float = 0F
        var rotation: Float = 0F

        override fun draw(canvas: Canvas) {
            val width = bounds.right
            val height = bounds.bottom
            val diagonal = sqrt(width * width + height * height.toDouble()).toInt()
            paint.color = textColor
            paint.textSize = AppUtil.sp2px(textSize).toFloat()
            paint.isAntiAlias = true
            val textWidth = paint.measureText(text)
            canvas.drawColor(Color.TRANSPARENT)
            canvas.rotate(rotation)
            var index = 0
            var fromX: Float
            var positionY = diagonal / 10
            while (positionY <= diagonal) {
                fromX = -width + index++ % 2 * textWidth
                var positionX = fromX
                while (positionX < width) {
                    canvas.drawText(text, positionX, positionY.toFloat(), paint)
                    positionX += textWidth * 2
                }
                positionY += diagonal / 10
            }
            canvas.save()
            canvas.restore()
        }

        override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}