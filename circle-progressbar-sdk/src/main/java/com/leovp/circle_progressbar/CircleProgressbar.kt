package com.leovp.circle_progressbar

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.*
import kotlin.math.abs
import kotlin.math.min

/**
 * This class is copied from [AndroidButtonProgress](https://github.com/abdularis/AndroidButtonProgress)
 * and make some small changes.
 *
 * This view class shows 4 different view in different state
 * 1. Idle state show a button (download button in this case, but not limited)
 * 2. Indeterminate state show indeterminate circular progress, with optional button in the center
 * 3. Determinate state show determinate progress, with optional button in the center
 * 4. Finish state show finish drawable or hide this view
 *
 * You can use this view to make a download or upload button, you might also use this for another purpose.
 */
@Suppress("unused", "WeakerAccess")
class CircleProgressbar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), View.OnClickListener {
    private var _idleIcon: Drawable
    private var _cancelIcon: Drawable
    private var _finishIcon: Drawable
    private var _cancelable = DEF_CANCELABLE
    private var _enableClickListener = DEF_ENABLE_CLICK_LISTENER
    private var _idleIconWidth = 0
    private var _idleIconHeight = 0
    private var _cancelIconWidth = 0
    private var _cancelIconHeight = 0
    private var _finishIconWidth = 0
    private var _finishIconHeight = 0
    var currState = STATE_IDLE
        private set
    private var _maxProgress = 100
    private var _currProgress = 0
    private var _idleBgColor = DEF_BG_COLOR
    private var _finishBgColor = DEF_BG_COLOR
    private var _indeterminateBgColor = DEF_BG_COLOR
    private var _determinateBgColor = DEF_BG_COLOR
    private var _idleBgDrawable: Drawable? = null
    private var _finishBgDrawable: Drawable? = null
    private var _indeterminateBgDrawable: Drawable? = null
    private var _determinateBgDrawable: Drawable? = null
    private lateinit var _indeterminateAnimator: ValueAnimator
    private var _currIndeterminateBarPos = 0
    private var _progressIndeterminateSweepAngle = DEF_PROGRESS_INDETERMINATE_WIDTH
    private var _progressDeterminateColor = DEF_DETERMINATE_COLOR
    private var _progressIndeterminateColor = DEF_INDETERMINATE_COLOR
    private var _progressMargin = DEF_PROGRESS_MARGIN
    private val _bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val _bgRect = RectF()
    private val _progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val _progressRect = RectF()
    private val _clickListeners: MutableList<OnClickListener> = ArrayList()
    private val _onStateChangedListeners: MutableList<OnStateChangedListener> = ArrayList()

    private val _progressTextPaint = Paint()
    private var _showProgressText = DEF_SHOW_PROGRESS_TEXT
    private var _progressTextColor = DEF_PROGRESS_TEXT_COLOR
    private var _progressTextSize = sp2px(DEF_PROGRESS_TEXT_SIZE_IN_SP.toFloat(), context)

    init {
        super.setOnClickListener(this)
        initIndeterminateAnimator()
        _progressPaint.style = Paint.Style.STROKE
        _progressPaint.isDither = true
        _progressPaint.strokeJoin = Paint.Join.ROUND
        _progressPaint.strokeCap = Paint.Cap.ROUND
        _progressPaint.pathEffect = CornerPathEffect(50f)

        _progressTextPaint.style = Paint.Style.FILL
        _progressTextPaint.textAlign = Paint.Align.CENTER

//        val res = context.resources
        if (attrs != null) {
            val attr = context.obtainStyledAttributes(attrs, R.styleable.CircleProgressbar, 0, 0)
            initBackgroundDrawableFromAttributes(attr)
            currState = attr.getInt(R.styleable.CircleProgressbar_state, STATE_IDLE)
            _cancelable = attr.getBoolean(R.styleable.CircleProgressbar_cancelable, DEF_CANCELABLE)
            _enableClickListener = attr.getBoolean(R.styleable.CircleProgressbar_enableClickListener, DEF_ENABLE_CLICK_LISTENER)
            _progressIndeterminateSweepAngle = attr.getInteger(R.styleable.CircleProgressbar_progressIndeterminateSweepAngle, DEF_PROGRESS_INDETERMINATE_WIDTH)
            _progressDeterminateColor = attr.getColor(R.styleable.CircleProgressbar_progressDeterminateColor, DEF_DETERMINATE_COLOR)
            _progressIndeterminateColor = attr.getColor(R.styleable.CircleProgressbar_progressIndeterminateColor, DEF_INDETERMINATE_COLOR)
            _progressPaint.strokeWidth = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_progressWidth, DEF_PROGRESS_WIDTH).toFloat()
            _progressMargin = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_progressMargin, DEF_PROGRESS_MARGIN)
            _currProgress = attr.getInteger(R.styleable.CircleProgressbar_currentProgress, 0)
            _maxProgress = attr.getInteger(R.styleable.CircleProgressbar_maxProgress, 100)
            val icIdleDrawableId = attr.getResourceId(R.styleable.CircleProgressbar_idleIconDrawable, R.drawable.ic_default_idle)
            _idleIcon = context.getDrawable(icIdleDrawableId)!!
            _idleIconWidth = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_idleIconWidth, _idleIcon.minimumWidth)
            _idleIconHeight = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_idleIconHeight, _idleIcon.minimumHeight)
            val icCancelDrawableId = attr.getResourceId(R.styleable.CircleProgressbar_cancelIconDrawable, R.drawable.ic_default_cancel)
            _cancelIcon = context.getDrawable(icCancelDrawableId)!!
            _cancelIconWidth = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_cancelIconWidth, _cancelIcon.minimumWidth)
            _cancelIconHeight = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_cancelIconHeight, _cancelIcon.minimumHeight)
            val icFinishDrawableId = attr.getResourceId(R.styleable.CircleProgressbar_finishIconDrawable, R.drawable.ic_default_finish)
            _finishIcon = context.getDrawable(icFinishDrawableId)!!
            _finishIconWidth = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_finishIconWidth, _finishIcon.minimumWidth)
            _finishIconHeight = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_finishIconHeight, _finishIcon.minimumHeight)

            _showProgressText = attr.getBoolean(R.styleable.CircleProgressbar_showProgressText, DEF_SHOW_PROGRESS_TEXT)
            _progressTextColor = attr.getColor(R.styleable.CircleProgressbar_progressTextColor, DEF_PROGRESS_TEXT_COLOR)
            _progressTextSize = attr.getDimensionPixelSize(R.styleable.CircleProgressbar_progressTextSize, sp2px(DEF_PROGRESS_TEXT_SIZE_IN_SP.toFloat(), context))

            attr.recycle()
        } else {
            _progressPaint.strokeWidth = DEF_PROGRESS_WIDTH.toFloat()
            _idleIcon = context.getDrawable(R.drawable.ic_default_idle)!!
            _idleIconWidth = _idleIcon.minimumWidth
            _idleIconHeight = _idleIcon.minimumHeight
            _cancelIcon = context.getDrawable(R.drawable.ic_default_cancel)!!
            _cancelIconWidth = _cancelIcon.minimumWidth
            _cancelIconHeight = _cancelIcon.minimumHeight
            _finishIcon = context.getDrawable(R.drawable.ic_default_finish)!!
            _finishIconWidth = _finishIcon.minimumWidth
            _finishIconHeight = _finishIcon.minimumHeight
//            _progressTextPaint.color = DEF_PROGRESS_TEXT_COLOR
//            _progressTextPaint.textSize = DEF_PROGRESS_TEXT_SIZE.toFloat()
        }
        if (currState == STATE_INDETERMINATE) setIndeterminate()
    }

    private fun initBackgroundDrawableFromAttributes(attrs: TypedArray) {
        val idleResId = attrs.getResourceId(R.styleable.CircleProgressbar_idleBackgroundDrawable, -1)
        val finishResId = attrs.getResourceId(R.styleable.CircleProgressbar_finishBackgroundDrawable, -1)
        val indeterminateResId = attrs.getResourceId(R.styleable.CircleProgressbar_indeterminateBackgroundDrawable, -1)
        val determinateResId = attrs.getResourceId(R.styleable.CircleProgressbar_determinateBackgroundDrawable, -1)
        if (idleResId != -1) _idleBgDrawable = context.getDrawable(idleResId)
        if (finishResId != -1) _finishBgDrawable = context.getDrawable(finishResId)
        if (indeterminateResId != -1) _indeterminateBgDrawable = context.getDrawable(indeterminateResId)
        if (determinateResId != -1) _determinateBgDrawable = context.getDrawable(determinateResId)
        _idleBgColor = attrs.getColor(R.styleable.CircleProgressbar_idleBackgroundColor, DEF_BG_COLOR)
        _finishBgColor = attrs.getColor(R.styleable.CircleProgressbar_finishBackgroundColor, DEF_BG_COLOR)
        _indeterminateBgColor = attrs.getColor(R.styleable.CircleProgressbar_indeterminateBackgroundColor, DEF_BG_COLOR)
        _determinateBgColor = attrs.getColor(R.styleable.CircleProgressbar_determinateBackgroundColor, DEF_BG_COLOR)
    }

    var maxProgress: Int
        get() = _maxProgress
        set(maxProgress) {
            _maxProgress = maxProgress
            invalidate()
        }
    var currentProgress: Int
        get() = _currProgress
        set(progress) {
            if (currState != STATE_DETERMINATE) return
            _currProgress = min(progress, _maxProgress)
            invalidate()
        }
    var idleIcon: Drawable
        get() = _idleIcon
        set(idleIcon) {
            _idleIcon = idleIcon
            invalidate()
        }
    var cancelIcon: Drawable
        get() = _cancelIcon
        set(cancelIcon) {
            _cancelIcon = cancelIcon
            invalidate()
        }
    var finishIcon: Drawable
        get() = _finishIcon
        set(finishIcon) {
            _finishIcon = finishIcon
            invalidate()
        }
    var isCancelable: Boolean
        get() = _cancelable
        set(cancelable) {
            _cancelable = cancelable
            invalidate()
        }
    var idleIconWidth: Int
        get() = _idleIconWidth
        set(idleIconWidth) {
            _idleIconWidth = idleIconWidth
            invalidate()
        }
    var idleIconHeight: Int
        get() = _idleIconHeight
        set(idleIconHeight) {
            _idleIconHeight = idleIconHeight
            invalidate()
        }
    var cancelIconWidth: Int
        get() = _cancelIconWidth
        set(cancelIconWidth) {
            _cancelIconWidth = cancelIconWidth
            invalidate()
        }
    var cancelIconHeight: Int
        get() = _cancelIconHeight
        set(cancelIconHeight) {
            _cancelIconHeight = cancelIconHeight
            invalidate()
        }
    var finishIconWidth: Int
        get() = _finishIconWidth
        set(finishIconWidth) {
            _finishIconWidth = finishIconWidth
            invalidate()
        }
    var finishIconHeight: Int
        get() = _finishIconHeight
        set(finishIconHeight) {
            _finishIconHeight = finishIconHeight
            invalidate()
        }
    var idleBgColor: Int
        get() = _idleBgColor
        set(idleBgColor) {
            _idleBgColor = idleBgColor
            invalidate()
        }
    var finishBgColor: Int
        get() = _finishBgColor
        set(finishBgColor) {
            _finishBgColor = finishBgColor
            invalidate()
        }
    var indeterminateBgColor: Int
        get() = _indeterminateBgColor
        set(indeterminateBgColor) {
            _indeterminateBgColor = indeterminateBgColor
            invalidate()
        }
    var determinateBgColor: Int
        get() = _determinateBgColor
        set(determinateBgColor) {
            _determinateBgColor = determinateBgColor
            invalidate()
        }
    var idleBgDrawable: Drawable?
        get() = _idleBgDrawable
        set(idleBgDrawable) {
            _idleBgDrawable = idleBgDrawable
            invalidate()
        }
    var finishBgDrawable: Drawable?
        get() = _finishBgDrawable
        set(finishBgDrawable) {
            _finishBgDrawable = finishBgDrawable
            invalidate()
        }
    var indeterminateBgDrawable: Drawable?
        get() = _indeterminateBgDrawable
        set(indeterminateBgDrawable) {
            _indeterminateBgDrawable = indeterminateBgDrawable
            invalidate()
        }
    var determinateBgDrawable: Drawable?
        get() = _determinateBgDrawable
        set(determinateBgDrawable) {
            _determinateBgDrawable = determinateBgDrawable
            invalidate()
        }
    var progressDeterminateColor: Int
        get() = _progressDeterminateColor
        set(progressDeterminateColor) {
            _progressDeterminateColor = progressDeterminateColor
            invalidate()
        }
    var progressIndeterminateColor: Int
        get() = _progressIndeterminateColor
        set(progressIndeterminateColor) {
            _progressIndeterminateColor = progressIndeterminateColor
            invalidate()
        }
    var progressMargin: Int
        get() = _progressMargin
        set(progressMargin) {
            _progressMargin = progressMargin
            invalidate()
        }
    var progressIndeterminateSweepAngle: Int
        get() = _progressIndeterminateSweepAngle
        set(progressIndeterminateSweepAngle) {
            _progressIndeterminateSweepAngle = progressIndeterminateSweepAngle
            invalidate()
        }
    var showProgressText: Boolean
        get() = _showProgressText
        set(showProgressText) {
            _showProgressText = showProgressText
            invalidate()
        }
    var progressTextColor: Int
        get() = _progressTextColor
        set(progressTextColor) {
            _progressTextColor = progressTextColor
            invalidate()
        }
    var progressTextSize: Int
        get() = _progressTextSize
        set(progressTextSize) {
            _progressTextSize = progressTextSize
            invalidate()
        }

    fun setIdle() {
        currState = STATE_IDLE
        callStateChangedListener(currState)
        invalidate()
    }

    fun setIndeterminate() {
        _currIndeterminateBarPos = BASE_START_ANGLE
        currState = STATE_INDETERMINATE
        callStateChangedListener(currState)
        invalidate()
        _indeterminateAnimator.start()
    }

    fun setDeterminate() {
        _indeterminateAnimator.end()
        _currProgress = 0
        currState = STATE_DETERMINATE
        callStateChangedListener(currState)
        invalidate()
    }

    fun setFinish() {
        _currProgress = 0
        currState = STATE_FINISHED
        callStateChangedListener(currState)
        invalidate()
    }

    fun addOnClickListener(listener: OnClickListener): Boolean = if (!_clickListeners.contains(listener)) _clickListeners.add(listener) else false

    fun removeOnClickListener(listener: OnClickListener): Boolean = _clickListeners.remove(listener)

    fun setOnClickListener(listener: OnClickListener) {
        removeAllOnClickListeners()
        addOnClickListener(listener)
    }

    fun removeAllOnClickListeners() = _clickListeners.clear()

    fun addOnStateChangedListeners(listener: OnStateChangedListener): Boolean = if (!_onStateChangedListeners.contains(listener)) _onStateChangedListeners.add(listener) else false

    fun removeOnStateChangedListener(listener: OnStateChangedListener): Boolean = _onStateChangedListeners.remove(listener)

    private fun callStateChangedListener(newState: Int) {
        for (listener in _onStateChangedListeners) listener.onStateChanged(newState)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return _enableClickListener && performClick()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onClick(v: View) {
        if (!_cancelable && (currState == STATE_INDETERMINATE || currState == STATE_DETERMINATE)) return
        if (currState == STATE_IDLE) {
            for (listener in _clickListeners) listener.onIdleButtonClick(v)
        } else if (currState == STATE_INDETERMINATE || currState == STATE_DETERMINATE) {
            for (listener in _clickListeners) listener.onCancelButtonClick(v)
        } else if (currState == STATE_FINISHED) {
            for (listener in _clickListeners) listener.onFinishButtonClick(v)
        }
    }

    private fun drawIdleState(canvas: Canvas) {
        if (_idleBgDrawable != null) {
            _idleBgDrawable?.run {
                setBounds(0, 0, width, height)
                draw(canvas)
            }
        } else {
            _bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            _bgPaint.color = _idleBgColor
            canvas.drawOval(_bgRect, _bgPaint)
        }
        drawDrawableInCenter(_idleIcon, canvas, _idleIconWidth, _idleIconHeight)
    }

    private fun drawFinishState(canvas: Canvas) {
        if (_finishBgDrawable != null) {
            _finishBgDrawable!!.setBounds(0, 0, width, height)
            _finishBgDrawable!!.draw(canvas)
        } else {
            _bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            _bgPaint.color = _finishBgColor
            canvas.drawOval(_bgRect, _bgPaint)
        }
        drawDrawableInCenter(_finishIcon, canvas, _finishIconWidth, _finishIconHeight)
    }

    private fun drawIndeterminateState(canvas: Canvas) {
        if (_indeterminateBgDrawable != null) {
            _indeterminateBgDrawable?.run {
                setBounds(0, 0, width, height)
                draw(canvas)
            }
        } else {
            _bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            _bgPaint.color = _indeterminateBgColor
            canvas.drawOval(_bgRect, _bgPaint)
        }
        if (_cancelable) {
            drawDrawableInCenter(_cancelIcon, canvas, _cancelIconWidth, _cancelIconHeight)
        }
        setProgressRectBounds()
        _progressPaint.color = _progressIndeterminateColor
        canvas.drawArc(_progressRect, _currIndeterminateBarPos.toFloat(), _progressIndeterminateSweepAngle.toFloat(), false, _progressPaint)
    }

    private fun drawDeterminateState(canvas: Canvas) {
        if (_determinateBgDrawable != null) {
            _determinateBgDrawable?.run {
                setBounds(0, 0, width, height)
                draw(canvas)
            }
        } else {
            _bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            _bgPaint.color = _determinateBgColor
            canvas.drawOval(_bgRect, _bgPaint)
        }
        if (!_showProgressText && _cancelable) {
            drawDrawableInCenter(_cancelIcon, canvas, _cancelIconWidth, _cancelIconHeight)
        }
        setProgressRectBounds()
        _progressPaint.color = _progressDeterminateColor
        canvas.drawArc(_progressRect, BASE_START_ANGLE.toFloat(), getDegrees(), false, _progressPaint)
        if (_showProgressText) setProgressText(canvas)
    }

    private fun setProgressText(canvas: Canvas) {
        _progressTextPaint.color = _progressTextColor
        _progressTextPaint.textSize = _progressTextSize.toFloat()
        val baseLineY: Float = abs(_progressTextPaint.ascent() + _progressTextPaint.descent()) / 2
        canvas.drawText("$_currProgress%", width.toFloat() / 2, height.toFloat() / 2 + baseLineY, _progressTextPaint)
    }

    override fun onDraw(canvas: Canvas) {
        when (currState) {
            STATE_IDLE -> drawIdleState(canvas)
            STATE_INDETERMINATE -> drawIndeterminateState(canvas)
            STATE_DETERMINATE -> drawDeterminateState(canvas)
            STATE_FINISHED -> drawFinishState(canvas)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(INSTANCE_STATE, super.onSaveInstanceState())
        bundle.putInt(INSTANCE_MAX_PROGRESS, maxProgress)
        bundle.putInt(INSTANCE_CURRENT_PROGRESS, currentProgress)
        bundle.putInt(INSTANCE_CURRENT_STATE, currState)
        bundle.putBoolean(INSTANCE_CANCELABLE, isCancelable)
        bundle.putBoolean(INSTANCE_ENABLE_CLICK, _enableClickListener)
        bundle.putInt(INSTANCE_IDLE_WIDTH, idleIconWidth)
        bundle.putInt(INSTANCE_IDLE_HEIGHT, idleIconHeight)
        bundle.putInt(INSTANCE_CANCEL_WIDTH, cancelIconWidth)
        bundle.putInt(INSTANCE_CANCEL_HEIGHT, cancelIconHeight)
        bundle.putInt(INSTANCE_FINISH_WIDTH, finishIconWidth)
        bundle.putInt(INSTANCE_FINISH_HEIGHT, finishIconHeight)
        bundle.putInt(INSTANCE_IDLE_BG_COLOR, idleBgColor)
        bundle.putInt(INSTANCE_FINISH_BG_COLOR, finishBgColor)
        bundle.putInt(INSTANCE_INDETERMINATE_BG_COLOR, indeterminateBgColor)
        bundle.putInt(INSTANCE_DETERMINATE_BG_COLOR, determinateBgColor)
        bundle.putInt(INSTANCE_PROGRESS_DETERMINATE_COLOR, progressDeterminateColor)
        bundle.putInt(INSTANCE_PROGRESS_INDETERMINATE_COLOR, progressIndeterminateColor)
        bundle.putInt(INSTANCE_PROGRESS_MARGIN, progressMargin)
        bundle.putBoolean(INSTANCE_SHOW_PROGRESS_TEXT, showProgressText)
        bundle.putInt(INSTANCE_PROGRESS_TEXT_COLOR, progressTextColor)
        bundle.putInt(INSTANCE_PROGRESS_TEXT_SIZE, progressTextSize)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            _maxProgress = state.getInt(INSTANCE_MAX_PROGRESS)
            _currProgress = state.getInt(INSTANCE_CURRENT_PROGRESS)
            currState = state.getInt(INSTANCE_CURRENT_STATE)
            _cancelable = state.getBoolean(INSTANCE_CANCELABLE)
            _enableClickListener = state.getBoolean(INSTANCE_ENABLE_CLICK)
            _idleIconWidth = state.getInt(INSTANCE_IDLE_WIDTH)
            _idleIconHeight = state.getInt(INSTANCE_IDLE_HEIGHT)
            _cancelIconWidth = state.getInt(INSTANCE_CANCEL_WIDTH)
            _cancelIconHeight = state.getInt(INSTANCE_CANCEL_HEIGHT)
            _finishIconWidth = state.getInt(INSTANCE_FINISH_WIDTH)
            _finishIconHeight = state.getInt(INSTANCE_FINISH_HEIGHT)
            _idleBgColor = state.getInt(INSTANCE_IDLE_BG_COLOR)
            _finishBgColor = state.getInt(INSTANCE_FINISH_BG_COLOR)
            _indeterminateBgColor = state.getInt(INSTANCE_INDETERMINATE_BG_COLOR)
            _determinateBgColor = state.getInt(INSTANCE_DETERMINATE_BG_COLOR)
            _progressDeterminateColor = state.getInt(INSTANCE_PROGRESS_DETERMINATE_COLOR)
            _progressIndeterminateColor = state.getInt(INSTANCE_PROGRESS_INDETERMINATE_COLOR)
            _progressMargin = state.getInt(INSTANCE_PROGRESS_MARGIN)
            _showProgressText = state.getBoolean(INSTANCE_SHOW_PROGRESS_TEXT)
            _progressTextColor = state.getInt(INSTANCE_PROGRESS_TEXT_COLOR)
            _progressTextSize = state.getInt(INSTANCE_PROGRESS_TEXT_SIZE)
            super.onRestoreInstanceState(state.getParcelable(INSTANCE_STATE))
            if (currState == STATE_INDETERMINATE) _indeterminateAnimator.start()
            return
        }
        super.onRestoreInstanceState(state)
    }

    private fun setProgressRectBounds() {
        val halfStroke = _progressPaint.strokeWidth / 2.0f
        val totalMargin = _progressMargin + halfStroke
        _progressRect.set(totalMargin, totalMargin, width - totalMargin, height - totalMargin)
    }

    private fun initIndeterminateAnimator() {
        _indeterminateAnimator = ValueAnimator.ofInt(0, 360).apply {
            interpolator = LinearInterpolator()
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                _currIndeterminateBarPos = value - BASE_START_ANGLE
                invalidate()
            }
        }
    }

    private fun getDegrees(): Float = _currProgress.toFloat() / _maxProgress.toFloat() * 360

    private fun drawDrawableInCenter(drawable: Drawable, canvas: Canvas, width: Int, height: Int) {
        val left = getWidth() / 2 - width / 2
        val top = getHeight() / 2 - height / 2
        drawable.setBounds(left, top, left + width, top + height)
        drawable.draw(canvas)
    }

    interface OnClickListener {
        fun onIdleButtonClick(view: View)
        fun onCancelButtonClick(view: View)
        fun onFinishButtonClick(view: View)
    }

    interface OnStateChangedListener {
        fun onStateChanged(newState: Int)
    }

    companion object {
        const val STATE_IDLE = 1
        const val STATE_INDETERMINATE = 2
        const val STATE_DETERMINATE = 3
        const val STATE_FINISHED = 4

        private const val INSTANCE_STATE = "saved_instance"
        private const val INSTANCE_MAX_PROGRESS = "max_progress"
        private const val INSTANCE_CURRENT_PROGRESS = "current_progress"
        private const val INSTANCE_CURRENT_STATE = "current_state"
        private const val INSTANCE_CANCELABLE = "cancelable"
        private const val INSTANCE_ENABLE_CLICK = "enable_click"
        private const val INSTANCE_IDLE_WIDTH = "idle_width"
        private const val INSTANCE_IDLE_HEIGHT = "idle_height"
        private const val INSTANCE_CANCEL_WIDTH = "cancel_width"
        private const val INSTANCE_CANCEL_HEIGHT = "cancel_height"
        private const val INSTANCE_FINISH_WIDTH = "finish_width"
        private const val INSTANCE_FINISH_HEIGHT = "finish_height"
        private const val INSTANCE_IDLE_BG_COLOR = "idle_bg_color"
        private const val INSTANCE_FINISH_BG_COLOR = "finish_bg_color"
        private const val INSTANCE_INDETERMINATE_BG_COLOR = "indeterminate_bg_color"
        private const val INSTANCE_DETERMINATE_BG_COLOR = "determinate_bg_color"
        private const val INSTANCE_PROGRESS_DETERMINATE_COLOR = "prog_det_color"
        private const val INSTANCE_PROGRESS_INDETERMINATE_COLOR = "prog_indet_color"
        private const val INSTANCE_PROGRESS_MARGIN = "prog_margin"
        private const val INSTANCE_SHOW_PROGRESS_TEXT = "show_prog_text"
        private const val INSTANCE_PROGRESS_TEXT_COLOR = "prog_text_color"
        private const val INSTANCE_PROGRESS_TEXT_SIZE = "prog_text_size"
        private const val BASE_START_ANGLE = -90
        private const val DEF_BG_COLOR = 0x4c000000
        private const val DEF_CANCELABLE = true
        private const val DEF_ENABLE_CLICK_LISTENER = true
        private const val DEF_DETERMINATE_COLOR = Color.GREEN
        private const val DEF_INDETERMINATE_COLOR = Color.WHITE
        private const val DEF_PROGRESS_WIDTH = 8
        private const val DEF_PROGRESS_MARGIN = 5
        private const val DEF_PROGRESS_INDETERMINATE_WIDTH = 90
        private const val DEF_SHOW_PROGRESS_TEXT = false
        private const val DEF_PROGRESS_TEXT_SIZE_IN_SP = 14
        private const val DEF_PROGRESS_TEXT_COLOR = Color.GREEN
    }
}

fun sp2px(spValue: Float, ctx: Context? = null): Int {
    return if (ctx == null) {
        val fontScale: Float = Resources.getSystem().displayMetrics.scaledDensity
        (spValue * fontScale + 0.5f).toInt()
    } else {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spValue, ctx.resources.displayMetrics).toInt()
    }
}