package com.leovp.floatview.framework

import android.content.Context
import android.os.Looper
import android.util.Log
import com.leovp.floatview.entities.DefaultConfig
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "FloatViewManager"

/** Window manager operations are only valid on the main thread. */
internal fun checkFloatViewMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "FloatView must run on the main thread"
    }
}

/**
 * Author: Michael Leo
 * Date: 2021/8/30 10:52
 */
internal object FloatViewManager {
    private val windowMap = ConcurrentHashMap<String, FloatViewImpl>()

    /**
     * Creates a float view for [config.tag]. Creation is skipped, with a warning, when [context]
     * belongs to a finishing or destroyed Activity: the window could never be shown, and callers
     * commonly reach this from asynchronous callbacks after the user has already left the screen.
     */
    fun create(context: Context, config: DefaultConfig) {
        checkFloatViewMainThread()
        if (!FloatViewOwner.isContextAlive(context)) {
            Log.w(TAG, "Skip float view tag[${config.tag}]: Activity is finishing or destroyed")
            return
        }
        if (!windowMap.containsKey(config.tag)) {
            val tag = config.tag
            windowMap[tag] = FloatViewImpl(context, config) { removed ->
                if (windowMap[tag] === removed) windowMap.remove(tag)
            }
        } else {
            throw IllegalAccessError(
                "Float view tag[${config.tag}] has already exist. Can't recreate it!"
            )
        }
    }

    fun exist(tag: String): Boolean = windowMap[tag] != null

    fun allFloatViewTags(): List<String> = windowMap.map { it.key }

    fun show(tag: String) = windowMap[tag]?.show()

    fun remove(tag: String, immediately: Boolean = false) {
        windowMap[tag]?.remove(immediately)
    }

    fun remove(immediately: Boolean = false) {
        //        Call requires API level 24 (current min is 21): java.lang.Iterable#forEach
        //        windowMap.forEach { (_, floatViewImpl) -> floatViewImpl.dismiss() }
        for ((tag, _) in windowMap) remove(tag, immediately)
    }

    fun visible(tag: String, show: Boolean) {
        windowMap[tag]?.visible(show)
    }

    fun visibleAll(show: Boolean) {
        for ((tag, _) in windowMap) visible(tag, show)
    }

    fun getFloatViewWidth(tag: String): Int = getConfig(tag)?.customView?.width ?: 0
    fun getFloatViewHeight(tag: String): Int = getConfig(tag)?.customView?.height ?: 0

    fun getFloatViewImpl(tag: String): FloatViewImpl? = windowMap[tag]

    fun getConfig(tag: String): DefaultConfig? = windowMap[tag]?.config
}
