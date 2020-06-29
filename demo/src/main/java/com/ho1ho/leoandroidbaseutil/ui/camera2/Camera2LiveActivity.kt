package com.ho1ho.leoandroidbaseutil.ui.camera2

import android.media.MediaFormat
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import com.ho1ho.androidbase.utils.AppUtil
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.camera2live.view.BackPressedListener
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.leoandroidbaseutil.ui.camera2.record.Camera2RecordFragment
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission

class Camera2LiveActivity : BaseDemonstrationActivity() {
    private val cameraViewFragment = Camera2RecordFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUtil.hideNavigationBar(this)
        AppUtil.requestFullScreen(this)
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_camera2_live)

        CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_AVC)
            .forEach { CLog.e(TAG, "H264 Encoder: ${it.name}") }
        val hasTopazEncoder =
            CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
        CLog.e(TAG, "hasTopazEncoder=$hasTopazEncoder")

        cameraViewFragment.backPressListener = object :
            BackPressedListener {
            override fun onBackPressed() {
                this@Camera2LiveActivity.onBackPressed()
            }
        }

        AndPermission.with(this)
            .runtime()
            .permission(Permission.CAMERA)
            .onGranted {
                Toast.makeText(this, "Grant camera permission", Toast.LENGTH_SHORT).show()
            }
            .onDenied { Toast.makeText(this, "Deny camera permission", Toast.LENGTH_SHORT).show();finish() }
            .start()

        supportFragmentManager.beginTransaction()
            .replace(R.id.cameraFragment, cameraViewFragment, "cameraview")
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onDestroy() {
        supportFragmentManager.beginTransaction()
            .remove(cameraViewFragment)
            .commitAllowingStateLoss()
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    companion object {
        private const val TAG = "Camera2LiveActivity"
    }
}