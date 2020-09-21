package com.ho1ho.leoandroidbaseutil.basic_components.examples.camera2

import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.camera2live.Camera2ComponentHelper
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.leoandroidbaseutil.R
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_camera2_without_preview.*

class Camera2WithoutPreviewActivity : AppCompatActivity() {
    companion object {
        private val DESIGNED_CAMERA_SIZE = Camera2ComponentHelper.CAMERA_SIZE_HIGH
    }

    private lateinit var camera2Helper: Camera2ComponentHelper
    private var previousLensFacing = CameraMetadata.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera2_without_preview)

        btnCameraRecord.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (AndPermission.hasPermissions(this, Permission.CAMERA)) {
                    doStartRecord()
                } else {
                    AndPermission.with(this)
                        .runtime()
                        .permission(Permission.CAMERA)
                        .onGranted {
                            Toast.makeText(this, "Grant camera permission", Toast.LENGTH_SHORT).show()
                            doStartRecord()
                        }
                        .onDenied { Toast.makeText(this, "Deny camera permission", Toast.LENGTH_SHORT).show();finish() }
                        .start()
                }
            } else {
                stopRecord()
            }
        }
    }

    override fun onStop() {
        stopRecord()
        super.onStop()
    }

    override fun onDestroy() {
        camera2Helper.stopCameraThread()
        super.onDestroy()
    }

    private fun doStartRecord() {
        camera2Helper = Camera2ComponentHelper(this, CameraMetadata.LENS_FACING_BACK)
        camera2Helper.enableRecordFeature = false
        camera2Helper.enableTakePhotoFeature = false
        camera2Helper.enableGallery = false

        camera2Helper.encoderType = if (
            CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
            || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
            || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
        ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
        else DataProcessFactory.ENCODER_TYPE_NORMAL

        // Selects appropriate preview size and configures camera surface
        val previewSize = getPreviewOutputSize(
            Size(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height)/*cameraView.display*/,
            camera2Helper.characteristics,
            SurfaceHolder::class.java
        )
        // To ensure that size is set, initialize camera in the view's thread
        runCatching {
            camera2Helper.initializeCamera(previewSize.width, previewSize.height)
        }.getOrElse {
            LLog.e(ITAG, "=====> Finally openCamera error <=====")
            ToastUtil.showErrorToast("Initialized camera error. Please try again later.")
        }

        // CAMERA_SIZE_NORMAL & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=5
        // BITRATE_MODE_CQ: 348.399kB/s
        // BITRATE_MODE_CBR: 85.875kB/s
        // BITRATE_MODE_VBR: 84.929kB/s
        // CAMERA_SIZE_HIGH & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=3
        // BITRATE_MODE_CBR: 113.630kB/s
        val camera2ComponentBuilder = camera2Helper.Builder(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height)
//        camera2ComponentBuilder.previewInFullscreen = true
        camera2ComponentBuilder.quality = Camera2ComponentHelper.BITRATE_NORMAL
        // On Nexus6 Camera Fps should be CAMERA_FPS_VERY_HIGH - Range(30, 30)
        camera2ComponentBuilder.cameraFps = Camera2ComponentHelper.CAMERA_FPS_NORMAL
        camera2ComponentBuilder.videoFps = Camera2ComponentHelper.VIDEO_FPS_FREQUENCY_HIGH
        camera2ComponentBuilder.iFrameInterval = 1
        camera2ComponentBuilder.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        camera2ComponentBuilder.build()
        camera2Helper.outputH264ForDebug = true
        camera2Helper.setEncodeListener(object : Camera2ComponentHelper.EncodeDataUpdateListener {
            override fun onUpdate(h264Data: ByteArray) {
                Log.d(ITAG, "Get encoded video data length=" + h264Data.size)
            }
        })
        camera2Helper.setLensSwitchListener(object : Camera2ComponentHelper.LensSwitchListener {
            override fun onSwitch(lensFacing: Int) {
                Log.w(ITAG, "lensFacing=$lensFacing")
                if (CameraMetadata.LENS_FACING_FRONT == lensFacing) {
                    Log.w(ITAG, "Front lens")
                } else {
                    Log.w(ITAG, "Back lens")
                }
                previousLensFacing = lensFacing
            }
        })

        camera2Helper.initDebugOutput()
    }

    private fun stopRecord() {
        camera2Helper.closeDebugOutput()
        if (camera2Helper.isRecording) {
            camera2Helper.stopRecording()
        } else {
            camera2Helper.closeCamera()
        }
    }
}
