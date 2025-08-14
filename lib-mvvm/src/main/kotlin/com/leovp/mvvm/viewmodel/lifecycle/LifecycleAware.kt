package com.leovp.mvvm.viewmodel.lifecycle

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
