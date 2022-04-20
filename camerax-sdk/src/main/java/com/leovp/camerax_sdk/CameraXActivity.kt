package com.leovp.camerax_sdk

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.leovp.camerax_sdk.databinding.ActivityCameraxMainBinding
import com.leovp.camerax_sdk.fragments.CameraFragment
import com.leovp.lib_common_android.exts.hideNavigationBar
import com.leovp.lib_common_android.exts.requestFullScreenAfterVisible
import com.leovp.lib_common_android.exts.requestFullScreenBeforeSetContentView

class CameraXActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraxMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreenBeforeSetContentView()
        super.onCreate(savedInstanceState)
        binding = ActivityCameraxMainBinding.inflate(layoutInflater).apply { setContentView(root) }
    }

    override fun onResume() {
        super.onResume()
        // Call [requestFullScreenAfterVisible] method after activity is visual.
        requestFullScreenAfterVisible()
        // Generally, call this method in `onResume` to let navigation bar always hide.
        hideNavigationBar()
    }

    /** When key down event is triggered, relay it via local broadcast so fragments can handle it */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container_camerax) as NavHostFragment
                val cameraFragment: CameraFragment = navHostFragment.childFragmentManager.primaryNavigationFragment as CameraFragment
                cameraFragment.functionKey.value = keyCode
                true
            }
            else                         -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }
}