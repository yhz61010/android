package com.leovp.demo.basic_components.examples.media_player.ui

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceView
import com.leovp.log_sdk.LogContext
import kotlin.math.max
import kotlin.math.min

/**
 * Author: Michael Leo
 * Date: 20-3-24 下午16:00
 */
class CustomSurfaceView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyle: Int = 0) :
    SurfaceView(context, attrs, defStyle) {

    private var aspectRatio = 0f

    /**
     * **Attention**: The parameter values are the camera orientation. NOT the device orientation.
     */
    fun setDimension(width: Int, height: Int) {
        require(!(width < 0 || height < 0)) { "Size cannot be negative." }
        val realWidth: Int
        val realHeight: Int
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            realWidth = width
            realHeight = height
            aspectRatio = width.toFloat() / height.toFloat()
        } else {
            realWidth = min(width, height)
            realHeight = max(width, height)
            aspectRatio = realHeight.toFloat() / realWidth.toFloat()
        }
        LogContext.log.d(TAG, "setDimension width=$width height=$height ratio=$aspectRatio realWidth=$realWidth realHeight=$realHeight ")

        holder.setFixedSize(realWidth, realHeight)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var finalWidth = widthMeasureSpec
        var finalHeight = heightMeasureSpec
        LogContext.log.d(
            TAG, "onMeasure target=" + aspectRatio +
                    " width=[" + MeasureSpec.toString(widthMeasureSpec) +
                    "] height=[" + MeasureSpec.toString(heightMeasureSpec) + "]"
        )

        // Target aspect ratio will be < 0 if it hasn't been set yet.  In that case,
        // we just use whatever we've been handed.

        // Target aspect ratio will be < 0 if it hasn't been set yet.  In that case,
        // we just use whatever we've been handed.
        if (aspectRatio > 0) {
            var initialWidth = MeasureSpec.getSize(widthMeasureSpec)
            var initialHeight = MeasureSpec.getSize(heightMeasureSpec)

            // factor the padding out
            val horizPadding = paddingLeft + paddingRight
            val vertPadding = paddingTop + paddingBottom
            initialWidth -= horizPadding
            initialHeight -= vertPadding
            val viewAspectRatio = initialWidth.toDouble() / initialHeight
            val aspectDiff: Double = aspectRatio / viewAspectRatio - 1
            if (Math.abs(aspectDiff) < 0.01) {
                // We're very close already.  We don't want to risk switching from e.g. non-scaled
                // 1280x720 to scaled 1280x719 because of some floating-point round-off error,
                // so if we're really close just leave it alone.
                LogContext.log.d(
                    TAG, "aspect ratio is good (target=" + aspectRatio +
                            ", view=" + initialWidth + "x" + initialHeight + ")"
                )
            } else {
                if (aspectDiff > 0) {
                    // limited by narrow width; restrict height
                    initialHeight = (initialWidth / aspectRatio).toInt()
                } else {
                    // limited by short height; restrict width
                    initialWidth = (initialHeight * aspectRatio).toInt()
                }
                LogContext.log.d(
                    TAG, "new size=" + initialWidth + "x" + initialHeight + " + padding " +
                            horizPadding + "x" + vertPadding
                )
                initialWidth += horizPadding
                initialHeight += vertPadding
                finalWidth = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY)
                finalHeight = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY)
            }
        }

        //LogContext.log.d(TAG, "set width=[" + MeasureSpec.toString(widthMeasureSpec) +
        //        "] height=[" + View.MeasureSpec.toString(heightMeasureSpec) + "]");

        //LogContext.log.d(TAG, "set width=[" + MeasureSpec.toString(widthMeasureSpec) +
        //        "] height=[" + View.MeasureSpec.toString(heightMeasureSpec) + "]");
        super.onMeasure(finalWidth, finalHeight)
    }

    companion object {
        private const val TAG = "CameraSurfaceView"
    }
}