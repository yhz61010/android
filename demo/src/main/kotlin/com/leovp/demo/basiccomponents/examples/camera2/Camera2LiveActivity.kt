package com.leovp.demo.basiccomponents.examples.camera2

import android.media.MediaFormat
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.android.exts.hideNavigationBar
import com.leovp.android.exts.requestFullScreenAfterVisible
import com.leovp.android.exts.requestFullScreenBeforeSetContentView
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.camera2live.view.BackPressedListener
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityCamera2LiveBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class Camera2LiveActivity : BaseDemonstrationActivity<ActivityCamera2LiveBinding>(R.layout.activity_camera2_live) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityCamera2LiveBinding {
        return ActivityCamera2LiveBinding.inflate(layoutInflater)
    }

    private val cameraViewFragment = Camera2LiveFragment()

    override fun onCreateBeginning() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreenBeforeSetContentView()
        super.onCreate(savedInstanceState)

        CodecUtil.getCodecListByMimeType(MediaFormat.MIMETYPE_VIDEO_AVC)
            .forEach { LogContext.log.i(TAG, "H264 Encoder: ${it.name}") }
        val hasTopazEncoder =
            CodecUtil.hasCodecByName(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                "OMX.IMG.TOPAZ.VIDEO.Encoder"
            )
        LogContext.log.d(TAG, "hasTopazEncoder=$hasTopazEncoder")

        onBackPressedDispatcher.addCallback(this, true) {
            finish()
        }

        cameraViewFragment.backPressListener = object : BackPressedListener {
            override fun onBackPressed() {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (XXPermissions.isGranted(this, Permission.CAMERA)) {
            addFragment()
        } else {
            XXPermissions.with(this).permission(Permission.CAMERA).request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>, all: Boolean) {
                    toast("Grant camera permission")
                    addFragment()
                }

                override fun onDenied(denied: MutableList<String>, never: Boolean) {
                    toast("Deny camera permission")
                    finish()
                }
            })
        }
    }

    private fun addFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.cameraFragment, cameraViewFragment, "cameraview")
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        requestFullScreenAfterVisible()
        hideNavigationBar()
    }

    override fun onDestroy() {
        supportFragmentManager.beginTransaction().remove(cameraViewFragment).commitAllowingStateLoss()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Camera2LiveActivity"
    }
}
