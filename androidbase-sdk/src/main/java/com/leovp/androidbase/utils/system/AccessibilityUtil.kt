@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.androidbase.utils.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.leovp.log_sdk.LogContext
import java.lang.ref.WeakReference


/**
 * `AccessibilityUtil` must be initialized in your custom `AccessibilityService`.
 *
 * https://github.com/PopFisher/AccessibilitySample
 * https://www.jianshu.com/p/27df6983321f
 * https://www.jianshu.com/p/cd1cd53909d7
 *
 * Author: Michael Leo
 * Date: 21-3-23 上午10:11
 */
object AccessibilityUtil {
    private lateinit var weakService: WeakReference<AccessibilityService>
    private const val TAG = "Acce"

    /**
     * This method must be called in your AccessibilityService's onAccessibilityEvent method.
     */
    fun init(service: AccessibilityService) {
        if (!::weakService.isInitialized) {
            synchronized(this) {
                if (!::weakService.isInitialized) {
                    weakService = WeakReference(service)
                }
            }
        }
    }

    /**
     * Usage:
     * ```
     * if (!AccessibilityUtil.isAccessibilityEnabled(SimulatedClickService::class.java)) {
     *     startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
     * }
     * ```
     */
    fun isAccessibilityEnabled(): Boolean {
        if (!::weakService.isInitialized) {
            LogContext.log.e(TAG, "You must call init method first.")
            return false
        }
        val accessibilityService = weakService.get() ?: return false
        var accessibilityEnabled = 0
        runCatching {
            accessibilityEnabled = Settings.Secure.getInt(accessibilityService.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        }.onFailure { it.printStackTrace() }
        val packageName: String = accessibilityService.packageName
        val serviceCanonicalName = "$packageName/${accessibilityService.javaClass.canonicalName}"
        LogContext.log.d("serviceCanonicalName: $serviceCanonicalName")
        if (accessibilityEnabled == 1) {
            val settingValue: String? = Settings.Secure.getString(accessibilityService.applicationContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            settingValue?.split(':', ignoreCase = true)?.forEach { value ->
                if (value.equals(serviceCanonicalName, ignoreCase = true)) return true
            }
        }
        return false
    }

    // nodeInfo = accessibilityService.rootInActiveWindow

    /**
     * @param resourceId Resource id is "foo.bar:id/baz"
     */
    fun findNodesById(resourceId: String): List<AccessibilityNodeInfo>? {
        if (!::weakService.isInitialized) {
            LogContext.log.e(TAG, "You must call init method first.")
            return null
        }
        return weakService.get()?.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(resourceId)
    }

    /**
     * @param text The text to be searched.
     */
    fun findNodesByText(text: String): List<AccessibilityNodeInfo>? {
        if (!::weakService.isInitialized) {
            LogContext.log.e(TAG, "You must call init method first.")
            return null
        }
        return weakService.get()?.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
    }

    private fun performClick(nodesInfo: List<AccessibilityNodeInfo>?): Boolean { // As of API level 24
        //        nodesInfo?.forEach { node ->
        //            if (node.isEnabled) return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        //        }
        nodesInfo?.let { list ->
            for (node in list) {
                if (node.isEnabled) return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
            }
        }
        return false
    }

    /**
     * @param resourceId Resource id is "foo.bar:id/baz"
     */
    fun clickById(resourceId: String): Boolean = performClick(findNodesById(resourceId))

    /**
     * @param text The text to be searched.
     */
    fun clickByText(text: String): Boolean = performClick(findNodesByText(text))

    fun clickBackKey(): Boolean = performFunctionKey(AccessibilityService.GLOBAL_ACTION_BACK)

    fun clickHomeKey(): Boolean = performFunctionKey(AccessibilityService.GLOBAL_ACTION_HOME)

    fun clickRecentKey(): Boolean = performFunctionKey(AccessibilityService.GLOBAL_ACTION_RECENTS)

    fun openNotification(): Boolean = performFunctionKey(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    fun performFunctionKey(action: Int): Boolean {
        if (!::weakService.isInitialized) {
            LogContext.log.e(TAG, "You must call init method first.")
            return false
        }
        return weakService.get()?.performGlobalAction(action) ?: false
    }

    /**
     * @param editTextResourceId Resource id is "foo.bar:id/baz"
     */
    fun setTextById(text: String, editTextResourceId: String): Boolean {
        val editText: AccessibilityNodeInfo? = findNodesById(editTextResourceId)?.firstOrNull()
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return runCatching {
            editText?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) ?: false
        }.getOrDefault(false)
    }

    fun scrollBackwardById(resourceId: String): Boolean = runCatching {
        findNodesById(resourceId)?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false
    }.getOrDefault(false)

    fun scrollForwardById(resourceId: String): Boolean = runCatching {
        findNodesById(resourceId)?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
    }.getOrDefault(false)

    fun jumpToSettingPage(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}