@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.reflection

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.*

/**
 * Author: Michael Leo
 * Date: 2022/10/17 17:12
 */
object ActivityReflect {
    private val mActivityList: LinkedList<Activity> = LinkedList()

    /**
     * Return whether the activity is alive.
     *
     * @param context The context.
     * @return `true`: yes<br></br>`false`: no
     */
    fun isActivityAlive(context: Context?): Boolean {
        return isActivityAlive(getActivityByContext(context))
    }

    /**
     * Return whether the activity is alive.
     *
     * @param activity The activity.
     * @return `true`: yes<br></br>`false`: no
     */
    fun isActivityAlive(activity: Activity?): Boolean {
        return (activity != null && !activity.isFinishing && !activity.isDestroyed)
    }

    fun getActivityByContext(context: Context?): Activity? {
        if (context == null) return null
        val activity = getActivityByContextInner(context)
        return if (!isActivityAlive(activity)) null else activity
    }

    private fun getActivityByContextInner(context: Context?): Activity? {
        var ctx = context ?: return null
        val list: MutableList<Context?> = ArrayList()
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            val activity = getActivityFromDecorContext(ctx)
            if (activity != null) return activity
            list.add(ctx)
            ctx = ctx.baseContext
            if (list.contains(ctx)) {
                // loop context
                return null
            }
        }
        return null
    }

    private fun getActivityFromDecorContext(context: Context?): Activity? {
        if (context == null) return null
        if (context.javaClass.name == "com.android.internal.policy.DecorContext") {
            runCatching {
                val mActivityContextField = context.javaClass.getDeclaredField("mActivityContext")
                mActivityContextField.isAccessible = true
                return (mActivityContextField.get(context) as WeakReference<*>).get() as Activity
            }
        }
        return null
    }

    // ==============================
    // ==============================
    // ==============================

    fun getTopActivity(): Activity? {
        mActivityList.clear()
        val activityList: List<Activity> = getActivityList()
        for (activity in activityList) {
            if (!isActivityAlive(activity)) {
                continue
            }
            return activity
        }
        return null
    }

    fun getActivityList(): List<Activity> {
        if (!mActivityList.isEmpty()) {
            return LinkedList(mActivityList)
        }
        val reflectActivities: List<Activity> = getActivitiesByReflect()
        mActivityList.addAll(reflectActivities)
        return LinkedList(mActivityList)
    }

    /**
     * @return the activities which topActivity is first position
     */
    private fun getActivitiesByReflect(): List<Activity> {
        val list: LinkedList<Activity> = LinkedList()
        var topActivity: Activity? = null
        runCatching {
            val activityThread: Any = runCatching { getActivityThread() }.getOrNull() ?: return list
            val mActivitiesField: Field = activityThread.javaClass.getDeclaredField("mActivities")
            mActivitiesField.isAccessible = true
            val mActivities: Any = mActivitiesField.get(activityThread) as? Map<*, *> ?: return list
            val binderActivityClientRecordMap = mActivities as Map<*, *>
            for (activityRecord in binderActivityClientRecordMap.values) {
                requireNotNull(activityRecord)
                val activityClientRecordClass: Class<*> = activityRecord.javaClass
                val activityField: Field = activityClientRecordClass.getDeclaredField("activity")
                activityField.isAccessible = true
                val activity = activityField.get(activityRecord) as Activity
                if (topActivity == null) {
                    val pausedField: Field = activityClientRecordClass.getDeclaredField("paused")
                    pausedField.isAccessible = true
                    if (!pausedField.getBoolean(activityRecord)) {
                        topActivity = activity
                    } else {
                        list.addFirst(activity)
                    }
                } else {
                    list.addFirst(activity)
                }
            }
        }
        if (topActivity != null) {
            list.addFirst(topActivity)
        }
        return list
    }

    private fun getActivityThread(): Any {
        return runCatching { getActivityThreadInActivityThreadStaticField() }.getOrElse {
            getActivityThreadInActivityThreadStaticMethod()
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun getActivityThreadInActivityThreadStaticField(): Any {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val sCurrentActivityThreadField: Field = activityThreadClass.getDeclaredField("sCurrentActivityThread")
            sCurrentActivityThreadField.isAccessible = true
            sCurrentActivityThreadField.get(null)
        } catch (e: Exception) {
            error(e)
        }
    }

    @SuppressLint("PrivateApi")
    private fun getActivityThreadInActivityThreadStaticMethod(): Any {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            activityThreadClass.getMethod("currentActivityThread").invoke(null)
        } catch (e: Exception) {
            error(e)
        }
    }
}
