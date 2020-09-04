package com.ho1ho.leoandroidbaseutil.common_components.examples

import android.content.res.Configuration
import android.os.Bundle
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity

class SaveInstanceStateActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        LLog.w(ITAG, "=====> onCreate <=====")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_instance_state)
    }

    /**
     * Only you set following property in `AndroidManifest.xml` for activity, this method will be called.
     * Otherwise, it will not be called.
     *
     * ```xml
     * android:configChanges="orientation|screenSize"
     * ```
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        LLog.w(ITAG, "=====> onConfigurationChanged <=====")
        super.onConfigurationChanged(newConfig)
    }

    override fun onUserLeaveHint() {
        LLog.w(ITAG, "=====> onUserLeaveHint <=====")
        super.onUserLeaveHint()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        LLog.w(ITAG, "=====> onSaveInstanceState <=====")
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        LLog.w(ITAG, "=====> onRestoreInstanceState <=====")
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onStart() {
        LLog.w(ITAG, "=====> onStart <=====")
        super.onStart()
    }

    override fun onResume() {
        LLog.w(ITAG, "=====> onResume <=====")
        super.onResume()
    }

    override fun onStop() {
        LLog.w(ITAG, "=====> onStop <=====")
        super.onStop()
    }

    override fun onDestroy() {
        LLog.w(ITAG, "=====> onDestroy <=====")
        super.onDestroy()
    }
}