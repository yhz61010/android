package com.ho1ho.leoandroidbaseutil.ui.camera2.photo

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import androidx.exifinterface.media.ExifInterface
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.camera2live.view.BaseCamera2Fragment

/**
 * Author: Michael Leo
 * Date: 20-6-24 下午4:18
 */
class Camera2PhotoFragment : BaseCamera2Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(cameraView.display, camera2Helper.characteristics, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${cameraView.width} x ${cameraView.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                cameraView.setDimension(previewSize.width, previewSize.height)
                // To ensure that size is set, initialize camera in the view's thread
                view.post { camera2Helper.initializeCamera(false) }
            }
        })
    }

    override suspend fun onTakePhotoButtonClick() {
        camera2Helper.takePhoto().use { result ->
            Log.d(TAG, "Result received: $result")

            // Save the result to disk
            val output = camera2Helper.saveResult(result)
            Log.d(TAG, "Image saved: ${output.absolutePath}")

            // If the result is a JPEG file, update EXIF metadata with orientation info
            if (output.extension == "jpg") {
                val exif = ExifInterface(output.absolutePath)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, result.orientation.toString())
                exif.saveAttributes()
                Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
            }

            // Display the photo taken to user
//                    lifecycleScope.launch(Dispatchers.Main) {
//                        navController.navigate(
//                            CameraFragmentDirections
//                                .actionCameraToJpegViewer(output.absolutePath)
//                                .setOrientation(result.orientation)
//                                .setDepth(
//                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
//                                            result.format == ImageFormat.DEPTH_JPEG
//                                )
//                        )
//                    }
        }
    }

    override suspend fun onRecordButtonClick() {
        throw IllegalAccessError("onRecordButtonClick() method should not be called.")
    }

    companion object {
        private val TAG = Camera2PhotoFragment::class.java.simpleName
    }
}