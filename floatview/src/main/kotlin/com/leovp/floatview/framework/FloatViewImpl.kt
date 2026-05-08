package com.leovp.floatview.framework

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.children
import com.leovp.floatview.entities.DefaultConfig
import com.leovp.floatview.entities.DockEdge
import com.leovp.floatview.entities.StickyEdge
import com.leovp.floatview.utils.canDrawOverlays
import com.leovp.floatview.utils.getScreenSize
import com.leovp.floatview.utils.isGoogle
import com.leovp.floatview.utils.screenAvailableResolution
import com.leovp.floatview.utils.screenRealResolution
import com.leovp.floatview.utils.screenSurfaceRotation
import com.leovp.floatview.utils.statusBarHeight
import kotlin.math.abs
import kotlin.math.max

/**
 * Author: Michael Leo
 * Date: 2021/8/30 10:56
 */
internal class FloatViewImpl(private val context: Context, internal var config: DefaultConfig) {
    companion object {
        private const val TAG = "FVI"
        private const val ANIMATION_DURATION_START = 350L
        private const val ANIMATION_DURATION_END = 500L
    }

    private val windowManager = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var firstX: Int = 0
    private var firstY: Int = 0

    private var touchConsumedByMove = false

    private var isClickGesture = true

    private val mainHandler = Handler(Looper.getMainLooper())

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val rotation = displayManager.getDisplay(displayId)?.rotation ?: return
            if (rotation != lastScrOri) updateScreenOrientation(rotation)
        }
    }

    private val orientationEventListener = object : OrientationEventListener(context) {
        private var lastDeviceRotation: Int = -1

        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45..134 -> Surface.ROTATION_270
                in 135..224 -> Surface.ROTATION_180
                in 225..314 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            if (rotation != lastDeviceRotation) {
                lastDeviceRotation = rotation
                if (rotation != lastScrOri) {
                    mainHandler.post { updateScreenOrientation(rotation) }
                }
            }
        }
    }

    // Cached screen dimensions — refreshed only in refreshScreenCache()
    private var cachedRealResolution: Size = context.screenRealResolution
    private var cachedAvailableResolution: Size = context.screenAvailableResolution
    private var cachedStatusBarHeight: Int = context.statusBarHeight

    private fun refreshScreenCache() {
        cachedRealResolution = context.screenRealResolution
        cachedAvailableResolution = context.screenAvailableResolution
        cachedStatusBarHeight = context.statusBarHeight
    }

    private fun getScreenOrientationSize(orientation: Int): Size {
        val realRes = cachedRealResolution
        val availRes = cachedAvailableResolution
        val heightNecessaryOffset = abs(
            max(realRes.width, realRes.height) - max(availRes.width, availRes.height)
        )
        val finalHeightNecessaryOffset = when {
            isGoogle -> heightNecessaryOffset - cachedStatusBarHeight
            else -> heightNecessaryOffset
        }
        val curScreenOrientationSize = context.getScreenSize(orientation, realRes)
        return Size(curScreenOrientationSize.width, curScreenOrientationSize.height - finalHeightNecessaryOffset)
    }

    @Volatile private var lastScrOri = context.screenSurfaceRotation.takeIf {
        config.screenOrientation == -1
    } ?: config.screenOrientation
    private var screenOrientSz: Size = getScreenOrientationSize(lastScrOri)

    // Previous layoutParams position for dirty-flag check
    private var prevLayoutX: Int = Int.MIN_VALUE
    private var prevLayoutY: Int = Int.MIN_VALUE

    @SuppressLint("ClickableViewAccessibility")
    private val onTouchListener = View.OnTouchListener { view, event ->
        val consumeIsAlwaysFalse = !config.enableDrag || config.fullScreenFloatView

        val totalDeltaX = lastX - firstX
        val totalDeltaY = lastY - firstY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                firstX = lastX
                firstY = lastY
                isClickGesture = true
                touchConsumedByMove = config.touchEventListener?.touchDown(view, lastX, lastY) ?: false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                if (!consumeIsAlwaysFalse && config.dockEdge != DockEdge.NONE) {
                    startDockAnim(layoutParams.x, layoutParams.y, config.dockEdge)
                }
                touchConsumedByMove =
                    config.touchEventListener?.touchUp(view, lastX, lastY, isClickGesture) ?: !isClickGesture
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX.toInt() - lastX
                val deltaY = event.rawY.toInt() - lastY
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                if (abs(totalDeltaX) >= config.touchToleranceInPx || abs(totalDeltaY) >= config.touchToleranceInPx) {
                    isClickGesture = false
                    view.isPressed = false
                    if (!consumeIsAlwaysFalse) {
                        if (event.pointerCount == 1) {
                            updateLayoutForSticky(deltaX, deltaY)
                            touchConsumedByMove = true
                            // Only call updateViewLayout when position actually changed
                            if (layoutParams.x != prevLayoutX || layoutParams.y != prevLayoutY) {
                                prevLayoutX = layoutParams.x
                                prevLayoutY = layoutParams.y
                                windowManager.updateViewLayout(config.customView, layoutParams)
                            }
                        } else {
                            touchConsumedByMove = false
                        }
                    }
                } else {
                    isClickGesture = true
                    touchConsumedByMove = false
                }
                touchConsumedByMove =
                    config.touchEventListener?.touchMove(view, lastX, lastY, isClickGesture) ?: touchConsumedByMove
            }

            else -> Unit
        }
        if (consumeIsAlwaysFalse) touchConsumedByMove = false
        touchConsumedByMove
    }

    private fun updateLayoutForSticky(deltaX: Int, deltaY: Int) {
        when (config.stickyEdge) {
            StickyEdge.NONE -> {
                layoutParams.x += deltaX
                layoutParams.y += deltaY
                layoutParams.x = adjustPosX(layoutParams.x, config.edgeMargin)
                layoutParams.y = adjustPosY(layoutParams.y, config.edgeMargin)
            }

            StickyEdge.LEFT -> {
                layoutParams.x = getFloatViewLeftMinMargin()
                layoutParams.y += deltaY
                layoutParams.y = adjustPosY(layoutParams.y, config.edgeMargin)
            }

            StickyEdge.RIGHT -> {
                layoutParams.x = getFloatViewRightMaxMargin()
                layoutParams.y += deltaY
                layoutParams.y = adjustPosY(layoutParams.y, config.edgeMargin)
            }

            StickyEdge.TOP -> {
                layoutParams.x += deltaX
                layoutParams.x = adjustPosX(layoutParams.x, config.edgeMargin)
                layoutParams.y = getFloatViewTopMinMargin()
            }

            StickyEdge.BOTTOM -> {
                layoutParams.x += deltaX
                layoutParams.x = adjustPosX(layoutParams.x, config.edgeMargin)
                layoutParams.y = getFloatViewBottomMaxMargin()
            }
        }
    }

    private fun startDockAnim(left: Int, top: Int, dockEdge: DockEdge) {
        config.customView?.let { v ->
            val viewWidth = v.width
            val viewHeight = v.height
            val floatViewCenterX = left + viewWidth / 2
            val floatViewCenterY = top + viewHeight / 2
            var animateDirectionForDockFull = DockEdge.NONE
            when (dockEdge) {
                DockEdge.NONE -> null
                DockEdge.LEFT -> ValueAnimator.ofInt(left, getFloatViewLeftMinMargin())
                DockEdge.RIGHT -> ValueAnimator.ofInt(left, getFloatViewRightMaxMargin())
                DockEdge.TOP -> ValueAnimator.ofInt(top, getFloatViewTopMinMargin())
                DockEdge.BOTTOM -> ValueAnimator.ofInt(top, getFloatViewBottomMaxMargin())
                DockEdge.LEFT_RIGHT -> {
                    if (floatViewCenterX <= screenOrientSz.width / 2) {
                        ValueAnimator.ofInt(left, getFloatViewLeftMinMargin())
                    } else {
                        ValueAnimator.ofInt(left, getFloatViewRightMaxMargin())
                    }
                }

                DockEdge.TOP_BOTTOM -> {
                    if (floatViewCenterY <= screenOrientSz.height / 2) {
                        ValueAnimator.ofInt(top, getFloatViewTopMinMargin())
                    } else {
                        ValueAnimator.ofInt(top, getFloatViewBottomMaxMargin())
                    }
                }

                DockEdge.FULL -> {
                    val distToLeft = left - config.edgeMargin
                    val distToRight = screenOrientSz.width - (left + viewWidth) - config.edgeMargin
                    val distToTop = top - getTopHeightOffset() - config.edgeMargin
                    val distToBottom = screenOrientSz.height - (top + viewHeight) - config.edgeMargin

                    if (floatViewCenterX <= screenOrientSz.width / 2) { // On left screen
                        if (floatViewCenterY <= screenOrientSz.height / 2) { // Top left
                            if (distToLeft <= distToTop) {
                                animateDirectionForDockFull = DockEdge.LEFT
                                ValueAnimator.ofInt(left, getFloatViewLeftMinMargin())
                            } else {
                                animateDirectionForDockFull = DockEdge.TOP
                                ValueAnimator.ofInt(top, getFloatViewTopMinMargin())
                            }
                        } else { // Bottom left
                            if (distToLeft <= distToBottom) {
                                animateDirectionForDockFull = DockEdge.LEFT
                                ValueAnimator.ofInt(left, getFloatViewLeftMinMargin())
                            } else {
                                animateDirectionForDockFull = DockEdge.BOTTOM
                                ValueAnimator.ofInt(top, getFloatViewBottomMaxMargin())
                            }
                        }
                    } else { // On right screen
                        if (floatViewCenterY <= screenOrientSz.height / 2) { // Top right
                            if (distToTop <= distToRight) {
                                animateDirectionForDockFull = DockEdge.TOP
                                ValueAnimator.ofInt(top, getFloatViewTopMinMargin())
                            } else {
                                animateDirectionForDockFull = DockEdge.RIGHT
                                ValueAnimator.ofInt(left, getFloatViewRightMaxMargin())
                            }
                        } else { // Bottom right
                            if (distToBottom <= distToRight) {
                                animateDirectionForDockFull = DockEdge.BOTTOM
                                ValueAnimator.ofInt(top, getFloatViewBottomMaxMargin())
                            } else {
                                animateDirectionForDockFull = DockEdge.RIGHT
                                ValueAnimator.ofInt(left, getFloatViewRightMaxMargin())
                            }
                        }
                    }
                }
            }?.apply {
                duration = config.dockAnimDuration
                addUpdateListener {
                    val value = it.animatedValue as Int
                    when (dockEdge) {
                        DockEdge.NONE -> Unit
                        DockEdge.LEFT, DockEdge.RIGHT, DockEdge.LEFT_RIGHT -> layoutParams.x = value
                        DockEdge.TOP, DockEdge.BOTTOM, DockEdge.TOP_BOTTOM -> layoutParams.y = value
                        DockEdge.FULL -> {
                            when (animateDirectionForDockFull) {
                                DockEdge.LEFT, DockEdge.RIGHT -> layoutParams.x = value
                                DockEdge.TOP, DockEdge.BOTTOM -> layoutParams.y = value
                                else -> Unit
                            }
                        }
                    }
                    try {
                        windowManager.updateViewLayout(v, layoutParams)
                    } catch (_: Exception) {
                        cancel()
                    }
                }
                start()
            }
        }
    }

    fun updateFloatViewGravity(gravity: Int) {
        config.customView?.let { v ->
            config.gravity = gravity
            layoutParams.gravity = gravity
            windowManager.updateViewLayout(v, layoutParams)
        }
    }

    fun updateFloatViewPosition(x: Int?, y: Int?, accumulateY: Boolean = false) {
        config.customView?.let { v ->
            try {
                x?.let {
                    val finalX = adjustPosX(x, config.edgeMargin)
                    layoutParams.x = finalX
                    config.x = finalX
                }
                y?.let {
                    val finalY = adjustPosY(y, config.edgeMargin, accumulateY)
                    layoutParams.y = finalY
                    config.y = finalY
                }
                windowManager.updateViewLayout(v, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getFloatViewPosition(): Point {
        config.x = adjustPosX(layoutParams.x, config.edgeMargin)
        config.y = adjustPosY(layoutParams.y, config.edgeMargin)
        return Point(config.x, config.y)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        setWindowLayoutParams()

        // Ensure customView is measured and laid out before calculating position
        config.customView?.let { view ->
            if (view.width <= 0 || view.height <= 0) {
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }
        }

        val viewWidth = config.customView?.width ?: 0
        val viewHeight = config.customView?.height ?: 0

        val needsAdjustX = config.x < config.edgeMargin ||
            config.x <= 0 ||
            (config.x + viewWidth + config.edgeMargin) >= screenOrientSz.width
        val needsAdjustY = config.y < config.edgeMargin ||
            config.y <= 0 ||
            (config.y + viewHeight + config.edgeMargin) >= screenOrientSz.height

        val finalX = if (needsAdjustX) adjustPosX(config.x, config.edgeMargin) else config.x
        val finalY = if (needsAdjustY) adjustPosY(config.y, config.edgeMargin) else config.y

        layoutParams.x = finalX
        layoutParams.y = finalY
        config.x = finalX
        config.y = finalY

        // Set touch listener on root view and child views with click listeners
        config.customView?.let { v ->
            v.setOnTouchListener(onTouchListener)
            addTouchListenerToChildViews(v, onTouchListener)
        }
    }

    private fun adjustPosX(x: Int, edgeMargin: Int): Int {
        val viewWidth = config.customView?.width ?: return x
        if (x < edgeMargin || x <= 0) return edgeMargin
        return if ((x + viewWidth + edgeMargin) >= screenOrientSz.width) {
            screenOrientSz.width - viewWidth - edgeMargin
        } else {
            x
        }
    }

    /**
     * @param considerY When it is true, if the `y` is less equal then the minimum value,
     * the result will accumulate `y`.
     */
    private fun adjustPosY(y: Int, edgeMargin: Int, considerY: Boolean = false): Int {
        val viewHeight = config.customView?.height ?: return y
        val drawHeightOffset = getTopHeightOffset()
        if (y <= edgeMargin + drawHeightOffset) {
            return edgeMargin + drawHeightOffset + (y.takeIf { considerY } ?: 0)
        }
        return if ((y + viewHeight + edgeMargin) >= screenOrientSz.height) {
            screenOrientSz.height - viewHeight - edgeMargin
        } else {
            y
        }
    }

    private fun getFloatViewLeftMinMargin(): Int = config.edgeMargin
    private fun getFloatViewRightMaxMargin(): Int =
        screenOrientSz.width - (config.customView?.width ?: 0) - config.edgeMargin

    private fun getFloatViewTopMinMargin(): Int = getTopHeightOffset() + config.edgeMargin
    private fun getFloatViewBottomMaxMargin(): Int =
        screenOrientSz.height - (config.customView?.height ?: 0) - config.edgeMargin

    private fun getTopHeightOffset(): Int = if (config.immersiveMode) 0 else cachedStatusBarHeight

    private fun setWindowLayoutParams() {
        layoutParams = WindowManager.LayoutParams().apply {
            format = PixelFormat.TRANSLUCENT
            flags = if (config.touchable) {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (config.systemWindow && context.canDrawOverlays) {
                type = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else -> {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_TOAST or WindowManager.LayoutParams.TYPE_PHONE
                    }
                }
            }

            gravity = config.gravity
            if (config.fullScreenFloatView) {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
            } else {
                width = config.width ?: WindowManager.LayoutParams.WRAP_CONTENT
                height = config.height ?: WindowManager.LayoutParams.WRAP_CONTENT
            }
        }
    }

    private fun addTouchListenerToChildViews(view: View, touchListener: View.OnTouchListener) {
        if (view is ViewGroup) {
            if (view.hasOnClickListeners()) {
                view.setOnTouchListener(touchListener)
            }
            view.children.forEach { child -> addTouchListenerToChildViews(child, touchListener) }
        } else {
            if (view.hasOnClickListeners()) {
                view.setOnTouchListener(touchListener)
            }
        }
    }

    @MainThread
    fun updateAutoDock(dockEdge: DockEdge) {
        config.dockEdge = dockEdge
        startDockAnim(layoutParams.x, layoutParams.y, dockEdge)
    }

    @MainThread
    fun updateStickyEdge(stickyEdge: StickyEdge) {
        config.stickyEdge = stickyEdge
        updateLayoutForSticky(0, 0)
        try {
            windowManager.updateViewLayout(config.customView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @MainThread
    fun updateScreenOrientation(orientation: Int) {
        val oldScreenSize = screenOrientSz
        lastScrOri = orientation
        config.screenOrientation = orientation
        refreshScreenCache()
        screenOrientSz = getScreenOrientationSize(orientation)

        // Scale position proportionally to maintain relative position after orientation change
        if (oldScreenSize.width > 0 && oldScreenSize.width != screenOrientSz.width) {
            layoutParams.x = (layoutParams.x.toFloat() / oldScreenSize.width * screenOrientSz.width).toInt()
            layoutParams.x = adjustPosX(layoutParams.x, config.edgeMargin)
        }
        if (oldScreenSize.height > 0 && oldScreenSize.height != screenOrientSz.height) {
            layoutParams.y = (layoutParams.y.toFloat() / oldScreenSize.height * screenOrientSz.height).toInt()
            layoutParams.y = adjustPosY(layoutParams.y, config.edgeMargin)
        }

        if (config.dockEdge != DockEdge.NONE) {
            updateAutoDock(config.dockEdge)
        } else {
            updateStickyEdge(config.stickyEdge)
        }
    }

    @Suppress("unused")
    private fun getResourceEntryName(@IdRes id: Int): String =
        runCatching { context.resources.getResourceEntryName(id) }.getOrDefault("")

    fun show() {
        runCatching {
            if (config.systemWindow && !context.canDrawOverlays) {
                Log.w(
                    TAG,
                    "FloatView tag=${config.tag} is setting as SystemWindow. " +
                        "However, app doesn't have [DrawOverlays] permission."
                )
            }

            remove(true)
            registerListeners()
            init()
            windowManager.addView(config.customView!!, layoutParams)
            visible(true)
            updateAutoDock(config.dockEdge)
            updateStickyEdge(config.stickyEdge)
        }.onFailure {
            unregisterListeners()
            it.printStackTrace()
        }
    }

    private fun registerListeners() {
        displayManager.registerDisplayListener(displayListener, mainHandler)
        if (config.followDeviceOrientation && orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
    }

    private fun unregisterListeners() {
        displayManager.unregisterDisplayListener(displayListener)
        orientationEventListener.disable()
    }

    fun remove(immediately: Boolean = false) {
        unregisterListeners()
        if (immediately) {
            config.customView?.let { v ->
                hideCustomView(v)
                if (v.windowToken != null) windowManager.removeViewImmediate(v)
            }
        } else {
            visible(false) {
                config.customView?.let { v ->
                    if (v.windowToken != null) windowManager.removeViewImmediate(v)
                }
            }
        }
    }

    fun visible(show: Boolean, hideCallback: (() -> Unit)? = null) {
        config.customView?.let { v ->
            if (show) {
                if (!config.enableAlphaAnimation) {
                    showCustomView(v)
                    return
                }
                ObjectAnimator.ofFloat(v, "alpha", 0.0f, 1.0f)
                    .apply {
                        duration = ANIMATION_DURATION_START
                        removeAllListeners()
                        doOnStart { showCustomView(v) }
                        setAutoCancel(true)
                        start()
                    }
            } else {
                if (!config.enableAlphaAnimation) {
                    hideCustomView(v)
                    hideCallback?.invoke()
                    return
                }
                ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.0f)
                    .apply {
                        duration = ANIMATION_DURATION_END
                        removeAllListeners()
                        setAutoCancel(true)
                        doOnEnd {
                            hideCustomView(v)
                            hideCallback?.invoke()
                        }
                        doOnCancel {
                            hideCustomView(v)
                            hideCallback?.invoke()
                        }
                        start()
                    }
            }
        }
    }

    private fun showCustomView(view: View) {
        view.visibility = View.VISIBLE
        config.isDisplaying = true
    }

    private fun hideCustomView(view: View) {
        view.visibility = View.GONE
        config.isDisplaying = false
    }
}
