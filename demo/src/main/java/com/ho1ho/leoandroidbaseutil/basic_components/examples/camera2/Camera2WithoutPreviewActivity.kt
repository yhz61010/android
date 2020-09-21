package com.ho1ho.leoandroidbaseutil.basic_components.examples.camera2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ho1ho.leoandroidbaseutil.R
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_camera2_without_preview.*

class Camera2WithoutPreviewActivity : AppCompatActivity() {
//    protected lateinit var camera2Helper: Camera2ComponentHelper

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

    private fun doStartRecord() {
//        camera2Helper = Camera2ComponentHelper(this, CameraMetadata.LENS_FACING_BACK, view)
//        camera2Helper.enableRecordFeature = enableRecordFeature
//        camera2Helper.enableTakePhotoFeature = enableTakePhotoFeature
//        camera2Helper.enableGallery = enableGallery
//
//        camera2Helper.encoderType = if (
//            CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
//            || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
//            || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
//        ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
//        else DataProcessFactory.ENCODER_TYPE_NORMAL
//
//        // Selects appropriate preview size and configures camera surface
//        val previewSize = getPreviewOutputSize(
//            Size(Camera2LiveFragment.DESIGNED_CAMERA_SIZE.width, Camera2LiveFragment.DESIGNED_CAMERA_SIZE.height)/*cameraView.display*/,
//            camera2Helper.characteristics,
//            SurfaceHolder::class.java
//        )
//        // To ensure that size is set, initialize camera in the view's thread
//        runCatching {
//            `camera2Helper`.initializeCamera(previewSize.width, previewSize.height)
//        }.getOrElse {
//            LLog.e(Camera2LiveFragment.TAG, "=====> Finally openCamera error <=====")
//            ToastUtil.showErrorToast("Initialized camera error. Please try again later.")
//        }
    }

    private fun stopRecord() {
    }
}
