package com.leovp.demo.basiccomponents.examples

import android.os.Bundle
import android.view.View
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.android.exts.toast
import com.leovp.androidbase.exts.kotlin.sleep
import com.leovp.circleprogressbar.CircleProgressbar
import com.leovp.circleprogressbar.base.DefaultOnClickListener
import com.leovp.circleprogressbar.base.State
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityCircleProgressbarBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlin.concurrent.thread

/**
 * Author: Michael Leo
 * Date: 2021/9/30 14:07
 */
class CircleProgressbarActivity :
    BaseDemonstrationActivity<ActivityCircleProgressbarBinding>(R.layout.activity_circle_progressbar) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityCircleProgressbarBinding {
        return ActivityCircleProgressbarBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.btnDownload.setOnSingleClickListener { btn ->
            btn.isEnabled = false
            binding.vDownload.setDeterminate()
            thread {
                for (prog in 0..100) {
                    sleep(50)
                    binding.vDownload.currentProgress = prog
                }
                binding.vDownload.setFinish()
                runOnUiThread { btn.isEnabled = true }
            }
        }

        binding.btnUpload.setOnSingleClickListener { btn ->
            btn.isEnabled = false
            binding.vUpload.setDeterminate()
            thread {
                for (prog in 0..100) {
                    sleep(20)
                    binding.vUpload.currentProgress = prog
                }
                binding.vUpload.setError()
                runOnUiThread { btn.isEnabled = true }
            }
        }

        binding.vCustomInd.setOnClickListener {
            toast("Default OnClickListener")
            LogContext.log.w(ITAG, "Default OnClickListener")
        }
        binding.vCustomInd.setOnClickListener(object : DefaultOnClickListener() {
            override fun onCancelButtonClick(view: View) {
                toast("Custom click listener Cancel.")
                LogContext.log.w(ITAG, "Custom click listener Cancel.")
            }
        })

        binding.vCustomInd2.setIndeterminate()

        // This click listener will not be triggered. Because `enableClickListener` in xml has been set to `false`.
        binding.vCustomIdle.setOnClickListener(object : DefaultOnClickListener() {
            override fun onIdleButtonClick(view: View) {
                toast("Click Upload Idle.")
                LogContext.log.w(ITAG, "Click Upload Idle.")
            }
        })

        binding.vDownload.addOnClickListener(object : DefaultOnClickListener() {
            override fun onIdleButtonClick(view: View) {
                LogContext.log.i(ITAG, "onIdleButtonClick")
                toast("onIdleButtonClick")
            }

            override fun onCancelButtonClick(view: View) {
                LogContext.log.i(ITAG, "onCancelButtonClick")
                toast("onCancelButtonClick")
            }

            override fun onFinishButtonClick(view: View) {
                LogContext.log.i(ITAG, "onFinishButtonClick")
                toast("onFinishButtonClick")
            }
        })

        binding.vUpload.addOnStateChangedListeners(object : CircleProgressbar.OnStateChangedListener {
            override fun onStateChanged(newState: State.Type) {
                val stateName = when (newState) {
                    State.Type.STATE_IDLE -> "Idle"
                    State.Type.STATE_INDETERMINATE -> "Indeterminate"
                    State.Type.STATE_DETERMINATE -> "Determinate"
                    State.Type.STATE_FINISHED -> "Finish"
                    State.Type.STATE_ERROR -> "Error"
                    else -> "Unknown"
                }
                toast("Current state=$stateName")
                LogContext.log.w(ITAG, "Current state=$stateName")
            }
        })
    }
}
