package com.ho1ho.leoandroidbaseutil.ui.camera2.record

import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.androidbase.utils.media.CodecUtil
import com.ho1ho.camera2live.Camera2ComponentHelper
import com.ho1ho.camera2live.base.DataProcessFactory
import com.ho1ho.camera2live.view.BaseCamera2Fragment

/**
 * Author: Michael Leo
 * Date: 20-6-29 上午9:50
 */
class Camera2RecordFragment : BaseCamera2Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // CAMERA_SIZE_NORMAL & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=5
        // BITRATE_MODE_CQ: 348.399kB/s
        // BITRATE_MODE_CBR: 85.875kB/s
        // BITRATE_MODE_VBR: 84.929kB/s
        // CAMERA_SIZE_HIGH & BITRATE_NORMAL & CAMERA_FPS_NORMAL & VIDEO_FPS_FREQUENCY_HIGH & KEY_I_FRAME_INTERVAL=3
        // BITRATE_MODE_CBR: 113.630kB/s
        val desiredSize = CAMERA_SIZE_HIGH
        val camera2ComponentBuilder = camera2Helper.Builder(desiredSize[0], desiredSize[1])
//        camera2ComponentBuilder.previewInFullscreen = true
        camera2ComponentBuilder.quality = Camera2ComponentHelper.BITRATE_NORMAL
        // On Nexus6 Camera Fps should be CAMERA_FPS_VERY_HIGH - Range(30, 30)
        camera2ComponentBuilder.cameraFps = Camera2ComponentHelper.CAMERA_FPS_VERY_HIGH
        camera2ComponentBuilder.videoFps = Camera2ComponentHelper.VIDEO_FPS_FREQUENCY_HIGH
        camera2ComponentBuilder.iFrameInterval = 1
        camera2ComponentBuilder.bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        camera2ComponentBuilder.build()
        camera2Helper.outputH264ForDebug = true
        camera2Helper.setEncodeListener(object : Camera2ComponentHelper.EncodeDataUpdateListener {
            override fun onUpdate(h264Data: ByteArray) {
                Log.d(TAG, "Get encoded video data length=" + h264Data.size)
            }
        })
        camera2Helper.setLensSwitchListener(object : Camera2ComponentHelper.LensSwitchListener {
            override fun onSwitch(lensFacing: Int) {
                Log.w(TAG, "lensFacing=$lensFacing")
                if (CameraMetadata.LENS_FACING_FRONT == lensFacing) {
                    switchFlashBtn.isChecked = false
                    switchFlashBtn.visibility = View.GONE
                } else {
                    switchFlashBtn.visibility = View.VISIBLE
                }
                previousLensFacing = lensFacing
            }
        })

        cameraView.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                camera2Helper.initDebugOutput()
                camera2Helper.encoderType = if (
                    CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
                    || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.Exynos.AVC.Encoder")
                    || CodecUtil.hasEncoderByCodecName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.MTK.VIDEO.ENCODER.AVC")
                ) DataProcessFactory.ENCODER_TYPE_YUV_ORIGINAL
                else DataProcessFactory.ENCODER_TYPE_NORMAL

                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(cameraView.display, camera2Helper.characteristics, SurfaceHolder::class.java)
                Log.d(TAG, "Selected preview size: $previewSize")
                cameraView.setDimension(previewSize.width, previewSize.height)
                // To ensure that size is set, initialize camera in the view's thread
                view.post { camera2Helper.initializeCamera(true) }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    override suspend fun onTakePhotoButtonClick() {
        throw IllegalAccessError("onTakePhotoButtonClick() method should not be called.")
    }

    override suspend fun onRecordButtonClick() {
        CLog.w(TAG, "onRecordButtonClick")
        camera2Helper.startRecording()
    }

    override suspend fun onStopRecordButtonClick() {
        camera2Helper.stopRecording()
    }

    override fun onResume() {
        CLog.i(TAG, "Camera2RecordFragment onResume")
        super.onResume()
    }

    override fun onPause() {
        CLog.i(TAG, "Camera2RecordFragment onPause")
        camera2Helper.closeDebugOutput()
        super.onPause()
    }

    companion object {
        private val TAG = Camera2RecordFragment::class.java.simpleName
    }
}