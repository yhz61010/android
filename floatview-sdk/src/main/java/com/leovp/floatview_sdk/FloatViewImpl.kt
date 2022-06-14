package com.leovp.floatview_sdk

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.*
import androidx.annotation.IdRes
import androidx.core.view.children
import com.leovp.floatview_sdk.base.AutoDock
import com.leovp.floatview_sdk.base.DefaultConfig
import com.leovp.floatview_sdk.base.StickyEdge
import com.leovp.floatview_sdk.util.getScreenAvailableHeight
import com.leovp.floatview_sdk.util.getScreenWidth
import com.leovp.floatview_sdk.util.statusBarHeight
import kotlin.math.abs

/**
 * Author: Michael Leo
 * Date: 2021/8/30 10:56
 */
internal class FloatViewImpl(private val context: Activity, internal var config: DefaultConfig) {
    private val windowManager: WindowManager = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var firstX: Int = 0
    private var firstY: Int = 0

    private var touchConsumedByMove = false

    private var isClickGesture = true

    /**
     * If [config#canDragOverStatusBar] is false, this values is the height of status bar.
     */
    private var drawHeightOffset = 0

    @SuppressLint("ClickableViewAccessibility")
    private val onTouchListener = View.OnTouchListener { view, event ->
        val consumeIsAlwaysFalse = !config.enableDrag || config.fullScreenFloatView

        val totalDeltaX = lastX - firstX
        val totalDeltaY = lastY - firstY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN                                                      -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                firstX = lastX
                firstY = lastY
                isClickGesture = true
//                if (GlobalConstants.DEBUG) LogContext.log.e("ACTION_DOWN isPressed=${view.isPressed} hasFocus=${view.hasFocus()} isActivated=${view.isActivated} $view")
                touchConsumedByMove = config.touchEventListener?.touchDown(view, lastX, lastY) ?: false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
//                view.performClick()
//                if (GlobalConstants.DEBUG) LogContext.log.e("ACTION_UP isPressed=${view.isPressed} hasFocus=${view.hasFocus()} isActivated=${view.isActivated} isClickGesture=$isClickGesture $view")
                if (!consumeIsAlwaysFalse && config.autoDock != AutoDock.NONE) {
                    startDockAnim(layoutParams.x, layoutParams.y, config.autoDock)
                }
                touchConsumedByMove =
                        config.touchEventListener?.touchUp(view, lastX, lastY, isClickGesture) ?: !isClickGesture
            }
            MotionEvent.ACTION_MOVE                                                      -> {
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
                            windowManager.updateViewLayout(config.customView, layoutParams)
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
            else                                                                         -> Unit
        }
        if (consumeIsAlwaysFalse) touchConsumedByMove = false
        touchConsumedByMove
    }

    private fun updateLayoutForSticky(deltaX: Int, deltaY: Int) {
        when (config.stickyEdge) {
            StickyEdge.NONE   -> {
                layoutParams.x += deltaX
                layoutParams.y += deltaY
                layoutParams.x = adjustPosX(layoutParams.x, config.edgeMargin)
                layoutParams.y = adjustPosY(layoutParams.y, config.edgeMargin)
            }
            StickyEdge.LEFT   -> {
                layoutParams.x = getFloatViewLeftMinMargin()
                layoutParams.y += deltaY
                layoutParams.y = adjustPosY(layoutParams.y, config.edgeMargin)
            }
            StickyEdge.RIGHT  -> {
                layoutParams.x = getFloatViewRightMaxMargin()
                layoutParams.y += deltaY
                layoutParams.y = adjustPosY(layoutParams.y, config.edgeMargin)
            }
            StickyEdge.TOP    -> {
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

    private fun startDockAnim(left: Int, top: Int, autoDock: AutoDock) {
        config.customView?.let { v ->
            val floatViewCenterX = left + config.customView!!.width / 2
            val floatViewCenterY = top + config.customView!!.height / 2
            var animateDirectionForDockFull = AutoDock.NONE
            when (autoDock) {
                AutoDock.NONE       -> null
                AutoDock.LEFT       -> ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                AutoDock.RIGHT      -> ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                AutoDock.TOP        -> ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                AutoDock.BOTTOM     -> ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                AutoDock.LEFT_RIGHT -> {
                    if (floatViewCenterX <= context.getScreenWidth() / 2) {
                        ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                    } else {
                        ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                    }
                }
                AutoDock.TOP_BOTTOM -> {
                    if (floatViewCenterY <= context.getScreenAvailableHeight() / 2) {
                        ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                    } else {
                        ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                    }
                }
                AutoDock.FULL       -> {
                    if (floatViewCenterX <= context.getScreenWidth() / 2) { // On left screen
                        if (floatViewCenterY <= context.getScreenAvailableHeight() / 2) { // On top screen // Top left
                            if (left <= top - drawHeightOffset) { // Animate to left
                                animateDirectionForDockFull = AutoDock.LEFT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                            } else { // Animate to top
                                animateDirectionForDockFull = AutoDock.TOP
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                            }
                        } else { // On bottom screen // Bottom left
                            if (left <= context.getScreenAvailableHeight() - getFloatViewBottomLeftPos().y) { // Animate to left
                                animateDirectionForDockFull = AutoDock.LEFT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                            } else { // Animate to bottom
                                animateDirectionForDockFull = AutoDock.BOTTOM
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                            }
                        }
                    } else { // On right screen
                        if (floatViewCenterY <= context.getScreenAvailableHeight() / 2) { // On top screen // Top right
                            if (getFloatViewTopRightPos().y - drawHeightOffset <= context.getScreenWidth() - getFloatViewTopRightPos().x) { // Animate to top
                                animateDirectionForDockFull = AutoDock.TOP
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                            } else { // Animate to right
                                animateDirectionForDockFull = AutoDock.RIGHT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                            }
                        } else { // On bottom screen // Bottom right
                            if (context.getScreenAvailableHeight() - getFloatViewBottomRightPos().y <= context.getScreenWidth() - getFloatViewBottomRightPos().x) { // Animate to bottom
                                animateDirectionForDockFull = AutoDock.BOTTOM
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                            } else { // Animate to right
                                animateDirectionForDockFull = AutoDock.RIGHT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                            }
                        }
                    }
                }
            }?.apply {
                duration = config.dockAnimDuration
                start()
            }?.addUpdateListener {
                when (autoDock) {
                    AutoDock.NONE                                      -> Unit
                    AutoDock.LEFT, AutoDock.RIGHT, AutoDock.LEFT_RIGHT -> layoutParams.x = it.animatedValue as Int
                    AutoDock.TOP, AutoDock.BOTTOM, AutoDock.TOP_BOTTOM -> layoutParams.y = it.animatedValue as Int
                    AutoDock.FULL                                      -> {
                        when (animateDirectionForDockFull) {
                            AutoDock.LEFT, AutoDock.RIGHT -> layoutParams.x = it.animatedValue as Int
                            AutoDock.TOP, AutoDock.BOTTOM -> layoutParams.y = it.animatedValue as Int
                            else                          -> Unit
                        }
                    }
                }
                runCatching { windowManager.updateViewLayout(v, layoutParams) }.onFailure { e ->
                    e.printStackTrace()
                    it.cancel() // Cancel animation
                }
            }
        }
    }

    fun updateFloatViewPosition(x: Int?, y: Int?) {
        config.customView?.let { v ->
            runCatching {
                x?.let {
                    val finalX = adjustPosX(x, config.edgeMargin)
                    layoutParams.x = finalX
                    config.x = finalX
                }
                y?.let {
                    val finalY = adjustPosY(y, config.edgeMargin)
                    layoutParams.y = finalY
                    config.y = finalY
                }
                windowManager.updateViewLayout(v, layoutParams)
            }.onFailure { e ->
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

        val finalX = adjustPosX(config.x, config.edgeMargin)
        layoutParams.x = finalX
        config.x = finalX

        val finalY = adjustPosY(config.y, config.edgeMargin)
        layoutParams.y = finalY
        config.y = finalY

        layoutParams.x = finalX
        layoutParams.y = finalY

        // Ensure to set the touch listener to the root view.
        config.customView?.setOnTouchListener(onTouchListener)
    }

    private fun adjustPosX(x: Int, minValue: Int): Int {
        if (x < minValue || x <= 0) return minValue
        return if ((x + (config.customView?.width
                ?: 0) + minValue) >= context.getScreenWidth()
        ) context.getScreenWidth() - (config.customView?.width ?: 0) - minValue else x
    }

    private fun adjustPosY(y: Int, minValue: Int): Int {
        if (y <= minValue + drawHeightOffset) return minValue + drawHeightOffset
        return if ((y + (config.customView?.height
                ?: 0) + minValue) >= context.getScreenAvailableHeight()
        ) context.getScreenAvailableHeight() - (config.customView?.height ?: 0) - minValue
        else y
    }

    private fun getFloatViewLeftMinMargin(): Int = config.edgeMargin
    private fun getFloatViewRightMaxMargin(): Int =
            context.getScreenWidth() - (config.customView?.width ?: 0) - config.edgeMargin

    private fun getFloatViewTopMinMargin(): Int = drawHeightOffset + config.edgeMargin
    private fun getFloatViewBottomMaxMargin(): Int =
            context.getScreenAvailableHeight() - (config.customView?.height ?: 0) - config.edgeMargin

    private fun getFloatViewTopLeftPos(): Point = Point(layoutParams.x, layoutParams.y)
    private fun getFloatViewTopRightPos(): Point =
            Point(layoutParams.x + (config.customView?.width ?: 0), layoutParams.y)

    private fun getFloatViewBottomLeftPos(): Point =
            Point(layoutParams.x, layoutParams.y + (config.customView?.height ?: 0))

    private fun getFloatViewBottomRightPos(): Point =
            Point(layoutParams.x + (config.customView?.width ?: 0), layoutParams.y + (config.customView?.height ?: 0))

    private fun setWindowLayoutParams() {
        drawHeightOffset = if (config.canDragOverStatusBar) 0 else context.statusBarHeight
        layoutParams = WindowManager.LayoutParams().apply {
            format = PixelFormat.TRANSLUCENT
            flags = if (config.touchable) {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            } else {
                // In ScreenShareMasterActivity demo,
                // I just want to mask a full screen transparent float window and I can show finger paint on screen.
                // Meanwhile, I can still touch screen and pass through the float window to the bottom layer just like no that float window.
                // In this case, I should set touchable status to `false`.

                // FLAG_NOT_TOUCHABLE will bubble the event to the bottom layer.
                // However the float layer itself can not be touched anymore.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            }
            type = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else                                           -> @Suppress("DEPRECATION") {
                    WindowManager.LayoutParams.TYPE_TOAST or WindowManager.LayoutParams.TYPE_PHONE
                }
                // Attention: Add [WindowManager.LayoutParams.TYPE_PHONE] type will fix the following error if API below Android 8.0
                // android.view.WindowManager$BadTokenException: Unable to add window -- token null is not valid; is your activity running?
            }

            gravity = Gravity.TOP or Gravity.START // Default value: Gravity.CENTER
            width =
                    if (config.fullScreenFloatView) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
            height =
                    if (config.fullScreenFloatView) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
        }
    }

    private fun addTouchListenerToView(view: View, touchListener: View.OnTouchListener) {
        if (view is ViewGroup) {
//                if (GlobalConstants.DEBUG) LogContext.log.e("Found ViewGroup: ${getResourceEntryName(view.id)}:${view::class.simpleName}")
            if (view.hasOnClickListeners()) {
//                    if (GlobalConstants.DEBUG) LogContext.log.e("setOnTouchListener for ViewGroup: ${getResourceEntryName(view.id)}:${view::class.simpleName}")
                view.setOnTouchListener(touchListener)
            }
            view.children.forEach { child -> addTouchListenerToView(child, touchListener) }
        } else {
//                if (GlobalConstants.DEBUG) LogContext.log.e("Found View     : ${getResourceEntryName(view.id)}:${view::class.simpleName}")
            if (view.hasOnClickListeners()) {
//                    if (GlobalConstants.DEBUG) LogContext.log.e("setOnTouchListener for View     : ${getResourceEntryName(view.id)}:${view::class.simpleName}")
                view.setOnTouchListener(touchListener)
            }
        }
    }

    fun updateAutoDock(autoDock: AutoDock) {
        config.autoDock = autoDock
        val floatViewTopLeft = getFloatViewTopLeftPos()
        startDockAnim(floatViewTopLeft.x, floatViewTopLeft.y, autoDock)
    }

    fun updateStickyEdge(stickyEdge: StickyEdge) {
        config.stickyEdge = stickyEdge
        updateLayoutForSticky(0, 0)
        runCatching {
            windowManager.updateViewLayout(config.customView, layoutParams)
        }.onFailure { e -> e.printStackTrace() }
    }

    @Suppress("unused")
    private fun getResourceEntryName(@IdRes id: Int): String =
            runCatching { context.resources.getResourceEntryName(id) }.getOrDefault("")

    fun show() {
        runCatching {
            init()
            dismiss()
            addTouchListenerToView(config.customView!!, onTouchListener)
            windowManager.addView(config.customView!!, layoutParams)
            visible(true)
            updateAutoDock(config.autoDock)
            updateStickyEdge(config.stickyEdge)
        }.onFailure { it.printStackTrace() }
    }

    fun dismiss() {
        if (config.isShowing) {
            visible(false)
            windowManager.removeView(config.customView)
        }
    }

    fun visible(show: Boolean) {
        config.customView?.visibility = if (show) {
            config.isShowing = true
            View.VISIBLE
        } else {
            config.isShowing = false
            View.GONE
        }
    }
}