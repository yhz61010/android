package com.leovp.floatview_sdk.framework

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Size
import android.view.*
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.children
import com.leovp.floatview_sdk.entities.DefaultConfig
import com.leovp.floatview_sdk.entities.DockEdge
import com.leovp.floatview_sdk.entities.StickyEdge
import com.leovp.floatview_sdk.utils.getScreenSize
import com.leovp.floatview_sdk.utils.screenRealResolution
import com.leovp.floatview_sdk.utils.screenSurfaceRotation
import com.leovp.floatview_sdk.utils.statusBarHeight
import com.leovp.lib_reflection.wrappers.ServiceManager
import kotlin.math.abs

/**
 * Author: Michael Leo
 * Date: 2021/8/30 10:56
 */
internal class FloatViewImpl(private val context: Context, internal var config: DefaultConfig) {
    companion object {
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

    private val rotationWatcher = object : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            //            Log.e("LEO-float-view", "tag=${config.tag} onRotationChanged rotation=$rotation lastScreenOrientation=$lastScrOri config.screenOrientation=${config.screenOrientation}")
            if (rotation != lastScrOri) config.customView?.post { updateScreenOrientation(rotation) }
        }
    }

    private fun getScreenOrientationSize(orientation: Int): Size {
        val curScreenOrientationSize = context.getScreenSize(orientation, context.screenRealResolution)
        //        Log.e("LEO-float-view", "getScreenOrientationSize()=$curScreenOrientationSize")
        return Size(curScreenOrientationSize.width, curScreenOrientationSize.height - getDrawHeightOffset())
    }

    private var lastScrOri = context.screenSurfaceRotation.takeIf { config.screenOrientation == -1 } ?: config.screenOrientation
    private var scrOriSz: Size = getScreenOrientationSize(lastScrOri)

    @SuppressLint("ClickableViewAccessibility")
    private val onTouchListener = View.OnTouchListener { view, event ->
        val consumeIsAlwaysFalse = !config.enableDrag || config.fullScreenFloatView

        val totalDeltaX = lastX - firstX
        val totalDeltaY = lastY - firstY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN    -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                firstX = lastX
                firstY = lastY
                isClickGesture = true
                //                Log.e("LEO-FV",
                //                    "ACTION_DOWN isPressed=${view.isPressed} hasFocus=${view.hasFocus()} isActivated=${view.isActivated} $view")
                touchConsumedByMove = config.touchEventListener?.touchDown(view, lastX, lastY) ?: false
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_OUTSIDE -> {
                //                view.performClick()
                //                Log.e("LEO-FV",
                //                    "ACTION_UP isPressed=${view.isPressed} " +
                //                            "consumeIsAlwaysFalse=$consumeIsAlwaysFalse " +
                //                            "config.autoDock=${config.autoDock} " +
                //                            "hasFocus=${view.hasFocus()} " +
                //                            "isActivated=${view.isActivated} " +
                //                            "isClickGesture=$isClickGesture $view")
                if (!consumeIsAlwaysFalse && config.dockEdge != DockEdge.NONE) {
                    startDockAnim(layoutParams.x, layoutParams.y, config.dockEdge)
                }
                touchConsumedByMove = config.touchEventListener?.touchUp(view, lastX, lastY, isClickGesture) ?: !isClickGesture
            }
            MotionEvent.ACTION_MOVE    -> {
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
                touchConsumedByMove = config.touchEventListener?.touchMove(view, lastX, lastY, isClickGesture) ?: touchConsumedByMove
            }
            else                       -> Unit
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

    private fun startDockAnim(left: Int, top: Int, dockEdge: DockEdge) {
        config.customView?.let { v ->
            val floatViewCenterX = left + config.customView!!.width / 2
            val floatViewCenterY = top + config.customView!!.height / 2
            var animateDirectionForDockFull = DockEdge.NONE
            when (dockEdge) {
                DockEdge.NONE       -> null
                DockEdge.LEFT       -> ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                DockEdge.RIGHT      -> ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                DockEdge.TOP        -> ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                DockEdge.BOTTOM     -> ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                DockEdge.LEFT_RIGHT -> {
                    if (floatViewCenterX <= scrOriSz.width / 2) {
                        ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                    } else {
                        ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                    }
                }
                DockEdge.TOP_BOTTOM -> {
                    if (floatViewCenterY <= scrOriSz.height / 2) {
                        ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                    } else {
                        ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                    }
                }
                DockEdge.FULL       -> {
                    if (floatViewCenterX <= scrOriSz.width / 2) { // On left screen
                        if (floatViewCenterY <= scrOriSz.height / 2) { // On top screen // Top left
                            if (left <= top - getDrawHeightOffset()) { // Animate to left
                                animateDirectionForDockFull = DockEdge.LEFT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                            } else { // Animate to top
                                animateDirectionForDockFull = DockEdge.TOP
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                            }
                        } else { // On bottom screen // Bottom left
                            if (left <= scrOriSz.height - getFloatViewBottomLeftPos().y) { // Animate to left
                                animateDirectionForDockFull = DockEdge.LEFT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewLeftMinMargin())
                            } else { // Animate to bottom
                                animateDirectionForDockFull = DockEdge.BOTTOM
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                            }
                        }
                    } else { // On right screen
                        if (floatViewCenterY <= scrOriSz.height / 2) { // On top screen // Top right
                            if (getFloatViewTopRightPos().y - getDrawHeightOffset() <= scrOriSz.width - getFloatViewTopRightPos().x) { // Animate to top
                                animateDirectionForDockFull = DockEdge.TOP
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewTopMinMargin())
                            } else { // Animate to right
                                animateDirectionForDockFull = DockEdge.RIGHT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                            }
                        } else { // On bottom screen // Bottom right
                            if (scrOriSz.height - getFloatViewBottomRightPos().y <= scrOriSz.width - getFloatViewBottomRightPos().x) { // Animate to bottom
                                animateDirectionForDockFull = DockEdge.BOTTOM
                                ObjectAnimator.ofInt(v, "translationY", top, getFloatViewBottomMaxMargin())
                            } else { // Animate to right
                                animateDirectionForDockFull = DockEdge.RIGHT
                                ObjectAnimator.ofInt(v, "translationX", left, getFloatViewRightMaxMargin())
                            }
                        }
                    }
                }
            }?.apply {
                duration = config.dockAnimDuration
                start()
            }?.addUpdateListener {
                when (dockEdge) {
                    DockEdge.NONE       -> Unit
                    DockEdge.LEFT,
                    DockEdge.RIGHT,
                    DockEdge.LEFT_RIGHT -> layoutParams.x = it.animatedValue as Int

                    DockEdge.TOP,
                    DockEdge.BOTTOM,
                    DockEdge.TOP_BOTTOM -> layoutParams.y = it.animatedValue as Int

                    DockEdge.FULL       -> {
                        when (animateDirectionForDockFull) {
                            DockEdge.LEFT,
                            DockEdge.RIGHT  -> layoutParams.x = it.animatedValue as Int

                            DockEdge.TOP,
                            DockEdge.BOTTOM -> layoutParams.y = it.animatedValue as Int
                            else            -> Unit
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

    fun updateFloatViewPosition(x: Int?, y: Int?, accumulateY: Boolean = false) {
        config.customView?.let { v ->
            runCatching {
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
                //                Log.e("LEO-float-view", "updateFloatViewPosition x=$x y=$y layoutParams=${layoutParams.x}x${layoutParams.y}")
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

        //        Log.e("LEO-float-view", "1config.x=${config.x} config.edgeMargin=${config.edgeMargin}")
        val finalX = adjustPosX(config.x, config.edgeMargin)
        //        Log.e("LEO-float-view", "2config.x=${config.x} finalX=$finalX config.edgeMargin=${config.edgeMargin}")
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

    private fun adjustPosX(x: Int, edgeMargin: Int): Int {
        if (x < edgeMargin || x <= 0) return edgeMargin
        return if ((x + (config.customView?.width ?: 0) + edgeMargin) >= scrOriSz.width)
            scrOriSz.width - (config.customView?.width ?: 0) - edgeMargin else x
    }

    /**
     * @param considerY When it is true, if the `y` is less equal then the minimum value,
     * the result will accumulate `y`.
     */
    private fun adjustPosY(y: Int, edgeMargin: Int, considerY: Boolean = false): Int {
        val drawHeightOffset = getDrawHeightOffset()
        if (y <= edgeMargin + drawHeightOffset) return edgeMargin + drawHeightOffset + (y.takeIf { considerY } ?: 0)
        return if ((y + (config.customView?.height ?: 0) + edgeMargin) >= scrOriSz.height)
            scrOriSz.height - (config.customView?.height ?: 0) - edgeMargin
        else y
    }

    private fun getFloatViewLeftMinMargin(): Int = config.edgeMargin
    private fun getFloatViewRightMaxMargin(): Int = scrOriSz.width - (config.customView?.width ?: 0) - config.edgeMargin

    private fun getFloatViewTopMinMargin(): Int = getDrawHeightOffset() + config.edgeMargin
    private fun getFloatViewBottomMaxMargin(): Int = scrOriSz.height - (config.customView?.height ?: 0) - config.edgeMargin

    private fun getFloatViewTopLeftPos(): Point = Point(layoutParams.x, layoutParams.y)
    private fun getFloatViewTopRightPos(): Point = Point(layoutParams.x + (config.customView?.width ?: 0), layoutParams.y)

    private fun getFloatViewBottomLeftPos(): Point = Point(layoutParams.x, layoutParams.y + (config.customView?.height ?: 0))

    private fun getFloatViewBottomRightPos(): Point =
            Point(layoutParams.x + (config.customView?.width ?: 0), layoutParams.y + (config.customView?.height ?: 0))

    private fun getDrawHeightOffset(): Int = if (config.immersiveMode) 0 else context.statusBarHeight

    private fun setWindowLayoutParams() {
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
            if (config.systemWindow) {
                type = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else                                           -> @Suppress("DEPRECATION") {
                        WindowManager.LayoutParams.TYPE_TOAST or WindowManager.LayoutParams.TYPE_PHONE
                    }
                    // Attention: Add [WindowManager.LayoutParams.TYPE_PHONE] type will fix the following error if API below Android 8.0
                    // android.view.WindowManager${WindowManager.BadTokenException}: Unable to add window -- token null is not valid; is your activity running?
                }
            }

            gravity = Gravity.TOP or Gravity.START // Default value: Gravity.CENTER
            width = if (config.fullScreenFloatView) WindowManager.LayoutParams.MATCH_PARENT else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }
            height = if (config.fullScreenFloatView) WindowManager.LayoutParams.MATCH_PARENT else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }
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

    @MainThread
    fun updateAutoDock(dockEdge: DockEdge) {
        config.dockEdge = dockEdge
        val floatViewTopLeft = getFloatViewTopLeftPos()
        startDockAnim(floatViewTopLeft.x, floatViewTopLeft.y, dockEdge)
    }

    @MainThread
    fun updateStickyEdge(stickyEdge: StickyEdge) {
        config.stickyEdge = stickyEdge
        updateLayoutForSticky(0, 0)
        runCatching {
            //            if (config.customView?.windowToken != null) {
            //            Log.e("LEO-float-view", "${config.tag} updateStickyEdge=$stickyEdge x=${config.x} y=${config.y}")
            windowManager.updateViewLayout(config.customView, layoutParams)
            //            }
        }.onFailure { e -> e.printStackTrace() }
    }

    @MainThread
    fun updateScreenOrientation(orientation: Int) {
        lastScrOri = orientation
        config.screenOrientation = orientation
        scrOriSz = getScreenOrientationSize(orientation)
        updateAutoDock(config.dockEdge)
        updateStickyEdge(config.stickyEdge)
        //        Log.e("LEO-float-view", "lastScrOri=$lastScrOri config.screenOrientation=$orientation")
    }

    @Suppress("unused")
    private fun getResourceEntryName(@IdRes id: Int): String = runCatching { context.resources.getResourceEntryName(id) }.getOrDefault("")

    fun show() {
        runCatching {
            //            Log.e("LEO-float-view", "1 show() x=${config.x} y=${config.y} lastScrOri=$lastScrOri scrOriSz=$scrOriSz")
            remove(true)
            //            Log.e("LEO-float-view", "1.2 show() x=${config.x} y=${config.y} lastScrOri=$lastScrOri scrOriSz=$scrOriSz")
            ServiceManager.windowManager?.registerRotationWatcher(rotationWatcher)
            init()
            //            Log.e("LEO-float-view", "2 show() x=${config.x} y=${config.y} lastScrOri=$lastScrOri scrOriSz=$scrOriSz")
            addTouchListenerToView(config.customView!!, onTouchListener)
            windowManager.addView(config.customView!!, layoutParams)
            visible(true)
            //            Log.e("LEO-float-view", "3 show() x=${config.x} y=${config.y} lastScrOri=$lastScrOri scrOriSz=$scrOriSz")
            updateAutoDock(config.dockEdge)
            updateStickyEdge(config.stickyEdge)
        }.onFailure {
            ServiceManager.windowManager?.removeRotationWatcher(rotationWatcher)
            it.printStackTrace()
        }
    }

    fun remove(immediately: Boolean = false) {
        ServiceManager.windowManager?.removeRotationWatcher(rotationWatcher)
        if (immediately) {
            config.customView?.let { v ->
                hideCustomView(v)
                if (v.windowToken != null) windowManager.removeViewImmediate(v)
            }
        } else visible(false) {
            config.customView?.let { v ->
                if (v.windowToken != null) windowManager.removeViewImmediate(v)
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

    //    private fun setCustomViewVisibility(show: Boolean) {
    //        config.customView?.visibility = if (show) {
    //            config.isDisplaying = true
    //            View.VISIBLE
    //        } else {
    //            config.isDisplaying = false
    //            View.GONE
    //        }
    //    }
}