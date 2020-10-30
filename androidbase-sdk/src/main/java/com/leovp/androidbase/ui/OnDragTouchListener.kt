package com.leovp.androidbase.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import com.leovp.androidbase.utils.log.LogContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Author: Michael Leo
 * Date: 19-7-30 上午11:40
 */
class OnDragTouchListener(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val statusBarHeight: Int,
    private val gapHeightInPixel: Int,//标记是否开启自动拉到边缘功能
    private val isAutoPullToBorder: Boolean
) : OnTouchListener {

    //手指按下时的初始位置
    private var mOriginalX = 0f
    private var mOriginalY = 0f

    //记录手指与view的左上角的距离
    private var mDistanceX = 0f
    private var mDistanceY = 0f
    private var mControlLayerLayoutWidth = 0
    private var mControlLayerWidth = 0
    private var mControlLayerHeight = 0

    private var mLeft = 0
    private var mTop = 0
    private var mRight = 0
    private var mBottom = 0
    var onDraggableClickListener: OnDraggableClickListener? = null
    private var mFocusedCtlBtn: View? = null
    private var mIsDragging = false
    private var mRealLeftOffset = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mControlLayerWidth = v.width
                mControlLayerHeight = v.height
                mIsDragging = false
                mOriginalX = event.rawX
                mOriginalY = event.rawY
                mDistanceX = event.rawX - v.left
                mDistanceY = event.rawY - v.top
                val param = v.layoutParams as RelativeLayout.LayoutParams
                param.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                param.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                param.leftMargin = v.left
                param.topMargin = v.top
                param.bottomMargin = 0
                param.rightMargin = 0
                if (onDraggableClickListener != null) {
                    val childCount = (v as LinearLayout).childCount
                    var visibleBtnIndexList = ArrayList<Int>()
                    for (i in 0 until childCount) {
                        if (v.getChildAt(i).isVisible) {
                            visibleBtnIndexList.add(i)
                        }
                    }
                    if (visibleBtnIndexList.isEmpty()) {
                        return true
                    }
                    mFocusedCtlBtn = v.getChildAt(visibleBtnIndexList.get(0))
                    val eachBtnHeightInPixel = mFocusedCtlBtn?.height!!
                    if (0 <= mDistanceY && mDistanceY <= eachBtnHeightInPixel) {
                        mFocusedCtlBtn?.alpha = BTN_PRESSED_ALPHA
                        return true
                    }
                    var i = 1
                    while (i < visibleBtnIndexList.size) {
                        if ((eachBtnHeightInPixel + gapHeightInPixel) * i <= mDistanceY && mDistanceY <= (eachBtnHeightInPixel + gapHeightInPixel) * i + eachBtnHeightInPixel) {
                            mFocusedCtlBtn = v.getChildAt(visibleBtnIndexList.get(i))
                            mFocusedCtlBtn?.alpha = BTN_PRESSED_ALPHA
                            return true
                        }
                        i++
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                mControlLayerLayoutWidth = max(v.layoutParams.width, v.width)
                if (mControlLayerWidth > 0 && mControlLayerLayoutWidth > 0) {
                    mRealLeftOffset = abs(mControlLayerLayoutWidth - mControlLayerWidth)
                }
                mIsDragging = true
                mLeft = (event.rawX - mDistanceX).toInt()
                mTop = (event.rawY - mDistanceY).toInt()
                mRight = mLeft + mControlLayerLayoutWidth
                mBottom = mTop + mControlLayerHeight
                if (mLeft < 0) {
                    mLeft = 0
                    mRight = mLeft + mControlLayerLayoutWidth
                }
                if (mTop < statusBarHeight) {
                    mTop = statusBarHeight
                    mBottom = mTop + mControlLayerHeight
                }
                if (mRight > screenWidth) {
                    mRight = screenWidth
                    mLeft = screenWidth - mControlLayerLayoutWidth
                }
                if (mBottom > screenHeight) {
                    mBottom = screenHeight
                    mTop = screenHeight - mControlLayerHeight
                }
                if (abs(event.rawX - mOriginalX) >= MOVE_THRESHOLD || abs(event.rawY - mOriginalY) >= MOVE_THRESHOLD) {
                    val params = v.layoutParams as RelativeLayout.LayoutParams
                    params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    params.leftMargin = mLeft
                    params.topMargin = mTop
                    //                    params.bottomMargin = mBottom;
//                    params.rightMargin = mRight;
                    v.layout(mLeft, mTop, mRight, mBottom)
                    if (onDraggableClickListener != null) {
                        onDraggableClickListener!!.onDragging(v, mLeft, mTop, mRight, mBottom)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                mFocusedCtlBtn!!.alpha = BTN_NORMAL_ALPHA

                //如果移动距离过小，则判定为点击
                if (abs(event.rawX - mOriginalX) < MOVE_THRESHOLD && abs(event.rawY - mOriginalY) < MOVE_THRESHOLD) {
                    if (onDraggableClickListener != null) {
                        LogContext.log.i(TAG, "Focused Button=${mFocusedCtlBtn}")
                        onDraggableClickListener!!.onClick(mFocusedCtlBtn!!)
                    }
                } else {
                    //在拖动过按钮后，如果其他view刷新导致重绘，会让按钮重回原点，所以需要更改布局参数
                    val lp = v.layoutParams as MarginLayoutParams
                    startAutoPull(v, lp, mRealLeftOffset)
                }
            }
        }
        return true
    }

    /**
     * 开启自动拖拽
     *
     * @param v  拉动控件
     * @param lp 控件布局参数
     */
    private fun startAutoPull(v: View, lp: MarginLayoutParams, realLeftOffset: Int) {
        if (!isAutoPullToBorder) {
            v.layout(mLeft + realLeftOffset, mTop, mRight, mBottom)
            lp.setMargins(mLeft + realLeftOffset, mTop, 0, 0)
            v.layoutParams = lp
            if (onDraggableClickListener != null) {
                onDraggableClickListener!!.onDragged(v, lp, mLeft + realLeftOffset, mTop, mRight, mBottom)
            }
            return
        }
        //当用户拖拽完后，让控件根据远近距离回到最近的边缘
        var end = 0f
        if (mLeft + mControlLayerWidth / 2 >= screenWidth / 2) {
            end = screenWidth - mControlLayerWidth - realLeftOffset.toFloat()
        }
        val animator = ValueAnimator.ofFloat(mLeft.toFloat(), end)
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation: ValueAnimator ->
            mLeft = (animation.animatedValue as Float).toInt()
            mRight = mLeft + mControlLayerWidth
            v.layout(mLeft, mTop, mRight, mBottom)
            lp.setMargins(mLeft, mTop, 0, 0)
            v.layoutParams = lp
        }
        val finalEnd = end
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (onDraggableClickListener != null) {
                    onDraggableClickListener!!.onDragged(v, lp, finalEnd.toInt(), mTop, mRight, mBottom)
                }
            }
        })
        animator.duration = 400
        animator.start()
    }

    /**
     * 控件拖拽监听器
     */
    interface OnDraggableClickListener {
        //        default void onAfterActionUp(View v, int finalLeft, int finalTop) {
        //
        //        }
        /**
         * 当控件拖拽完后回调
         *
         * @param v    拖拽控件
         * @param left 控件左边距
         * @param top  控件右边距
         */
        fun onDragged(v: View, lp: MarginLayoutParams?, left: Int, top: Int, right: Int, bottom: Int) {}
        fun onDragging(v: View, left: Int, top: Int, right: Int, bottom: Int) {}

        /**
         * 当可拖拽控件被点击时回调
         *
         * @param v 拖拽控件
         */
        fun onClick(v: View)
    }

    companion object {
        private const val TAG = "DragLayer"
        private const val BTN_NORMAL_ALPHA = 0.9f
        private const val BTN_PRESSED_ALPHA = 1f

        private const val MOVE_THRESHOLD = 8
    }
}