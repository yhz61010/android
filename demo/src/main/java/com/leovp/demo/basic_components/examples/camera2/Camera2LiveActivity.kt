package com.leovp.demo.basic_components.examples.camera2

import android.app.Activity
import android.content.Intent
import android.media.MediaFormat
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.media.CameraUtil
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.camera2live.view.BackPressedListener
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.lib_common_android.exts.hideNavigationBar
import com.leovp.lib_common_android.exts.requestFullScreenAfterVisible
import com.leovp.lib_common_android.exts.requestFullScreenBeforeSetContentView
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import java.io.File
import java.io.FileOutputStream

class Camera2LiveActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    private val cameraViewFragment = Camera2LiveFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreenBeforeSetContentView()
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_camera2_live)

        CodecUtil.getCodecListByMimeType(MediaFormat.MIMETYPE_VIDEO_AVC).forEach { LogContext.log.i(TAG, "H264 Encoder: ${it.name}") }
        val hasTopazEncoder = CodecUtil.hasCodecByName(MediaFormat.MIMETYPE_VIDEO_AVC, "OMX.IMG.TOPAZ.VIDEO.Encoder")
        LogContext.log.d(TAG, "hasTopazEncoder=$hasTopazEncoder")

        cameraViewFragment.backPressListener = object :
            BackPressedListener {
            override fun onBackPressed() {
                this@Camera2LiveActivity.onBackPressed()
            }
        }

        if (XXPermissions.isGranted(this, Permission.CAMERA)) {
            addFragment()
        } else {
            XXPermissions.with(this)
                .permission(Permission.CAMERA)
                .request(object : OnPermissionCallback {
                    override fun onGranted(granted: MutableList<String>?, all: Boolean) {
                        toast("Grant camera permission")
                        addFragment()
                    }

                    override fun onDenied(denied: MutableList<String>?, never: Boolean) {
                        toast("Deny camera permission")
                        finish()
                    }
                })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (CameraUtil.REQUEST_CODE_OPEN_GALLERY == requestCode && resultCode == Activity.RESULT_OK) {
            LogContext.log.i(TAG, "OPEN_GALLERY onActivityResult")
//            CameraUtil.handleImageAboveKitKat(this, data).forEach { LogContext.log.i(TAG, "Selected image=$it") }
            // The following code is just for demo. The exception is not considered.
            data?.data?.let {
                // In Android 10+, I really do not know how to get the file real path.
                // According to the post [https://stackoverflow.com/a/2790688],
                // there is no need for us to know the real path. I just need to get the InputStream directly. That's enough.
                // Set uri for ImageView
//                ImageView(this).setImageURI(uri)
                // Or get file input stream.
                val inputStream = contentResolver.openInputStream(it)!!
                val outputStream = FileOutputStream(File(getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath!!, "os.jpg"))
                inputStream.copyTo(outputStream)
                LogContext.log.i(TAG, "Image stream has been copied to FileOutputStream")
            }
        }
    }

    private fun addFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.cameraFragment, cameraViewFragment, "cameraview")
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        requestFullScreenAfterVisible()
        hideNavigationBar()
    }

    override fun onDestroy() {
        supportFragmentManager.beginTransaction()
            .remove(cameraViewFragment)
            .commitAllowingStateLoss()
        super.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    companion object {
        private const val TAG = "Camera2LiveActivity"
    }
}