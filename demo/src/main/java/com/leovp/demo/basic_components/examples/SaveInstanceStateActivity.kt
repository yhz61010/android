package com.leovp.demo.basic_components.examples

import android.content.res.Configuration
import android.os.Bundle
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class SaveInstanceStateActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    override fun onCreate(savedInstanceState: Bundle?) {
        LogContext.log.w(ITAG, "=====> onCreate <=====")
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
        LogContext.log.w(ITAG, "=====> onConfigurationChanged <=====")
        super.onConfigurationChanged(newConfig)
    }

    override fun onUserLeaveHint() {
        LogContext.log.w(ITAG, "=====> onUserLeaveHint <=====")
        super.onUserLeaveHint()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        LogContext.log.w(ITAG, "=====> onSaveInstanceState <=====")
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        LogContext.log.w(ITAG, "=====> onRestoreInstanceState <=====")
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onStart() {
        LogContext.log.w(ITAG, "=====> onStart <=====")
        super.onStart()
    }

    override fun onResume() {
        LogContext.log.w(ITAG, "=====> onResume <=====")
        super.onResume()
    }

    override fun onStop() {
        LogContext.log.w(ITAG, "=====> onStop <=====")
        super.onStop()
    }

    override fun onDestroy() {
        LogContext.log.w(ITAG, "=====> onDestroy <=====")
        super.onDestroy()
    }
}