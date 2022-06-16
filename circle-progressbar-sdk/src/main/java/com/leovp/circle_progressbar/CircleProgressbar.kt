@file:Suppress("MemberVisibilityCanBePrivate")

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
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import com.leovp.circle_progressbar.base.State
import com.leovp.circle_progressbar.state.CancelState
import com.leovp.circle_progressbar.state.ErrorState
import com.leovp.circle_progressbar.state.FinishState
import com.leovp.circle_progressbar.state.IdleState
import com.leovp.circle_progressbar.util.dp2px
import com.leovp.circle_progressbar.util.getParcelableOrNull
import com.leovp.circle_progressbar.util.getSerializableOrNull
import com.leovp.circle_progressbar.util.sp2px
import kotlin.math.abs
import kotlin.math.min

/**
 * This class is copied from [AndroidButtonProgress](https://github.com/abdularis/AndroidButtonProgress)
 * and make some small changes.
 *
 * This view class shows 5 different view in different state
 * 1. Idle state show a button (download button in this case, but not limited)
 * 2. Indeterminate state show indeterminate circular progress, with optional button in the center
 * 3. Determinate state show determinate progress, with optional button in the center
 * 4. Finish state show finish drawable or hide this view
 * 5. Error state show error drawable or hide this view
 *
 * You can use this view to make a download or upload button, you might also use this for another purpose.
 */
@Suppress("unused", "WeakerAccess")
class CircleProgressbar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    var idleItem: IdleState
    var finishItem: FinishState
    var errorItem: ErrorState
    var cancelItem: CancelState

    private var _cancelable = DEF_CANCELABLE
    private var _enableClickListener = DEF_ENABLE_CLICK_LISTENER

    var currState = State.Type.STATE_IDLE
        private set

    private var _maxProgress = 100
    private var _currProgress = 0

    private var _progressAnimDuration: Long = DEF_PROGRESS_ANIM_DURATION
    private var _currIndeterminateBarPos = 0
    private var _progressIndeterminateSweepAngle = DEF_PROGRESS_INDETERMINATE_SWEEP_ANGLE_IN_DEGREE
    private var _progressColor = DEF_PROGRESS_COLOR
    private var _progressMargin: Int = Resources.getSystem().dp2px(DEF_PROGRESS_MARGIN_IN_DP)

    private var _defaultBgColor = State.DEF_BG_COLOR
    private var _defaultBgDrawable: Drawable? = null

    private lateinit var _indeterminateAnimator: ValueAnimator
    private val _bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val _bgRect = RectF()
    private val _progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val _progressRect = RectF()
    private val _clickListeners: MutableList<OnClickListener> = ArrayList()
    private val _onStateChangedListeners: MutableList<OnStateChangedListener> = ArrayList()

    private val _progressTextPaint = Paint()
    private var _showProgressText = DEF_SHOW_PROGRESS_TEXT
    private var _progressTextColor = DEF_PROGRESS_TEXT_COLOR
    private var _progressTextSize: Int = resources.sp2px(DEF_PROGRESS_TEXT_SIZE_IN_SP.toFloat())

    init {
        _progressPaint.style = Paint.Style.STROKE
        _progressPaint.isDither = true
        _progressPaint.strokeJoin = Paint.Join.ROUND
        _progressPaint.strokeCap = Paint.Cap.ROUND
        _progressPaint.pathEffect = CornerPathEffect(50f)

        _progressTextPaint.style = Paint.Style.FILL
        _progressTextPaint.textAlign = Paint.Align.CENTER

        val attr: TypedArray? = if (attrs != null) context.obtainStyledAttributes(attrs,
            R.styleable.CircleProgressbar,
            0,
            0) else null
        if (attrs != null && attr != null) {
            val bgResId = attr.getResourceId(R.styleable.CircleProgressbar_backgroundDrawable, -1)
            if (bgResId != -1) _defaultBgDrawable = context.getDrawable(bgResId)
            _defaultBgColor =
                    attr.getColor(R.styleable.CircleProgressbar_backgroundColor, State.DEF_BG_COLOR)

            currState = State.Type.getState(attr.getInt(R.styleable.CircleProgressbar_state,
                State.Type.STATE_IDLE.value))
            _cancelable = attr.getBoolean(R.styleable.CircleProgressbar_cancelable, DEF_CANCELABLE)
            _enableClickListener =
                    attr.getBoolean(R.styleable.CircleProgressbar_enableClickListener,
                        DEF_ENABLE_CLICK_LISTENER)
            _progressIndeterminateSweepAngle =
                    attr.getInteger(R.styleable.CircleProgressbar_progressIndeterminateSweepAngle,
                        DEF_PROGRESS_INDETERMINATE_SWEEP_ANGLE_IN_DEGREE)
            _progressColor =
                    attr.getColor(R.styleable.CircleProgressbar_progressColor, DEF_PROGRESS_COLOR)
            _progressPaint.strokeWidth =
                    attr.getDimensionPixelSize(R.styleable.CircleProgressbar_progressWidth,
                        resources.dp2px(DEF_PROGRESS_WIDTH_IN_DP)).toFloat()
            _progressMargin =
                    attr.getDimensionPixelSize(R.styleable.CircleProgressbar_progressMargin,
                        resources.dp2px(DEF_PROGRESS_MARGIN_IN_DP))
            _currProgress = attr.getInteger(R.styleable.CircleProgressbar_progress, 0)
            _maxProgress = attr.getInteger(R.styleable.CircleProgressbar_maxProgress, 100)
            _progressAnimDuration =
                    attr.getInteger(R.styleable.CircleProgressbar_progressAnimDuration, 1000)
                        .toLong()

            _showProgressText = attr.getBoolean(R.styleable.CircleProgressbar_showProgressText,
                DEF_SHOW_PROGRESS_TEXT)
            _progressTextColor = attr.getColor(R.styleable.CircleProgressbar_progressTextColor,
                DEF_PROGRESS_TEXT_COLOR)
            _progressTextSize =
                    attr.getDimensionPixelSize(R.styleable.CircleProgressbar_progressTextSize,
                        resources.sp2px(DEF_PROGRESS_TEXT_SIZE_IN_SP.toFloat()))
        } else {
            _progressPaint.strokeWidth = resources.dp2px(DEF_PROGRESS_WIDTH_IN_DP)

            //            _progressTextPaint.color = DEF_PROGRESS_TEXT_COLOR
            //            _progressTextPaint.textSize = DEF_PROGRESS_TEXT_SIZE.toFloat()
        }

        idleItem = IdleState(this).apply {
            setAttributes(context, attrs, attr, _defaultBgColor, _defaultBgDrawable)
        }
        finishItem = FinishState(this).apply {
            setAttributes(context, attrs, attr, _defaultBgColor, _defaultBgDrawable)
        }
        errorItem = ErrorState(this).apply {
            setAttributes(context, attrs, attr, _defaultBgColor, _defaultBgDrawable)
        }
        cancelItem = CancelState(this).apply {
            setAttributes(context, attrs, attr, _defaultBgColor, _defaultBgDrawable)
        }

        attr?.recycle()

        initIndeterminateAnimator()
        if (currState == State.Type.STATE_INDETERMINATE) setIndeterminate()
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
            if (currState != State.Type.STATE_DETERMINATE) return
            _currProgress = min(progress, _maxProgress)
            invalidate()
        }
    var progressAnimDuration: Long
        get() = _progressAnimDuration
        set(progressAnimDuration) {
            _progressAnimDuration = progressAnimDuration
            invalidate()
        }
    var isCancelable: Boolean
        get() = _cancelable
        set(cancelable) {
            _cancelable = cancelable
            invalidate()
        }
    var defaultBackgroundColor: Int
        get() = _defaultBgColor
        set(defaultBackgroundColor) {
            _defaultBgColor = defaultBackgroundColor
            invalidate()
        }
    var defaultBackgroundDrawable: Drawable?
        get() = _defaultBgDrawable
        set(defaultBackgroundDrawable) {
            _defaultBgDrawable = defaultBackgroundDrawable
            invalidate()
        }
    var progressColor: Int
        get() = _progressColor
        set(progressColor) {
            _progressColor = progressColor
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
        currState = State.Type.STATE_IDLE
        callStateChangedListener(currState)
        invalidate()
    }

    fun setIndeterminate() {
        _indeterminateAnimator.end()
        _currIndeterminateBarPos = BASE_START_ANGLE
        currState = State.Type.STATE_INDETERMINATE
        _indeterminateAnimator.start()
        callStateChangedListener(currState)
        invalidate()
    }

    fun setDeterminate() {
        _indeterminateAnimator.end()
        _currProgress = 0
        currState = State.Type.STATE_DETERMINATE
        callStateChangedListener(currState)
        invalidate()
    }

    fun setFinish() {
        _currProgress = 0
        currState = State.Type.STATE_FINISHED
        callStateChangedListener(currState)
        invalidate()
    }

    fun setError() {
        _currProgress = 0
        currState = State.Type.STATE_ERROR
        callStateChangedListener(currState)
        invalidate()
    }

    fun addOnClickListener(listener: OnClickListener): Boolean =
            if (!_clickListeners.contains(listener)) _clickListeners.add(listener) else false

    fun removeOnClickListener(listener: OnClickListener): Boolean = _clickListeners.remove(listener)

    /**
     * If you also set `View#setOnClickListener`,
     * that listener will be triggered first then your custom `setOnClickListener` will be triggered after that.
     */
    fun setOnClickListener(listener: OnClickListener) {
        removeAllOnClickListeners()
        addOnClickListener(listener)
    }

    fun removeAllOnClickListeners() = _clickListeners.clear()

    fun addOnStateChangedListeners(listener: OnStateChangedListener): Boolean =
            if (!_onStateChangedListeners.contains(listener)) _onStateChangedListeners.add(listener) else false

    fun removeOnStateChangedListener(listener: OnStateChangedListener): Boolean =
            _onStateChangedListeners.remove(listener)

    private fun callStateChangedListener(newState: State.Type) {
        for (listener in _onStateChangedListeners) listener.onStateChanged(newState)
    }

    // https://stackoverflow.com/a/50343572
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!_enableClickListener) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP   -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // https://stackoverflow.com/a/50343572
    override fun performClick(): Boolean {
        super.performClick() // View.setOnClickListener will be called here.
        onCustomClick(this)
        return true
    }

    private fun onCustomClick(v: View) {
        if (!_cancelable && (currState == State.Type.STATE_INDETERMINATE || currState == State.Type.STATE_DETERMINATE)) return
        when (currState) {
            State.Type.STATE_IDLE                                        -> for (listener in _clickListeners) listener.onIdleButtonClick(
                v)
            State.Type.STATE_INDETERMINATE, State.Type.STATE_DETERMINATE -> for (listener in _clickListeners) listener.onCancelButtonClick(
                v)
            State.Type.STATE_FINISHED                                    -> for (listener in _clickListeners) listener.onFinishButtonClick(
                v)
            State.Type.STATE_ERROR                                       -> for (listener in _clickListeners) listener.onErrorButtonClick(
                v)
            else                                                         -> Unit
        }
    }

    private fun setBgDrawable(canvas: Canvas, bgDrawable: Drawable?, @ColorInt bgColor: Int) {
        if (bgDrawable != null) {
            bgDrawable.setBounds(0, 0, width, height)
            bgDrawable.draw(canvas)
        } else {
            _bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            _bgPaint.color = bgColor
            canvas.drawOval(_bgRect, _bgPaint)
        }
    }

    private fun drawStaticState(canvas: Canvas, state: State) {
        val bgDrawable: Drawable? =
                if (_defaultBgDrawable != state.backgroundDrawable) state.backgroundDrawable else _defaultBgDrawable
        setBgDrawable(canvas,
            bgDrawable,
            if (_defaultBgColor != state.backgroundColor) state.backgroundColor else _defaultBgColor)
        state.getIcon().setTint(state.iconTint)
        drawDrawableInCenter(state.getIcon(), canvas, state.width, state.height)
    }

    private fun drawActionState(canvas: Canvas,
        showProgressText: Boolean,
        startAngle: Float,
        sweepAngle: Float) {
        if (State.Type.STATE_INDETERMINATE != currState && State.Type.STATE_DETERMINATE != currState) throw IllegalArgumentException(
            "Illegal state. Current state=$currState")
        if (_cancelable) {
            setBgDrawable(canvas, cancelItem.backgroundDrawable, cancelItem.backgroundColor)
        } else {
            setBgDrawable(canvas, _defaultBgDrawable, _defaultBgColor)
        }
        if (!showProgressText && _cancelable) {
            cancelItem.getIcon().setTint(cancelItem.iconTint)
            drawDrawableInCenter(cancelItem.getIcon(), canvas, cancelItem.width, cancelItem.height)
        }
        setProgressRectBounds()
        _progressPaint.color = _progressColor
        canvas.drawArc(_progressRect, startAngle, sweepAngle, false, _progressPaint)
        if (showProgressText) setProgressText(canvas)
    }

    private fun setProgressText(canvas: Canvas) {
        _progressTextPaint.color = _progressTextColor
        _progressTextPaint.textSize = _progressTextSize.toFloat()
        val baseLineY: Float = abs(_progressTextPaint.ascent() + _progressTextPaint.descent()) / 2
        canvas.drawText("$_currProgress%",
            width.toFloat() / 2,
            height.toFloat() / 2 + baseLineY,
            _progressTextPaint)
    }

    override fun onDraw(canvas: Canvas) {
        when (currState) {
            State.Type.STATE_IDLE          -> drawStaticState(canvas, idleItem)
            State.Type.STATE_INDETERMINATE -> drawActionState(canvas,
                false,
                _currIndeterminateBarPos.toFloat(),
                _progressIndeterminateSweepAngle.toFloat())
            State.Type.STATE_DETERMINATE   -> drawActionState(canvas,
                _showProgressText,
                BASE_START_ANGLE.toFloat(),
                getDegrees())
            State.Type.STATE_FINISHED      -> drawStaticState(canvas, finishItem)
            State.Type.STATE_ERROR         -> drawStaticState(canvas, errorItem)
            else                           -> Unit
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable(INSTANCE_STATE, super.onSaveInstanceState())
            //            putSerializable(INSTANCE_IDLE_ITEM, idleItem)
            //            putSerializable(INSTANCE_FINISH_ITEM, finishItem)
            //            putSerializable(INSTANCE_ERROR_ITEM, errorItem)
            //            putSerializable(INSTANCE_CANCEL_ITEM, cancelItem)
            putInt(INSTANCE_MAX_PROGRESS, maxProgress)
            putInt(INSTANCE_CURRENT_PROGRESS, currentProgress)
            putSerializable(INSTANCE_CURRENT_STATE, currState)
            putBoolean(INSTANCE_CANCELABLE, isCancelable)
            putBoolean(INSTANCE_ENABLE_CLICK, _enableClickListener)
            putInt(INSTANCE_BG_COLOR, defaultBackgroundColor)
            putInt(INSTANCE_PROGRESS_COLOR, progressColor)
            putInt(INSTANCE_PROGRESS_MARGIN, progressMargin)
            putBoolean(INSTANCE_SHOW_PROGRESS_TEXT, showProgressText)
            putInt(INSTANCE_PROGRESS_TEXT_COLOR, progressTextColor)
            putInt(INSTANCE_PROGRESS_TEXT_SIZE, progressTextSize)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            _maxProgress = state.getInt(INSTANCE_MAX_PROGRESS)
            _currProgress = state.getInt(INSTANCE_CURRENT_PROGRESS)
            currState = state.getSerializableOrNull(INSTANCE_CURRENT_STATE)!!
            _cancelable = state.getBoolean(INSTANCE_CANCELABLE)
            _enableClickListener = state.getBoolean(INSTANCE_ENABLE_CLICK)
            //            idleItem = state.getSerializable(INSTANCE_IDLE_ITEM) as IdleState
            //            finishItem = state.getSerializable(INSTANCE_FINISH_ITEM) as FinishState
            //            errorItem = state.getSerializable(INSTANCE_ERROR_ITEM) as ErrorState
            //            cancelItem = state.getSerializable(INSTANCE_CANCEL_ITEM) as CancelState
            _defaultBgColor = state.getInt(INSTANCE_BG_COLOR)
            _progressColor = state.getInt(INSTANCE_PROGRESS_COLOR)
            _progressMargin = state.getInt(INSTANCE_PROGRESS_MARGIN)
            _showProgressText = state.getBoolean(INSTANCE_SHOW_PROGRESS_TEXT)
            _progressTextColor = state.getInt(INSTANCE_PROGRESS_TEXT_COLOR)
            _progressTextSize = state.getInt(INSTANCE_PROGRESS_TEXT_SIZE)

            val instanceState: Parcelable? = state.getParcelableOrNull(INSTANCE_STATE)

            super.onRestoreInstanceState(instanceState)
            if (currState == State.Type.STATE_INDETERMINATE) _indeterminateAnimator.start()
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
            duration = _progressAnimDuration
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
        fun onErrorButtonClick(view: View)
    }

    interface OnStateChangedListener {
        fun onStateChanged(newState: State.Type)
    }

    companion object {
        //        private const val INSTANCE_IDLE_ITEM = "idle_item"
        //        private const val INSTANCE_FINISH_ITEM = "idle_finish"
        //        private const val INSTANCE_ERROR_ITEM = "idle_error"
        //        private const val INSTANCE_CANCEL_ITEM = "idle_cancel"
        private const val INSTANCE_STATE = "saved_instance"
        private const val INSTANCE_MAX_PROGRESS = "max_progress"
        private const val INSTANCE_CURRENT_PROGRESS = "current_progress"
        private const val INSTANCE_CURRENT_STATE = "current_state"
        private const val INSTANCE_CANCELABLE = "cancelable"
        private const val INSTANCE_ENABLE_CLICK = "enable_click"
        private const val INSTANCE_BG_COLOR = "def_bg_color"
        private const val INSTANCE_PROGRESS_COLOR = "prog_color"
        private const val INSTANCE_PROGRESS_MARGIN = "prog_margin"
        private const val INSTANCE_SHOW_PROGRESS_TEXT = "show_prog_text"
        private const val INSTANCE_PROGRESS_TEXT_COLOR = "prog_text_color"
        private const val INSTANCE_PROGRESS_TEXT_SIZE = "prog_text_size"
        private const val BASE_START_ANGLE = -90
        private const val DEF_CANCELABLE = true
        private const val DEF_ENABLE_CLICK_LISTENER = true
        private const val DEF_PROGRESS_COLOR = Color.WHITE
        private const val DEF_PROGRESS_ANIM_DURATION: Long = 1000
        private const val DEF_PROGRESS_WIDTH_IN_DP = 3f
        private const val DEF_PROGRESS_MARGIN_IN_DP = 2f
        private const val DEF_PROGRESS_INDETERMINATE_SWEEP_ANGLE_IN_DEGREE = 90
        private const val DEF_SHOW_PROGRESS_TEXT = false
        private const val DEF_PROGRESS_TEXT_SIZE_IN_SP = 14
        private const val DEF_PROGRESS_TEXT_COLOR = Color.WHITE
    }
}