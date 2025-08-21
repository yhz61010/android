package com.leovp.mvvm.viewmodel.lifecycle

import com.leovp.log.base.LogOutType
import com.leovp.log.base.w

/**
 * Author: Michael Leo
 * Date: 2025/4/7 10:09
 */
interface LifecycleAware {
    fun onResume() {}
    fun onPause() {}
    fun onStart() {}
    fun onStop() {}
    fun onDestroy() {}
}

open class SimpleLifecycleAware : LifecycleAware {
    companion object {
        private const val TAG = "BaseLifecycle"
    }

    override fun onResume() {
        super.onResume()
        w {
            tag = TAG
            message = "onResume()"
            outputType = LogOutType.FRAMEWORK
        }
    }

    override fun onPause() {
        super.onPause()
        w {
            tag = TAG
            message = "onPause()"
            outputType = LogOutType.FRAMEWORK
        }
    }

    override fun onStart() {
        super.onStart()
        w {
            tag = TAG
            message = "onStart()"
            outputType = LogOutType.FRAMEWORK
        }
    }

    override fun onStop() {
        super.onStop()
        w {
            tag = TAG
            message = "onStop()"
            outputType = LogOutType.FRAMEWORK
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        w {
            tag = TAG
            message = "onDestroy()"
            outputType = LogOutType.FRAMEWORK
        }
    }
}
