@file:Suppress("MemberVisibilityCanBePrivate")

package com.leovp.circleprogressbar

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import com.leovp.circleprogressbar.base.State
import com.leovp.circleprogressbar.state.CancelState
import com.leovp.circleprogressbar.state.ErrorState
import com.leovp.circleprogressbar.state.FinishState
import com.leovp.circleprogressbar.state.IdleState
import com.leovp.circleprogressbar.util.dp2px
import com.leovp.circleprogressbar.util.getParcelableOrNull
import com.leovp.circleprogressbar.util.getSerializableOrNull
import com.leovp.circleprogressbar.util.sp2px
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

    private var internalCancelable = DEF_CANCELABLE
    private var internalEnableClickListener = DEF_ENABLE_CLICK_LISTENER

    var currState = State.Type.STATE_IDLE
        private set

    private var internalMaxProgress = 100
    private var internalCurrProgress = 0

    private var internalProgressAnimDuration: Long = DEF_PROGRESS_ANIM_DURATION
    private var internalCrrIndeterminateBarPos = 0
    private var internalProgressIndeterminateSweepAngle = DEF_PROGRESS_INDETERMINATE_SWEEP_ANGLE_IN_DEGREE
    private var internalProgressColor = DEF_PROGRESS_COLOR
    private var internalProgressMargin: Int = Resources.getSystem().dp2px(DEF_PROGRESS_MARGIN_IN_DP)

    private var internalDefaultBgColor = State.DEF_BG_COLOR
    private var internalDefaultBgDrawable: Drawable? = null

    private lateinit var internalIndeterminateAnimator: ValueAnimator
    private val internalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val internalBgRect = RectF()
    private val internalProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val internalProgressRect = RectF()
    private val internalClickListeners: MutableList<OnClickListener> = ArrayList()
    private val internalOnStateChangedListeners: MutableList<OnStateChangedListener> = ArrayList()

    private val internalProgressTextPaint = Paint()
    private var internalShowProgressText = DEF_SHOW_PROGRESS_TEXT
    private var internalProgressTextColor = DEF_PROGRESS_TEXT_COLOR
    private var internalProgressTextSize: Int = resources.sp2px(DEF_PROGRESS_TEXT_SIZE_IN_SP.toFloat())

    init {
        internalProgressPaint.style = Paint.Style.STROKE
        internalProgressPaint.isDither = true
        internalProgressPaint.strokeJoin = Paint.Join.ROUND
        internalProgressPaint.strokeCap = Paint.Cap.ROUND
        internalProgressPaint.pathEffect = CornerPathEffect(50f)

        internalProgressTextPaint.style = Paint.Style.FILL
        internalProgressTextPaint.textAlign = Paint.Align.CENTER

        val attr: TypedArray? = if (attrs != null) {
            context.obtainStyledAttributes(
                attrs,
                R.styleable.CircleProgressbar,
                0,
                0
            )
        } else {
            null
        }
        if (attrs != null && attr != null) {
            val bgResId = attr.getResourceId(R.styleable.CircleProgressbar_backgroundDrawable, -1)
            if (bgResId != -1) internalDefaultBgDrawable = context.getDrawable(bgResId)
            internalDefaultBgColor =
                attr.getColor(R.styleable.CircleProgressbar_backgroundColor, State.DEF_BG_COLOR)

            currState = State.Type.getState(
                attr.getInt(
                    R.styleable.CircleProgressbar_state,
                    State.Type.STATE_IDLE.value
                )
            )
            internalCancelable = attr.getBoolean(R.styleable.CircleProgressbar_cancelable, DEF_CANCELABLE)
            internalEnableClickListener =
                attr.getBoolean(
                    R.styleable.CircleProgressbar_enableClickListener,
                    DEF_ENABLE_CLICK_LISTENER
                )
            internalProgressIndeterminateSweepAngle =
                attr.getInteger(
                    R.styleable.CircleProgressbar_progressIndeterminateSweepAngle,
                    DEF_PROGRESS_INDETERMINATE_SWEEP_ANGLE_IN_DEGREE
                )
            internalProgressColor =
                attr.getColor(R.styleable.CircleProgressbar_progressColor, DEF_PROGRESS_COLOR)
            internalProgressPaint.strokeWidth =
                attr.getDimensionPixelSize(
                    R.styleable.CircleProgressbar_progressWidth,
                    resources.dp2px(DEF_PROGRESS_WIDTH_IN_DP)
                ).toFloat()
            internalProgressMargin =
                attr.getDimensionPixelSize(
                    R.styleable.CircleProgressbar_progressMargin,
                    resources.dp2px(DEF_PROGRESS_MARGIN_IN_DP)
                )
            internalCurrProgress = attr.getInteger(R.styleable.CircleProgressbar_progress, 0)
            internalMaxProgress = attr.getInteger(R.styleable.CircleProgressbar_maxProgress, 100)
            internalProgressAnimDuration =
                attr.getInteger(R.styleable.CircleProgressbar_progressAnimDuration, 1000)
                    .toLong()

            internalShowProgressText = attr.getBoolean(
                R.styleable.CircleProgressbar_showProgressText,
                DEF_SHOW_PROGRESS_TEXT
            )
            internalProgressTextColor = attr.getColor(
                R.styleable.CircleProgressbar_progressTextColor,
                DEF_PROGRESS_TEXT_COLOR
            )
            internalProgressTextSize =
                attr.getDimensionPixelSize(
                    R.styleable.CircleProgressbar_progressTextSize,
                    resources.sp2px(DEF_PROGRESS_TEXT_SIZE_IN_SP.toFloat())
                )
        } else {
            internalProgressPaint.strokeWidth = resources.dp2px(DEF_PROGRESS_WIDTH_IN_DP)

            //            _progressTextPaint.color = DEF_PROGRESS_TEXT_COLOR
            //            _progressTextPaint.textSize = DEF_PROGRESS_TEXT_SIZE.toFloat()
        }

        idleItem = IdleState(this).apply {
            setAttributes(context, attrs, attr, internalDefaultBgColor, internalDefaultBgDrawable)
        }
        finishItem = FinishState(this).apply {
            setAttributes(context, attrs, attr, internalDefaultBgColor, internalDefaultBgDrawable)
        }
        errorItem = ErrorState(this).apply {
            setAttributes(context, attrs, attr, internalDefaultBgColor, internalDefaultBgDrawable)
        }
        cancelItem = CancelState(this).apply {
            setAttributes(context, attrs, attr, internalDefaultBgColor, internalDefaultBgDrawable)
        }

        attr?.recycle()

        initIndeterminateAnimator()
        if (currState == State.Type.STATE_INDETERMINATE) setIndeterminate()
    }

    var maxProgress: Int
        get() = internalMaxProgress
        set(maxProgress) {
            internalMaxProgress = maxProgress
            invalidate()
        }
    var currentProgress: Int
        get() = internalCurrProgress
        set(progress) {
            if (currState != State.Type.STATE_DETERMINATE) return
            internalCurrProgress = min(progress, internalMaxProgress)
            invalidate()
        }
    var progressAnimDuration: Long
        get() = internalProgressAnimDuration
        set(progressAnimDuration) {
            internalProgressAnimDuration = progressAnimDuration
            invalidate()
        }
    var isCancelable: Boolean
        get() = internalCancelable
        set(cancelable) {
            internalCancelable = cancelable
            invalidate()
        }
    var defaultBackgroundColor: Int
        get() = internalDefaultBgColor
        set(defaultBackgroundColor) {
            internalDefaultBgColor = defaultBackgroundColor
            invalidate()
        }
    var defaultBackgroundDrawable: Drawable?
        get() = internalDefaultBgDrawable
        set(defaultBackgroundDrawable) {
            internalDefaultBgDrawable = defaultBackgroundDrawable
            invalidate()
        }
    var progressColor: Int
        get() = internalProgressColor
        set(progressColor) {
            internalProgressColor = progressColor
            invalidate()
        }
    var progressMargin: Int
        get() = internalProgressMargin
        set(progressMargin) {
            internalProgressMargin = progressMargin
            invalidate()
        }
    var progressIndeterminateSweepAngle: Int
        get() = internalProgressIndeterminateSweepAngle
        set(progressIndeterminateSweepAngle) {
            internalProgressIndeterminateSweepAngle = progressIndeterminateSweepAngle
            invalidate()
        }
    var showProgressText: Boolean
        get() = internalShowProgressText
        set(showProgressText) {
            internalShowProgressText = showProgressText
            invalidate()
        }
    var progressTextColor: Int
        get() = internalProgressTextColor
        set(progressTextColor) {
            internalProgressTextColor = progressTextColor
            invalidate()
        }
    var progressTextSize: Int
        get() = internalProgressTextSize
        set(progressTextSize) {
            internalProgressTextSize = progressTextSize
            invalidate()
        }

    fun setIdle() {
        currState = State.Type.STATE_IDLE
        callStateChangedListener(currState)
        invalidate()
    }

    fun setIndeterminate() {
        internalIndeterminateAnimator.end()
        internalCrrIndeterminateBarPos = BASE_START_ANGLE
        currState = State.Type.STATE_INDETERMINATE
        internalIndeterminateAnimator.start()
        callStateChangedListener(currState)
        invalidate()
    }

    fun setDeterminate() {
        internalIndeterminateAnimator.end()
        internalCurrProgress = 0
        currState = State.Type.STATE_DETERMINATE
        callStateChangedListener(currState)
        invalidate()
    }

    fun setFinish() {
        internalCurrProgress = 0
        currState = State.Type.STATE_FINISHED
        callStateChangedListener(currState)
        invalidate()
    }

    fun setError() {
        internalCurrProgress = 0
        currState = State.Type.STATE_ERROR
        callStateChangedListener(currState)
        invalidate()
    }

    fun addOnClickListener(listener: OnClickListener): Boolean =
        if (!internalClickListeners.contains(listener)) internalClickListeners.add(listener) else false

    fun removeOnClickListener(listener: OnClickListener): Boolean = internalClickListeners.remove(listener)

    /**
     * If you also set `View#setOnClickListener`,
     * that listener will be triggered first then your custom `setOnClickListener` will be triggered after that.
     */
    fun setOnClickListener(listener: OnClickListener) {
        removeAllOnClickListeners()
        addOnClickListener(listener)
    }

    fun removeAllOnClickListeners() = internalClickListeners.clear()

    fun addOnStateChangedListeners(listener: OnStateChangedListener): Boolean =
        if (!internalOnStateChangedListeners.contains(listener)) {
            internalOnStateChangedListeners.add(listener)
        } else {
            false
        }

    fun removeOnStateChangedListener(listener: OnStateChangedListener): Boolean =
        internalOnStateChangedListeners.remove(listener)

    private fun callStateChangedListener(newState: State.Type) {
        for (listener in internalOnStateChangedListeners) listener.onStateChanged(newState)
    }

    // https://stackoverflow.com/a/50343572
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!internalEnableClickListener) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
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
        if (!internalCancelable &&
            (
                currState == State.Type.STATE_INDETERMINATE ||
                    currState == State.Type.STATE_DETERMINATE
                )
        ) {
            return
        }
        when (currState) {
            State.Type.STATE_IDLE -> {
                for (listener in internalClickListeners) listener.onIdleButtonClick(v)
            }

            State.Type.STATE_INDETERMINATE, State.Type.STATE_DETERMINATE -> {
                for (listener in internalClickListeners) listener.onCancelButtonClick(v)
            }

            State.Type.STATE_FINISHED -> {
                for (listener in internalClickListeners) listener.onFinishButtonClick(v)
            }

            State.Type.STATE_ERROR -> {
                for (listener in internalClickListeners) listener.onErrorButtonClick(v)
            }

            else -> Unit
        }
    }

    private fun setBgDrawable(canvas: Canvas, bgDrawable: Drawable?, @ColorInt bgColor: Int) {
        if (bgDrawable != null) {
            bgDrawable.setBounds(0, 0, width, height)
            bgDrawable.draw(canvas)
        } else {
            internalBgRect.set(0f, 0f, width.toFloat(), height.toFloat())
            internalBgPaint.color = bgColor
            canvas.drawOval(internalBgRect, internalBgPaint)
        }
    }

    private fun drawStaticState(canvas: Canvas, state: State) {
        val bgDrawable: Drawable? =
            if (internalDefaultBgDrawable != state.backgroundDrawable) {
                state.backgroundDrawable
            } else {
                internalDefaultBgDrawable
            }
        setBgDrawable(
            canvas,
            bgDrawable,
            if (internalDefaultBgColor != state.backgroundColor) {
                state.backgroundColor
            } else {
                internalDefaultBgColor
            }
        )
        state.getIcon().setTint(state.iconTint)
        drawDrawableInCenter(state.getIcon(), canvas, state.width, state.height)
    }

    private fun drawActionState(canvas: Canvas, showProgressText: Boolean, startAngle: Float, sweepAngle: Float) {
        require(State.Type.STATE_INDETERMINATE == currState || State.Type.STATE_DETERMINATE == currState) {
            "Illegal state. Current state=$currState"
        }
        if (internalCancelable) {
            setBgDrawable(canvas, cancelItem.backgroundDrawable, cancelItem.backgroundColor)
        } else {
            setBgDrawable(canvas, internalDefaultBgDrawable, internalDefaultBgColor)
        }
        if (!showProgressText && internalCancelable) {
            cancelItem.getIcon().setTint(cancelItem.iconTint)
            drawDrawableInCenter(cancelItem.getIcon(), canvas, cancelItem.width, cancelItem.height)
        }
        setProgressRectBounds()
        internalProgressPaint.color = internalProgressColor
        canvas.drawArc(internalProgressRect, startAngle, sweepAngle, false, internalProgressPaint)
        if (showProgressText) setProgressText(canvas)
    }

    private fun setProgressText(canvas: Canvas) {
        internalProgressTextPaint.color = internalProgressTextColor
        internalProgressTextPaint.textSize = internalProgressTextSize.toFloat()
        val baseLineY: Float = abs(internalProgressTextPaint.ascent() + internalProgressTextPaint.descent()) / 2
        canvas.drawText(
            "$internalCurrProgress%",
            width.toFloat() / 2,
            height.toFloat() / 2 + baseLineY,
            internalProgressTextPaint
        )
    }

    override fun onDraw(canvas: Canvas) {
        when (currState) {
            State.Type.STATE_IDLE -> drawStaticState(canvas, idleItem)
            State.Type.STATE_INDETERMINATE -> drawActionState(
                canvas,
                false,
                internalCrrIndeterminateBarPos.toFloat(),
                internalProgressIndeterminateSweepAngle.toFloat()
            )

            State.Type.STATE_DETERMINATE -> drawActionState(
                canvas,
                internalShowProgressText,
                BASE_START_ANGLE.toFloat(),
                getDegrees()
            )

            State.Type.STATE_FINISHED -> drawStaticState(canvas, finishItem)
            State.Type.STATE_ERROR -> drawStaticState(canvas, errorItem)
            else -> Unit
        }
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(INSTANCE_STATE, super.onSaveInstanceState())
        //            putSerializable(INSTANCE_IDLE_ITEM, idleItem)
        //            putSerializable(INSTANCE_FINISH_ITEM, finishItem)
        //            putSerializable(INSTANCE_ERROR_ITEM, errorItem)
        //            putSerializable(INSTANCE_CANCEL_ITEM, cancelItem)
        putInt(INSTANCE_MAX_PROGRESS, maxProgress)
        putInt(INSTANCE_CURRENT_PROGRESS, currentProgress)
        putSerializable(INSTANCE_CURRENT_STATE, currState)
        putBoolean(INSTANCE_CANCELABLE, isCancelable)
        putBoolean(INSTANCE_ENABLE_CLICK, internalEnableClickListener)
        putInt(INSTANCE_BG_COLOR, defaultBackgroundColor)
        putInt(INSTANCE_PROGRESS_COLOR, progressColor)
        putInt(INSTANCE_PROGRESS_MARGIN, progressMargin)
        putBoolean(INSTANCE_SHOW_PROGRESS_TEXT, showProgressText)
        putInt(INSTANCE_PROGRESS_TEXT_COLOR, progressTextColor)
        putInt(INSTANCE_PROGRESS_TEXT_SIZE, progressTextSize)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            internalMaxProgress = state.getInt(INSTANCE_MAX_PROGRESS)
            internalCurrProgress = state.getInt(INSTANCE_CURRENT_PROGRESS)
            currState = state.getSerializableOrNull(INSTANCE_CURRENT_STATE)!!
            internalCancelable = state.getBoolean(INSTANCE_CANCELABLE)
            internalEnableClickListener = state.getBoolean(INSTANCE_ENABLE_CLICK)
            //            idleItem = state.getSerializable(INSTANCE_IDLE_ITEM) as IdleState
            //            finishItem = state.getSerializable(INSTANCE_FINISH_ITEM) as FinishState
            //            errorItem = state.getSerializable(INSTANCE_ERROR_ITEM) as ErrorState
            //            cancelItem = state.getSerializable(INSTANCE_CANCEL_ITEM) as CancelState
            internalDefaultBgColor = state.getInt(INSTANCE_BG_COLOR)
            internalProgressColor = state.getInt(INSTANCE_PROGRESS_COLOR)
            internalProgressMargin = state.getInt(INSTANCE_PROGRESS_MARGIN)
            internalShowProgressText = state.getBoolean(INSTANCE_SHOW_PROGRESS_TEXT)
            internalProgressTextColor = state.getInt(INSTANCE_PROGRESS_TEXT_COLOR)
            internalProgressTextSize = state.getInt(INSTANCE_PROGRESS_TEXT_SIZE)

            val instanceState: Parcelable? = state.getParcelableOrNull(INSTANCE_STATE)

            super.onRestoreInstanceState(instanceState)
            if (currState == State.Type.STATE_INDETERMINATE) internalIndeterminateAnimator.start()
            return
        }
        super.onRestoreInstanceState(state)
    }

    private fun setProgressRectBounds() {
        val halfStroke = internalProgressPaint.strokeWidth / 2.0f
        val totalMargin = internalProgressMargin + halfStroke
        internalProgressRect.set(totalMargin, totalMargin, width - totalMargin, height - totalMargin)
    }

    private fun initIndeterminateAnimator() {
        internalIndeterminateAnimator = ValueAnimator.ofInt(0, 360).apply {
            interpolator = LinearInterpolator()
            duration = internalProgressAnimDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                internalCrrIndeterminateBarPos = value - BASE_START_ANGLE
                invalidate()
            }
        }
    }

    private fun getDegrees(): Float = internalCurrProgress.toFloat() / internalMaxProgress.toFloat() * 360

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
