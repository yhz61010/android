package com.leovp.circle_progressbar.base

import android.view.View
import com.leovp.circle_progressbar.CircleProgressbar

/**
 * Author: Michael Leo
 * Date: 2022/1/30 10:05
 */
open class DefaultOnClickListener : CircleProgressbar.OnClickListener {
    override fun onIdleButtonClick(view: View) {}
    override fun onCancelButtonClick(view: View) {}
    override fun onFinishButtonClick(view: View) {}
    override fun onErrorButtonClick(view: View) {}
}