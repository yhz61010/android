package com.leovp.demo.jetpack_components.examples.camerax

import android.net.Uri
import com.leovp.camerax_sdk.CameraXActivity
import com.leovp.camerax_sdk.listeners.CaptureImageListener
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

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
 *
 * Author: Michael Leo
 * Date: 2022/4/25 14:50
 */
class CameraXDemoActivity : CameraXActivity() {
    override fun allowToOutputCaptureFile() = true

    /** You can implement `CaptureImageListener` or `SimpleCaptureImageListener` */
    override var captureImageListener: CaptureImageListener? = object : CaptureImageListener {
        override fun onSavedImageUri(savedUri: Uri) {
            LogContext.log.w(ITAG, "onSavedImageUri uri=$savedUri")
        }

        override fun onSavedImageBytes(imageBytes: ByteArray, width: Int, height: Int) {
            LogContext.log.w(ITAG, "onSavedImageBytes image[${imageBytes.size}] $width x $height")
        }
    }

}