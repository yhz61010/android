package com.leovp.androidbase.ui;

import android.view.MotionEvent;
import android.view.View;

/**
 * Author: Michael Leo
 * Date: 19-7-30 上午11:40
 */
public class OnDragTouchListener implements View.OnTouchListener {
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }
//    private static final String TAG = OnDragTouchListener.class.getSimpleName();
//
//    private static final Point SCREEN_RESOLUTION = DeviceUtil.getResolutionWithVirtualKey();
//
//    private static final float BTN_NORMAL_ALPHA = 0.9f;
//    private static final float BTN_PRESSED_ALPHA = 1f;
//
//    private static final int CUSTOM_STATUS_BAR_HEIGHT = CustomApplication.getInstance().getResources().getDimensionPixelSize(R.dimen.custom_status_bar_height);
//
//    //屏幕宽高
//    private int mScreenWidth, mScreenHeight;
//    //手指按下时的初始位置
//    private float mOriginalX, mOriginalY;
//    //记录手指与view的左上角的距离
//    private float mDistanceX, mDistanceY;
//    private int mControlLayerLayoutWidth;
//    private int mControlLayerWidth;
//    private int mControlLayerHeight;
//    //标记是否开启自动拉到边缘功能
//    private boolean mIsAutoPullToBorder;
//    private int mLeft, mTop, mRight, mBottom;
//    private OnDraggableClickListener mListener;
//
//    private View mFocusedCtlBtn;
//
//    private boolean mIsDragging;
//    private int mRealLeftOffset = 0;
//
//    public OnDragTouchListener() {
//    }
//
//    public OnDragTouchListener(boolean isAutoPullToBorder) {
//        mIsAutoPullToBorder = isAutoPullToBorder;
//    }
//
//    @SuppressLint("ClickableViewAccessibility")
//    @Override
//    public boolean onTouch(final View v, MotionEvent event) {
////        CLog.d(TAG, "event.getAction()=%d v=%s", event.getAction(), v);
//        switch (event.getAction()) {
//            case MotionEvent.ACTION_DOWN:
//                CLog.i(TAG, "ACTION_DOWN");
//
//                mControlLayerWidth = v.getWidth();
//                CLog.d(TAG, "onTouch v.getLayoutParams().width=%d v.getWidth=%d Left=%d Right=%d",
//                        v.getLayoutParams().width,
//                        v.getWidth(),
//                        v.getLeft(),
//                        v.getRight());
//                mControlLayerHeight = v.getHeight();
//
//                mIsDragging = false;
//                mScreenWidth = SCREEN_RESOLUTION.x;
//                mScreenHeight = SCREEN_RESOLUTION.y;
//                mOriginalX = event.getRawX();
//                mOriginalY = event.getRawY();
//                mDistanceX = event.getRawX() - v.getLeft();
//                mDistanceY = event.getRawY() - v.getTop();
//
//                RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) v.getLayoutParams();
//                param.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
//                param.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
//                param.leftMargin = v.getLeft();
//                param.topMargin = v.getTop();
//                param.bottomMargin = 0;
//                param.rightMargin = 0;
//
//                if (mListener != null) {
//                    int childCount = ((LinearLayout) v).getChildCount();
//                    if (childCount < 1) {
//                        break;
//                    }
//
//                    mFocusedCtlBtn = ((LinearLayout) v).getChildAt(0);
//                    int eachBtnHeightInPixel = mFocusedCtlBtn.getHeight();
//                    int gapHeightInPixel = CustomApplication.getInstance().getResources().getDimensionPixelSize(R.dimen.act_main_btn_ctl_spacer);
//
//                    if (0 <= mDistanceY && mDistanceY <= eachBtnHeightInPixel) {
//                        CLog.i(TAG, "Click on Button[0]");
//                        mFocusedCtlBtn.setAlpha(BTN_PRESSED_ALPHA);
//                        break;
//                    }
//
//                    for (int i = 1; i < childCount; i++) {
//                        if (((eachBtnHeightInPixel + gapHeightInPixel) * i) <= mDistanceY && mDistanceY <= ((eachBtnHeightInPixel + gapHeightInPixel) * i) + eachBtnHeightInPixel) {
//                            CLog.i(TAG, "Click on Button[%d]", i);
//                            mFocusedCtlBtn = ((LinearLayout) v).getChildAt(i);
//                            mFocusedCtlBtn.setAlpha(BTN_PRESSED_ALPHA);
//                            break;
//                        }
//                    }
//                }
//                break;
//            case MotionEvent.ACTION_MOVE:
//                mControlLayerLayoutWidth = Math.max(v.getLayoutParams().width, v.getWidth());
//                if (mControlLayerWidth > 0 && mControlLayerLayoutWidth > 0) {
//                    mRealLeftOffset = Math.abs(mControlLayerLayoutWidth - mControlLayerWidth);
//                    CLog.d(TAG, "mLeft=%d mControlLayerLayoutWidth=%d mRealLeftOffset=%d", mLeft, mControlLayerLayoutWidth, mRealLeftOffset);
//                }
//                mIsDragging = true;
//                mLeft = (int) (event.getRawX() - mDistanceX);
//                mTop = (int) (event.getRawY() - mDistanceY);
//                mRight = mLeft + mControlLayerLayoutWidth;
//                mBottom = mTop + mControlLayerHeight;
//                if (mLeft < 0) {
//                    mLeft = 0;
//                    mRight = mLeft + mControlLayerLayoutWidth;
//                }
//                if (mTop < CUSTOM_STATUS_BAR_HEIGHT) {
//                    mTop = CUSTOM_STATUS_BAR_HEIGHT;
//                    mBottom = mTop + mControlLayerHeight;
//                }
//                if (mRight > mScreenWidth) {
//                    mRight = mScreenWidth;
//                    mLeft = mScreenWidth - mControlLayerLayoutWidth;
//                }
//                if (mBottom > mScreenHeight) {
//                    mBottom = mScreenHeight;
//                    mTop = mScreenHeight - mControlLayerHeight;
//                }
//                CLog.i(TAG, "ACTION_MOVE L=%d|T=%d|R=%d|B=%d|vw=%d|vh=%d", mLeft, mTop, mRight, mBottom, mControlLayerLayoutWidth, mControlLayerHeight);
//                if (Math.abs(event.getRawX() - mOriginalX) <
//                        AppUtil.dip2px(5) &&
//                        Math.abs(event.getRawY() - mOriginalY) <
//                                AppUtil.dip2px(5)) {
//                    CLog.i(TAG, "ACTION_MOVE Distance is too short, do not move the layout.");
//                } else {
//                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) v.getLayoutParams();
//                    params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
//                    params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
//                    params.leftMargin = mLeft;
//                    params.topMargin = mTop;
////                    params.bottomMargin = mBottom;
////                    params.rightMargin = mRight;
//
//                    v.layout(mLeft, mTop, mRight, mBottom);
//                    if (mListener != null) {
//                        mListener.onDragging(v, mLeft, mTop, mRight, mBottom);
//                    }
//                }
//                break;
//            case MotionEvent.ACTION_UP:
//                CLog.i(TAG, "ACTION_UP mOriginalX=%f | mOriginalY=%f | mDistanceX=%f | mDistanceY=%f", mOriginalX, mOriginalY, mDistanceX, mDistanceY);
//                mFocusedCtlBtn.setAlpha(BTN_NORMAL_ALPHA);
//
//                //如果移动距离过小，则判定为点击
//                if (Math.abs(event.getRawX() - mOriginalX) <
//                        AppUtil.dip2px(5) &&
//                        Math.abs(event.getRawY() - mOriginalY) <
//                                AppUtil.dip2px(5)) {
//                    CLog.i(TAG, "ACTION_UP - Do click(%s)", mFocusedCtlBtn.getId());
//                    if (mListener != null) {
//                        mListener.onClick(mFocusedCtlBtn);
//                    }
//                } else {
//                    //在拖动过按钮后，如果其他view刷新导致重绘，会让按钮重回原点，所以需要更改布局参数
//                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
//                    startAutoPull(v, lp, mRealLeftOffset);
////                    if (mListener != null) {
////                        CLog.d(TAG, "onAfterActionUp finalLeft=%d top=%d mRealLeftOffset=%d", v.getLeft() + mRealLeftOffset, v.getTop(), mRealLeftOffset);
////                        mListener.onAfterActionUp(v, v.getLeft() + mRealLeftOffset, v.getTop());
////                    }
//                }
//                //消除警告
////                v.performClick();
//                break;
//        }
//        return true;
//    }
//
//    public OnDraggableClickListener getOnDraggableClickListener() {
//        return mListener;
//    }
//
//    public void setOnDraggableClickListener(OnDraggableClickListener listener) {
//        mListener = listener;
//    }
//
//    public boolean isAutoPullToBorder() {
//        return mIsAutoPullToBorder;
//    }
//
//    public void setAutoPullToBorder(boolean misAutoPullToBorder) {
//        this.mIsAutoPullToBorder = misAutoPullToBorder;
//    }
//
//    /**
//     * 开启自动拖拽
//     *
//     * @param v  拉动控件
//     * @param lp 控件布局参数
//     */
//    private void startAutoPull(final View v, final ViewGroup.MarginLayoutParams lp, int realLeftOffset) {
//        CLog.i(TAG, "startAutoPull Left=%d, Top=%d, Right=%d, Bottom=%d, realLeftOffset=%d",
//                mLeft, mTop, mRight, mBottom, realLeftOffset);
//        if (!mIsAutoPullToBorder) {
//            v.layout(mLeft + realLeftOffset, mTop, mRight, mBottom);
//            lp.setMargins(mLeft + realLeftOffset, mTop, 0, 0);
//            v.setLayoutParams(lp);
//            if (mListener != null) {
//                CLog.i(TAG, "1onDragged Left=%d Top=%d", mLeft + realLeftOffset, mTop);
//                mListener.onDragged(v, lp, mLeft + realLeftOffset, mTop, mRight, mBottom);
//            }
//            return;
//        }
//        //当用户拖拽完后，让控件根据远近距离回到最近的边缘
//        float end = 0;
//        if ((mLeft + mControlLayerWidth / 2) >= mScreenWidth / 2) {
//            end = mScreenWidth - mControlLayerWidth - realLeftOffset;
//        }
//        CLog.d(TAG, "mLeft=%d end=%f realLeftOffset=%d", mLeft, end, realLeftOffset);
//        ValueAnimator animator = ValueAnimator.ofFloat(mLeft, end);
//        animator.setInterpolator(new DecelerateInterpolator());
//        animator.addUpdateListener(animation -> {
//            mLeft = (int) ((float) animation.getAnimatedValue());
//            mRight = mLeft + mControlLayerWidth;
//            v.layout(mLeft, mTop, mRight, mBottom);
//            lp.setMargins(mLeft, mTop, 0, 0);
//            v.setLayoutParams(lp);
//            CLog.d(TAG, "animator mLeft=%d | mTop=%d | mRight=%d | mBottom=%d", mLeft, mTop, mRight, mBottom);
//        });
//        final float finalEnd = end;
//        animator.addListener(new AnimatorListenerAdapter() {
//            @Override
//            public void onAnimationEnd(Animator animation) {
//                if (mListener != null) {
//                    CLog.i(TAG, "2onDragged Left=%d Top=%d", (int) finalEnd, mTop);
//                    mListener.onDragged(v, lp, (int) finalEnd, mTop, mRight, mBottom);
//                }
//            }
//        });
//        animator.setDuration(400);
//        animator.start();
//    }
//
//    /**
//     * 控件拖拽监听器
//     */
//    public interface OnDraggableClickListener {
//
////        default void onAfterActionUp(View v, int finalLeft, int finalTop) {
////
////        }
//
//        /**
//         * 当控件拖拽完后回调
//         *
//         * @param v    拖拽控件
//         * @param left 控件左边距
//         * @param top  控件右边距
//         */
//        default void onDragged(final View v, final ViewGroup.MarginLayoutParams lp, int left, int top, int right, int bottom) {
//        }
//
//        default void onDragging(final View v, int left, int top, int right, int bottom) {
//        }
//
//        /**
//         * 当可拖拽控件被点击时回调
//         *
//         * @param v 拖拽控件
//         */
//        void onClick(final View v);
//    }
}