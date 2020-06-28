package com.ho1ho.camera2live

import android.annotation.SuppressLint
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ToggleButton
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.ho1ho.androidbase.exts.getPreviewOutputSize
import com.ho1ho.camera2live.utils.OrientationLiveData
import com.ho1ho.camera2live.view.CameraSurfaceView
import kotlinx.android.synthetic.main.fragment_camera_view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/**
 * Author: Michael Leo
 * Date: 20-6-24 下午4:18
 */
class CameraPhotoFragment : Fragment() {

    private lateinit var camera2Helper: CameraPhotoFragmentHelper

    private lateinit var switchFlashBtn: ToggleButton

    var backPressListener: BackPressedListener? = null

    /** Overlay on top of the camera preview */
    private lateinit var overlay: View

    /** Where the camera preview is displayed */
    private lateinit var cameraView: CameraSurfaceView

    /** Live data listener for changes in the device orientation relative to the camera */
    lateinit var relativeOrientation: OrientationLiveData

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_view, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            activity?.supportFragmentManager?.popBackStackImmediate()
            backPressListener?.onBackPressed()
        }
        overlay = view.findViewById(R.id.overlay)
        cameraView = view.findViewById(R.id.cameraSurfaceView)
        camera2Helper = CameraPhotoFragmentHelper(this, CameraMetadata.LENS_FACING_BACK, cameraView, overlay)
        switchFlashBtn = view.findViewById(R.id.switchFlashBtn)
        switchFlashBtn.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) camera2Helper.turnOnFlash() else camera2Helper.turnOffFlash()
        }

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
                view.post { camera2Helper.initializeCamera() }
            }
        })

        // Listen to the capture button
        ivShot.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false

            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
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

                // Re-enable click listener after photo is taken
                it.post { it.isEnabled = true }
            }
        }

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), camera2Helper.characteristics).apply {
            observe(viewLifecycleOwner, Observer { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
            })
        }
    }

    override fun onStop() {
        super.onStop()
        camera2Helper.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        camera2Helper.release()
    }

    companion object {
        private val TAG = CameraPhotoFragment::class.java.simpleName
    }
}