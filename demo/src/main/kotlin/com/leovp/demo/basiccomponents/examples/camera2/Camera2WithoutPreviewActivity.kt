package com.leovp.demo.basiccomponents.examples.camera2

import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecInfo
import android.os.Bundle
import android.util.Size
import android.view.SurfaceHolder
import androidx.annotation.RequiresPermission
import androidx.lifecycle.lifecycleScope
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.android.exts.toast
import com.leovp.camera2live.Camera2ComponentHelper
import com.leovp.camera2live.utils.getPreviewOutputSize
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityCamera2WithoutPreviewBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Camera2WithoutPreviewActivity : BaseDemonstrationActivity<ActivityCamera2WithoutPreviewBinding>() {
    override fun getTagName(): String = ITAG

    companion object {
        private val DESIGNED_CAMERA_SIZE = Camera2ComponentHelper.CAMERA_SIZE_EXTRA
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityCamera2WithoutPreviewBinding {
        return ActivityCamera2WithoutPreviewBinding.inflate(layoutInflater)
    }

    private lateinit var camera2Helper: Camera2ComponentHelper
    private var previousLensFacing = CameraMetadata.LENS_FACING_BACK

    @RequiresPermission(android.Manifest.permission.CAMERA)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        XXPermissions.with(this)
            .permission(Permission.CAMERA)
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>, all: Boolean) {
                    toast("Grant camera permission")
                }

                override fun onDenied(denied: MutableList<String>, never: Boolean) {
                    toast("Deny camera permission")
                    finish()
                }
            })

        camera2Helper = Camera2ComponentHelper(this, CameraMetadata.LENS_FACING_BACK).apply {
            enableRecordFeature = false
            enableTakePhotoFeature = false
            enableGallery = false
            setLensSwitchListener(object : Camera2ComponentHelper.LensSwitchListener {
                override fun onSwitch(lensFacing: Int) {
                    LogContext.log.w(ITAG, "lensFacing=$lensFacing")
                    if (CameraMetadata.LENS_FACING_FRONT == lensFacing) {
                        LogContext.log.w(ITAG, "Front lens")
                    } else {
                        LogContext.log.w(ITAG, "Back lens")
                    }
                    previousLensFacing = lensFacing
                }
            })

            //            encoderType = if (
            //                CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
            //                || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
            //                || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
            //            ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
            //            else DataProcessFactory.ENCODER_TYPE_NORMAL
            //            encoderType = DataProcessFactory.ENCODER_TYPE_YUV420SP

            // Selects appropriate preview size and configures camera surface
            val previewSize = getPreviewOutputSize(
                Size(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height)/*cameraView.display*/,
                characteristics,
                SurfaceHolder::class.java
            ) // To ensure that size is set, initialize camera in the view's thread
            runCatching {
                LogContext.log.i(ITAG, "Prepare to call initializeCamera. previewSize=$previewSize")
                initializeCamera(previewSize.width, previewSize.height)
            }.getOrElse {
                LogContext.log.e(ITAG, "=====> Finally openCamera error <=====")
                toast("Initialized camera error. Please try again later.", error = true)
            }
        }

        binding.btnCameraRecord.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) doStartRecord() else stopRecord()
        }
    }

    @RequiresPermission(android.Manifest.permission.CAMERA)
    override fun onStop() {
        stopRecord()
        super.onStop()
    }

    override fun onDestroy() {
        if (::camera2Helper.isInitialized) camera2Helper.stopCameraThread()
        super.onDestroy()
    }

    private fun doStartRecord() {
        lifecycleScope.launch(Dispatchers.IO) {
            camera2Helper.Builder(DESIGNED_CAMERA_SIZE.width, DESIGNED_CAMERA_SIZE.height).run {
                //        camera2ComponentBuilder.previewInFullscreen = true
                quality = Camera2ComponentHelper.BITRATE_INSANE_HIGH
                // On Nexus6 Camera Fps should be CAMERA_FPS_VERY_HIGH - Range(30, 30)
                cameraFps = Camera2ComponentHelper.CAMERA_FPS_VERY_HIGH
                videoFps = Camera2ComponentHelper.VIDEO_FPS_VERY_HIGH
                iFrameInterval = 5
                bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                build()
            }
            camera2Helper.outputH264ForDebug = true
            camera2Helper.setEncodeListener(object : Camera2ComponentHelper.EncodeDataUpdateListener {
                override fun onUpdate(h264Data: ByteArray) {
                    LogContext.log.d(ITAG, "Get encoded video data length=${h264Data.size}")
                }
            })
            camera2Helper.initDebugOutput()

            camera2Helper.extraInitializeCameraForRecording()
            camera2Helper.setImageReaderForRecording()
            camera2Helper.startRecording()
        }
    }

    @RequiresPermission(android.Manifest.permission.CAMERA)
    private fun stopRecord() {
        if (::camera2Helper.isInitialized) {
            camera2Helper.run {
                closeDebugOutput()
                if (isRecording) stopRecording() else closeCamera()
                initializeCamera(previewWidth, previewHeight)
            }
        }
    }
}
