package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.sleep
import com.leovp.circle_progressbar.CircleProgressbar
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityCircleProgressbarBinding
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
                binding.vUpload.setFinish()
            }
        }

        binding.vCustomInd.setOnClickListener {
            toast("You've clicked me!")
        }

        binding.vDownload.addOnClickListener(object : CircleProgressbar.OnClickListener {
            override fun onIdleButtonClick(view: View) {
                toast("onIdleButtonClick")
            }

            override fun onCancelButtonClick(view: View) {
                toast("onCancelButtonClick")
            }

            override fun onFinishButtonClick(view: View) {
                toast("onFinishButtonClick")
            }
        })

        binding.vUpload.addOnStateChangedListeners(object : CircleProgressbar.OnStateChangedListener {
            override fun onStateChanged(newState: Int) {
                val stateName = when (newState) {
                    CircleProgressbar.STATE_IDLE -> "Idle"
                    CircleProgressbar.STATE_INDETERMINATE -> "Indeterminate"
                    CircleProgressbar.STATE_DETERMINATE -> "Determinate"
                    CircleProgressbar.STATE_FINISHED -> "Finish"
                    else -> "Unknown"
                }
                toast("Current state=$stateName")
            }
        })
    }
}