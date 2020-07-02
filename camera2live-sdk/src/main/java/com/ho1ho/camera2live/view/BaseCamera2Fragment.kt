package com.ho1ho.camera2live.view

import android.annotation.SuppressLint
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.ho1ho.camera2live.Camera2ComponentHelper
import com.ho1ho.camera2live.R
import com.ho1ho.camera2live.utils.OrientationLiveData
import kotlinx.android.synthetic.main.fragment_camera_view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 20-6-28 下午5:36
 */
abstract class BaseCamera2Fragment : Fragment() {
    protected var previousLensFacing = CameraMetadata.LENS_FACING_BACK
    private lateinit var switchCameraBtn: ToggleButton
    protected lateinit var switchFlashBtn: ToggleButton

    protected lateinit var camera2Helper: Camera2ComponentHelper
    protected var enableTakePhotoFeature = true
    protected var enableRecordFeature = true
    protected var enableGallery = true

    var backPressListener: BackPressedListener? = null

    /** Where the camera preview is displayed */
    protected lateinit var cameraView: CameraSurfaceView

    /** Live data listener for changes in the device orientation relative to the camera */
    lateinit var relativeOrientation: OrientationLiveData

    abstract suspend fun getCapturingImage(result: Camera2ComponentHelper.CombinedCaptureResult)
    abstract suspend fun onRecordButtonClick()
    abstract suspend fun onStopRecordButtonClick()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_camera_view, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!enableRecordFeature) {
            view.findViewById<View>(R.id.ivShotRecord).visibility = View.GONE
            view.findViewById<View>(R.id.ivRecordStop).visibility = View.GONE
            view.findViewById<ViewGroup>(R.id.llRecordTime).visibility = View.GONE
        }
        if (!enableTakePhotoFeature) {
            view.findViewById<View>(R.id.ivShot).visibility = View.GONE
        }
        view.findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            activity?.supportFragmentManager?.popBackStackImmediate()
            backPressListener?.onBackPressed()
        }
        cameraView = view.findViewById(R.id.cameraSurfaceView)
        camera2Helper = Camera2ComponentHelper(this, CameraMetadata.LENS_FACING_BACK, view)
        camera2Helper.enableRecordFeature = enableRecordFeature
        camera2Helper.enableTakePhotoFeature = enableTakePhotoFeature
        switchFlashBtn = view.findViewById(R.id.switchFlashBtn)
        switchFlashBtn.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) camera2Helper.turnOnFlash() else camera2Helper.turnOffFlash()
        }
        switchCameraBtn = view.findViewById(R.id.switchFacing)
        switchCameraBtn.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> camera2Helper.switchCamera() }

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

        // Listen to the capture button
        ivShot.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false
            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                val st = SystemClock.elapsedRealtime()
                getCapturingImage(camera2Helper.takePhoto())
                // Re-enable click listener after photo is taken
                it.post { it.isEnabled = true }
                Log.d(TAG, "=====> Total click shot button processing cost: ${SystemClock.elapsedRealtime() - st}")
            }
        }

        ivShotRecord.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false

            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                onRecordButtonClick()
                camera2Helper.extraInitializeCameraForRecording()
                camera2Helper.setImageReaderForRecording()
                camera2Helper.setRepeatingRequest()
                camera2Helper.startRecording()
                // Re-enable click listener after recording is taken
                it.post { it.isEnabled = true }
            }
        }

        ivRecordStop.setOnClickListener {
            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false
            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                camera2Helper.stopRecording()
                onStopRecordButtonClick()
                // Re-enable click listener after recording is taken
                it.post { it.isEnabled = true; camera2Helper.initializeCamera(CAMERA_SIZE_HIGH.width, CAMERA_SIZE_HIGH.height) }
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
        if (camera2Helper.isRecording) {
            camera2Helper.stopRecording()
        } else {
            camera2Helper.closeCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        camera2Helper.stopCameraThread()
    }

    companion object {
        private val TAG = BaseCamera2Fragment::class.java.simpleName

        val CAMERA_SIZE_EXTRA = Size(1080, 1920)
        val CAMERA_SIZE_HIGH = Size(720, 1280)
        val CAMERA_SIZE_NORMAL = Size(720, 960)
        val CAMERA_SIZE_LOW = Size(480, 640)
    }
}

interface BackPressedListener {
    fun onBackPressed()
}