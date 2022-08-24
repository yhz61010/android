package com.leovp.demo.basiccomponents.examples

import android.content.res.Configuration
import android.os.Bundle
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivitySaveInstanceStateBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class SaveInstanceStateActivity : BaseDemonstrationActivity<ActivitySaveInstanceStateBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivitySaveInstanceStateBinding {
        return ActivitySaveInstanceStateBinding.inflate(layoutInflater)
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
