package com.leovp.camerax

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.leovp.camerax.sdk.R
import com.leovp.camerax.sdk.databinding.ActivityCameraxMainBinding
import com.leovp.camerax.enums.CapturedImageStrategy
import com.leovp.camerax.fragments.CameraFragment
import com.leovp.camerax.listeners.CaptureImageListener
import com.leovp.camerax.listeners.impl.SimpleCaptureImageListener
import com.leovp.android.exts.hideNavigationBar
import com.leovp.android.exts.requestFullScreenAfterVisible
import com.leovp.android.exts.requestFullScreenBeforeSetContentView

/**
 * Add following `<activity>` in your `<AndroidManifest.xml>`.
 *
 * ```xml
 * <activity android:name=".jetpack_components.examples.camerax.CameraXDemoActivity"
 *     android:configChanges="orientation|screenLayout|screenSize|smallestScreenSize"
 *     android:exported="false"
 *     android:resizeableActivity="true"
 *     android:rotationAnimation="seamless"
 *     android:screenOrientation="userPortrait"
 *     android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar"
 *     tools:ignore="LockedOrientationActivity"
 *     tools:targetApi="O">
 *     <!-- Declare notch support -->
 *     <meta-data android:name="android.notch_support"
 *     android:value="true" />
 * </activity>
 * ```
 */
open class CameraXActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraxMainBinding

    open fun getCaptureListener(): CaptureImageListener {
        return SimpleCaptureImageListener()
    }

    open fun getOutputCapturedImageStrategy(): CapturedImageStrategy = CapturedImageStrategy.FILE

    override fun onCreate(savedInstanceState: Bundle?) {
        requestFullScreenBeforeSetContentView()
        super.onCreate(savedInstanceState)
        binding = ActivityCameraxMainBinding.inflate(layoutInflater).apply { setContentView(root) }
        getCameraFragment()?.let { fragment ->
            fragment.captureImageListener = getCaptureListener()
            fragment.outputCapturedImageStrategy = getOutputCapturedImageStrategy()
        }

        onBackPressedDispatcher.addCallback(this, true) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
                // (https://issuetracker.google.com/issues/139738913)
                finishAfterTransition()
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Call [requestFullScreenAfterVisible] method after activity is visual.
        requestFullScreenAfterVisible()
        // Generally, call this method in `onResume` to let navigation bar always hide.
        hideNavigationBar()
    }

    private fun getCameraFragment(): CameraFragment? {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container_camerax) as NavHostFragment
        return navHostFragment.childFragmentManager.primaryNavigationFragment as? CameraFragment
    }

    /** When key down event is triggered, relay it via local broadcast so fragments can handle it */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                getCameraFragment()?.run {
                    functionKey.value = keyCode
                    return true
                }
                return false
            }
            else -> super.onKeyDown(
                keyCode,
                event
            )
        }
    }
}
