@file:Suppress("unused")

package com.leovp.reflection.wrappers

import android.os.IInterface
import android.util.Log
import android.view.Display
import android.view.IRotationWatcher
import java.lang.reflect.Method

class WindowManager(private val manager: IInterface) {
    companion object {
        private const val TAG = "WindowManager"
    }

    private var getRotationMethod: Method? = null
    private var freezeRotationMethod: Method? = null
    private var isRotationFrozenMethod: Method? = null
    private var thawRotationMethod: Method? = null

    private fun getGetRotationMethod(): Method? {
        if (getRotationMethod == null) {
            val cls: Class<*> = manager.javaClass
            getRotationMethod = runCatching {
                // method changed since this commit:
                // https://android.googlesource.com/platform/frameworks/base/+/8ee7285128c3843401d4c4d0412cd66e86ba49e3%5E%21/#F2
                cls.getMethod("getDefaultDisplayRotation")
            }.getOrDefault(
                // NoSuchMethodException
                // old version
                runCatching {
                    cls.getMethod("getRotation")
                }.getOrNull()
            )
        }
        return getRotationMethod
    }

    private fun getFreezeRotationMethod(): Method? {
        if (freezeRotationMethod == null) {
            freezeRotationMethod =
                manager.javaClass.getMethod("freezeRotation", Int::class.javaPrimitiveType)
        }
        return freezeRotationMethod
    }

    private fun getIsRotationFrozenMethod(): Method? {
        if (isRotationFrozenMethod == null) {
            isRotationFrozenMethod = manager.javaClass.getMethod("isRotationFrozen")
        }
        return isRotationFrozenMethod
    }

    private fun getThawRotationMethod(): Method? {
        if (thawRotationMethod == null) {
            thawRotationMethod = manager.javaClass.getMethod("thawRotation")
        }
        return thawRotationMethod
    }

    fun getRotation(): Int =
        runCatching { (getGetRotationMethod()?.invoke(manager) as? Int) ?: 0 }.getOrDefault(0)

    fun freezeRotation(rotation: Int) = runCatching { freezeRotationMethod?.invoke(manager, rotation) }

    fun isRotationFrozen(): Boolean {
        return runCatching { (getIsRotationFrozenMethod()?.invoke(manager) as? Boolean) ?: false }
            .getOrDefault(false)
    }

    fun thawRotation() = runCatching { getThawRotationMethod()?.invoke(manager) }

    fun registerRotationWatcher(
        rotationWatcher: IRotationWatcher,
        displayId: Int = Display.DEFAULT_DISPLAY
    ) {
        try {
            val cls: Class<*> = manager.javaClass
            runCatching {
                // display parameter added since this commit:
                // https://android.googlesource.com/platform/frameworks/base/+/35fa3c26adcb5f6577849fd0df5228b1f67cf2c6%5E%21/#F1
                // API 26 or above
                cls.getMethod(
                    "watchRotation",
                    IRotationWatcher::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(manager, rotationWatcher, displayId)
            }.onFailure {
                // NoSuchMethodException
                // old version
                cls.getMethod("watchRotation", IRotationWatcher::class.java)
                    .invoke(manager, rotationWatcher)
            }
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    /**
     * Stop monitoring for changes when you're done.
     */
    fun removeRotationWatcher(rotationWatcher: IRotationWatcher) {
        try {
            val cls: Class<*> = manager.javaClass
            cls.getMethod("removeRotationWatcher", IRotationWatcher::class.java)
                .invoke(manager, rotationWatcher)
        } catch (ignored: NoSuchMethodException) {
            Log.e(TAG, "removeRotationWatcher exception.")
        }
    }
}
