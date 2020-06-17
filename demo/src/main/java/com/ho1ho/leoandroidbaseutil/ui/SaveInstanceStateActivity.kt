package com.ho1ho.leoandroidbaseutil.ui

import android.content.res.Configuration
import android.os.Bundle
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity

class SaveInstanceStateActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CLog.w(ITAG, "=====> onCreate <=====")
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
        CLog.w(ITAG, "=====> onConfigurationChanged <=====")
        super.onConfigurationChanged(newConfig)
    }

    override fun onUserLeaveHint() {
        CLog.w(ITAG, "=====> onUserLeaveHint <=====")
        super.onUserLeaveHint()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        CLog.w(ITAG, "=====> onSaveInstanceState <=====")
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        CLog.w(ITAG, "=====> onRestoreInstanceState <=====")
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onStart() {
        CLog.w(ITAG, "=====> onStart <=====")
        super.onStart()
    }

    override fun onResume() {
        CLog.w(ITAG, "=====> onResume <=====")
        super.onResume()
    }

    override fun onStop() {
        CLog.w(ITAG, "=====> onStop <=====")
        CLog.flushLog(false)
        super.onStop()
    }

    override fun onDestroy() {
        CLog.w(ITAG, "=====> onDestroy <=====")
        super.onDestroy()
    }
}