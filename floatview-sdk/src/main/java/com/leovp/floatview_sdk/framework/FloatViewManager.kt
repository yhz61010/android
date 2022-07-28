package com.leovp.floatview_sdk.framework

import android.content.Context
import com.leovp.floatview_sdk.entities.DefaultConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Author: Michael Leo
 * Date: 2021/8/30 10:52
 */
internal object FloatViewManager {
    private val windowMap = ConcurrentHashMap<String, FloatViewImpl>()

    fun create(context: Context, config: DefaultConfig) {
        if (!windowMap.containsKey(config.tag)) {
            windowMap[config.tag] = FloatViewImpl(context, config)
        } else {
            throw IllegalAccessError("Float view tag[${config.tag}] has already exist. Can't recreate it!")
        }
    }

    fun exist(tag: String): Boolean = windowMap[tag] != null

    fun allFloatViewTags(): List<String> = windowMap.map { it.key }

    fun show(tag: String) = windowMap[tag]?.show()

    fun remove(tag: String, immediately: Boolean = false) {
        windowMap[tag]?.remove(immediately)
        windowMap.remove(tag)
    }

    fun remove(immediately: Boolean = false) {
        //        Call requires API level 24 (current min is 21): java.lang.Iterable#forEach
        //        windowMap.forEach { (_, floatViewImpl) -> floatViewImpl.dismiss() }
        for ((tag, _) in windowMap) remove(tag, immediately)
        windowMap.clear()
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