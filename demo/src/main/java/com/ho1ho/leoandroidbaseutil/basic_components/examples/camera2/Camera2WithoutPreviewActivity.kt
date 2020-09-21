package com.ho1ho.leoandroidbaseutil.basic_components.examples.camera2

import android.hardware.camera2.CameraMetadata
import android.media.MediaFormat
import android.os.Bundle
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
import com.ho1ho.camera2live.view.BaseCamera2Fragment
import com.ho1ho.leoandroidbaseutil.R
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_camera2_without_preview.*

class Camera2WithoutPreviewActivity : AppCompatActivity() {
    private lateinit var camera2Helper: Camera2ComponentHelper

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
            Size(BaseCamera2Fragment.CAMERA_SIZE_HIGH.width, BaseCamera2Fragment.CAMERA_SIZE_HIGH.height)/*cameraView.display*/,
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
    }

    private fun stopRecord() {
        if (camera2Helper.isRecording) {
            camera2Helper.stopRecording()
        } else {
            camera2Helper.closeCamera()
        }
    }
}
