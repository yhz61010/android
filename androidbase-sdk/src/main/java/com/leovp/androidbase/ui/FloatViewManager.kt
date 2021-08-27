package com.leovp.androidbase.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import androidx.annotation.LayoutRes
import androidx.core.view.children
import com.leovp.androidbase.exts.android.*
import com.leovp.androidbase.exts.kotlin.fail
import com.leovp.androidbase.utils.log.LogContext
import kotlin.math.abs


/**
 * Author: Michael Leo
 * Date: 20-3-3 下午3:40
 *
 * Need permission: `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
 *
 * @see [Float View](https://stackoverflow.com/a/53092436)
 * @see [Float View Github](https://github.com/aminography/FloatingWindowApp)
 */
class FloatViewManager(
    private val context: Context,
    @LayoutRes private var layoutId: Int,
    private val posX: Int = 0,
    private val posY: Int = 0
) {
    var touchEventListener: TouchEventListener? = null

    private var _enableDrag = true

    private var _fullScreenFloatView = false
    private var _canDragOverStatusBar = false

    /**
     * If [_canDragOverStatusBar] is false, this values is the height of status bar.
     */
    private var drawHeightOffset = 0

    var enableDrag: Boolean
        get() = _enableDrag
        set(enableDrag) {
            _enableDrag = enableDrag
            updateLayout()
        }

    /**
     * Whether the window itself in full screen mode.
     * In full screen mode, the [enableDrag] will be ignored and the click event for this float view itself will be consumed.
     */
    var enableFullScreenFloatView: Boolean
        get() = _fullScreenFloatView
        set(enableFloatViewInFullScreen) {
            _fullScreenFloatView = enableFloatViewInFullScreen
            updateLayout()
        }

    /**
     * If the float view is in full screen mode, this property will be ignored.
     */
    var canDragOverStatusBar: Boolean
        get() = _canDragOverStatusBar
        set(canDragOverStatusBar) {
            _canDragOverStatusBar = canDragOverStatusBar
            updateLayout()
        }

    var x: Int
        get() = layoutParams.x
        set(x) {
            if (context !is Activity) fail("The context should be Activity context.")
            LogContext.log.e("floatView dimension x=${floatView.width}x${floatView.height}")
            var finalX = x
            if (x < 0) finalX = 0
            if ((x + layoutParams.width) > context.screenRealWidth) finalX = context.screenRealWidth - layoutParams.width
            layoutParams.x = finalX
            windowManager.updateViewLayout(floatView, layoutParams)

//            if (GlobalConstants.DEBUG) LogContext.log.e( "floatView dimension55=${floatView.width}x${floatView.height}")
//            if (GlobalConstants.DEBUG) LogContext.log.e( "floatView dimension66=${floatView.measuredWidth}x${floatView.measuredHeight}")
//            Handler(context.mainLooper).post {
//                if (GlobalConstants.DEBUG) LogContext.log.e( "floatView dimension77=${floatView.width}x${floatView.height}")
//                if (GlobalConstants.DEBUG) LogContext.log.e( "floatView dimension88=${floatView.measuredWidth}x${floatView.measuredHeight}")
//            }
        }

    var y: Int
        get() = layoutParams.y
        set(y) {
            LogContext.log.e("floatView dimension y=${floatView.width}x${floatView.height}")
            var finalY = y
            if (y < drawHeightOffset) finalY = drawHeightOffset
            if ((y + layoutParams.height) > context.screenAvailableHeight) finalY = context.screenAvailableHeight - layoutParams.height
            layoutParams.y = finalY
            windowManager.updateViewLayout(floatView, layoutParams)
        }

    /**
     * Note that, [dismiss] method will be executed when you call this method.
     */
    fun replaceLayout(@LayoutRes layoutId: Int) {
        dismiss()
        this.layoutId = layoutId
        floatView = LayoutInflater.from(context).inflate(layoutId, null)
        init()
    }

    var floatView: View = LayoutInflater.from(context).inflate(layoutId, null)
        private set

    @SuppressLint("ClickableViewAccessibility")
    private val onTouchListener = View.OnTouchListener { view, event ->
        if (!_enableDrag || _fullScreenFloatView) return@OnTouchListener true
        val totalDeltaX = lastX - firstX
        val totalDeltaY = lastY - firstY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                firstX = lastX
                firstY = lastY
                isClickGesture = true
//                if (GlobalConstants.DEBUG) LogContext.log.e("ACTION_DOWN isPressed=${view.isPressed} hasFocus=${view.hasFocus()} isActivated=${view.isActivated} $view")
                touchConsumedByMove = touchEventListener?.touchDown(view, lastX, lastY) ?: false
            }
            MotionEvent.ACTION_UP -> {
//                view.performClick()
//                if (GlobalConstants.DEBUG) LogContext.log.e("ACTION_UP isPressed=${view.isPressed} hasFocus=${view.hasFocus()} isActivated=${view.isActivated} isClickGesture=$isClickGesture $view")
                touchConsumedByMove = touchEventListener?.touchUp(view, lastX, lastY, isClickGesture) ?: !isClickGesture
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX.toInt() - lastX
                val deltaY = event.rawY.toInt() - lastY
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                if (abs(totalDeltaX) >= TOUCH_TOLERANCE_IN_PX || abs(totalDeltaY) >= TOUCH_TOLERANCE_IN_PX) {
                    isClickGesture = false
                    view.isPressed = false
                    if (event.pointerCount == 1) {
                        layoutParams.x += deltaX
                        layoutParams.y += deltaY
                        if (layoutParams.y <= drawHeightOffset) layoutParams.y = drawHeightOffset
                        touchConsumedByMove = true
                        windowManager.updateViewLayout(floatView, layoutParams)
                    } else {
                        touchConsumedByMove = false
                    }
                } else {
                    isClickGesture = true
                    touchConsumedByMove = false
                }
                touchConsumedByMove = touchEventListener?.touchMove(view, lastX, lastY, isClickGesture) ?: touchConsumedByMove
            }
            else -> Unit
        }
        touchConsumedByMove
    }

    init {
        init()
    }

    private fun init() {
        updateLayout()
        // Ensure to set the touch listener to the root view.
        floatView.setOnTouchListener(onTouchListener)
    }

    private fun addTouchListenerToView(view: View, touchListener: View.OnTouchListener) {
        if (view is ViewGroup) {
//            runCatching {
//                if (GlobalConstants.DEBUG) LogContext.log.e("Found ViewGroup: ${app.resources.getResourceEntryName(view.id)}:${view::class.simpleName}")
//            }
            if (view.hasOnClickListeners()) {
//                runCatching {
//                    if (GlobalConstants.DEBUG) LogContext.log.e("setOnTouchListener for ViewGroup: ${app.resources.getResourceEntryName(view.id)}:${view::class.simpleName}")
//                }
                view.setOnTouchListener(touchListener)
            }
            view.children.forEach { child -> addTouchListenerToView(child, touchListener) }
        } else {
//            runCatching {
//                if (GlobalConstants.DEBUG) LogContext.log.e("Found View     : ${app.resources.getResourceEntryName(view.id)}:${view::class.simpleName}")
//            }
            if (view.hasOnClickListeners()) {
//                runCatching {
//                    if (GlobalConstants.DEBUG) LogContext.log.e("setOnTouchListener for View     : ${app.resources.getResourceEntryName(view.id)}:${view::class.simpleName}")
//                }
                view.setOnTouchListener(touchListener)
            }
        }
    }

    private val windowManager: WindowManager = (context.getSystemService(WINDOW_SERVICE) as WindowManager)

    private lateinit var layoutParams: WindowManager.LayoutParams

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var firstX: Int = 0
    private var firstY: Int = 0

    var isShowing = false
        private set
    private var touchConsumedByMove = false

    private var isClickGesture = true

    private fun updateLayout() {
        drawHeightOffset = if (_canDragOverStatusBar) 0 else context.statusBarHeight
        layoutParams = WindowManager.LayoutParams().apply {
            format = PixelFormat.TRANSLUCENT
            flags = if (_enableDrag && !_fullScreenFloatView) {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            } else {
                // FLAG_NOT_TOUCHABLE will bubble the event to the bottom layer.
                // However the float layer itself can not be touched anymore.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
            }
            @Suppress("DEPRECATION")
            type = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else -> WindowManager.LayoutParams.TYPE_TOAST
            }

            gravity = Gravity.TOP or Gravity.START // Default value: Gravity.CENTER
            x = posX
            y = posY
            width = if (_fullScreenFloatView) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
            height = if (_fullScreenFloatView) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
        }
    }

    fun show() {
        if (context.canDrawOverlays) {
            runCatching {
                dismiss()
                addTouchListenerToView(floatView, onTouchListener)
                isShowing = true
                windowManager.addView(floatView, layoutParams)
            }.onFailure { it.printStackTrace() }
        }
    }

    @Suppress("unused")
    fun dismiss() {
        if (isShowing) {
            windowManager.removeView(floatView)
            isShowing = false
        }
    }

    interface TouchEventListener {
        fun touchDown(view: View, x: Int, y: Int): Boolean = false
        fun touchMove(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean = true

        /**
         * Generally, if [isClickGesture] is `false` that means before touch up being triggered, user is just moving the view.
         * At this time, we should consume this touch event so the click listener that use set should NOT be triggered.
         *
         * In contrast, if [isClickGesture] is `true` that means user triggers the click event,
         * so this touch event should not be consumed.
         */
        fun touchUp(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean = !isClickGesture
    }

    companion object {
        private const val TOUCH_TOLERANCE_IN_PX = 8
    }
}