package com.leovp.androidbase.listeners

import android.view.View
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.utils.LLog

/**
 * Author: Michael Leo
 * Date: 2020/9/27 下午5:48
 *
 * A simple click listener to avoid duplicating click by using delay
 */
abstract class OnSingleClickListener : View.OnClickListener {
    companion object {
        private const val DELAY_TIME: Long = 500
    }

    var defaultDelayTime = DELAY_TIME

    private var lastClickTime = 0L

    abstract fun onSingleClick(view: View)

    @Synchronized
    private fun isDuplicatedClick(): Boolean {
        val time = System.currentTimeMillis()
        val delta = time - lastClickTime
        if (delta < defaultDelayTime) {
            return true
        }
        lastClickTime = time
        return false
    }

    override fun onClick(v: View) {
        if (!isDuplicatedClick()) {
            onSingleClick(v)
        } else {
            LLog.d(ITAG, "Duplicated click on $v")
        }
    }
}