package com.leovp.floatview.framework

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle

/** Binds Activity-backed windows to their owner, including wrapped Activity contexts. */
internal class FloatViewOwner(context: Context, private val onDestroyed: () -> Unit) :
    Application.ActivityLifecycleCallbacks {
    private val activity = findActivity(context)

    val isAlive: Boolean
        get() = activity?.let { !it.isFinishing && !it.isDestroyed } ?: true

    init {
        check(isAlive) { "Cannot create a float view for a finishing or destroyed Activity" }
        activity?.application?.registerActivityLifecycleCallbacks(this)
    }

    fun release() {
        activity?.application?.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === this.activity) onDestroyed()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return null
    }
}
