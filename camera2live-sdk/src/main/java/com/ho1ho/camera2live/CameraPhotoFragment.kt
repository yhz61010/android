package com.ho1ho.camera2live

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.ho1ho.camera2live.view.BaseCamera2Fragment

/**
 * Author: Michael Leo
 * Date: 20-6-24 下午4:18
 */
class CameraPhotoFragment : BaseCamera2Fragment() {
    override suspend fun onClickShutter() {
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

    companion object {
        private val TAG = CameraPhotoFragment::class.java.simpleName
    }
}