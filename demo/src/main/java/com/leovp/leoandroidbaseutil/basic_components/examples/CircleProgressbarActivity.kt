package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.sleep
import com.leovp.circle_progressbar.CircleProgressbar
import com.leovp.circle_progressbar.base.DefaultOnClickListener
import com.leovp.circle_progressbar.base.State
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityCircleProgressbarBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.LogContext
import kotlin.concurrent.thread

/**
 * Author: Michael Leo
 * Date: 2021/9/30 14:07
 */
class CircleProgressbarActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityCircleProgressbarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCircleProgressbarBinding.inflate(layoutInflater).apply { setContentView(root) }

        binding.btnDownload.setOnSingleClickListener {
            binding.vDownload.setDeterminate()
            thread {
                for (prog in 0..100) {
                    sleep(50)
                    binding.vDownload.currentProgress = prog
                }
                binding.vDownload.setFinish()
            }
        }

        binding.btnUpload.setOnSingleClickListener {
            binding.vUpload.setDeterminate()
            thread {
                for (prog in 0..100) {
                    sleep(20)
                    binding.vUpload.currentProgress = prog
                }
                binding.vUpload.setError()
            }
        }

        binding.vCustomInd.setOnClickListener {
            toast("Default OnClickListener")
            LogContext.log.w("Default OnClickListener")
        }
        binding.vCustomInd.setOnClickListener(object : DefaultOnClickListener() {
            override fun onCancelButtonClick(view: View) {
                toast("Custom click listener Cancel.")
                LogContext.log.w("Custom click listener Cancel.")
            }
        })

        binding.vCustomInd2.setIndeterminate()

        // This click listener will not be triggered. Because `enableClickListener` in xml has been set to `false`.
        binding.vCustomIdle.setOnClickListener(object : DefaultOnClickListener() {
            override fun onIdleButtonClick(view: View) {
                toast("Click Upload Idle.")
                LogContext.log.w("Click Upload Idle.")
            }
        })

        binding.vDownload.addOnClickListener(object : DefaultOnClickListener() {
            override fun onIdleButtonClick(view: View) {
                LogContext.log.i("onIdleButtonClick")
                toast("onIdleButtonClick")
            }

            override fun onCancelButtonClick(view: View) {
                LogContext.log.i("onCancelButtonClick")
                toast("onCancelButtonClick")
            }

            override fun onFinishButtonClick(view: View) {
                LogContext.log.i("onFinishButtonClick")
                toast("onFinishButtonClick")
            }
        })

        binding.vUpload.addOnStateChangedListeners(object : CircleProgressbar.OnStateChangedListener {
            override fun onStateChanged(newState: State.Type) {
                val stateName = when (newState) {
                    State.Type.STATE_IDLE          -> "Idle"
                    State.Type.STATE_INDETERMINATE -> "Indeterminate"
                    State.Type.STATE_DETERMINATE   -> "Determinate"
                    State.Type.STATE_FINISHED      -> "Finish"
                    State.Type.STATE_ERROR         -> "Error"
                    else                           -> "Unknown"
                }
                toast("Current state=$stateName")
                LogContext.log.w("Current state=$stateName")
            }
        })
    }
}