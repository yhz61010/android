package com.ho1ho.leoandroidbaseutil.ui

import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.Toast
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.camera2live.Camera2Component
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_camera2_live.*

class Camera2LiveActivity : BaseDemonstrationActivity() {
    private lateinit var camera2Component: Camera2Component
    private var previousLensFacing = CameraMetadata.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_camera2_live)

        CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_AVC)
            .forEach { CLog.e(TAG, "H264 Encoder: ${it.name}") }
        val hasTopazEncoder =
            CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
        CLog.e(TAG, "hasTopazEncoder=$hasTopazEncoder")

        val desiredSize = CAMERA_SIZE_HIGH

        // CAMERA_SIZE_NORMAL & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=5
        // BITRATE_MODE_CQ: 348.399kB/s
        // BITRATE_MODE_CBR: 85.875kB/s
        // BITRATE_MODE_VBR: 84.929kB/s

        // CAMERA_SIZE_HIGH & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=3
        // BITRATE_MODE_CBR: 113.630kB/s
        val camera2ComponentBuilder = Camera2Component.Builder(this, desiredSize[0], desiredSize[1])
        camera2ComponentBuilder.cameraTextureView = findViewById(R.id.texture)
        camera2ComponentBuilder.previewInFullscreen = true
        camera2ComponentBuilder.quality = Camera2Component.BITRATE_NORMAL
        // On Nexus6 Camera Fps should be CAMERA_FPS_VERY_HIGH - Range(30, 30)
        camera2ComponentBuilder.cameraFps = Camera2Component.CAMERA_FPS_NORMAL
        camera2ComponentBuilder.videoFps = Camera2Component.VIDEO_FPS_FREQUENCY_HIGH
        camera2ComponentBuilder.iFrameInterval = 1
        camera2ComponentBuilder.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        camera2Component = camera2ComponentBuilder.build()

        camera2Component.setDebugOutputH264(true)
        //        camera2Component.setDebugOutputYuv(true);

        camera2Component.setEncodeListener(object : Camera2Component.EncodeDataUpdateListener {
            override fun onUpdate(h264Data: ByteArray) {
                CLog.d(TAG, "Get encoded video data length=${h264Data.size}")
            }
        })
        camera2Component.setLensSwitchListener(object : Camera2Component.LensSwitchListener {
            override fun onSwitch(lensFacing: Int) {
                CLog.w(TAG, "lensFacing=$lensFacing")
                previousLensFacing = lensFacing
            }
        })

        switchBtn.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> camera2Component.switchCamera() }
        switchFlashBtn.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> camera2Component.switchFlash() }

        AndPermission.with(this)
            .runtime()
            .permission(Permission.CAMERA)
            .onGranted {
                Toast.makeText(this, "Grant camera permission", Toast.LENGTH_SHORT).show()
                permissionGranted()
            }
            .onDenied { Toast.makeText(this, "Deny camera permission", Toast.LENGTH_SHORT).show();finish() }
            .start()
    }

    public override fun onResume() {
        super.onResume()
    }

    private fun permissionGranted() {
        camera2Component.initDebugOutput()
        camera2Component.encoderType = if (
            CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
            || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
            || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
        ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
        else DataProcessFactory.ENCODER_TYPE_NORMAL
        camera2Component.openCameraAndGetData(previousLensFacing) // LENS_FACING_FRONT LENS_FACING_BACK
    }

    override fun onPause() {
        camera2Component.closeDebugOutput()
        camera2Component.closeCameraAndStopRecord()
        super.onPause()
    }

    companion object {
        private const val TAG = "Camera2LiveActivity"

        @Suppress("unused")
        private val CAMERA_SIZE_HIGH = intArrayOf(720, 1280)

        @Suppress("unused")
        private val CAMERA_SIZE_NORMAL = intArrayOf(720, 960)

        @Suppress("unused")
        private val CAMERA_SIZE_LOW = intArrayOf(480, 640)
    }
}