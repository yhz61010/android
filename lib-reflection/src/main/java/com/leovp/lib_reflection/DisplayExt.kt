package com.leovp.lib_reflection

import android.annotation.SuppressLint
import android.os.Build
import android.os.IBinder
import android.view.Display
import android.view.IRotationWatcher
import java.lang.reflect.Method

/**
 * Author: Michael Leo
 * Date: 2022/7/28 09:38
 */

// This method seems to have been introduced in Android 4.3, so don't expect to always find it
private var removeRotationWatcher: Method? = null
private lateinit var windowManagerService: Any
private lateinit var screenRotationChanged: IRotationWatcher.Stub

/**
 * https://stackoverflow.com/a/30394613/1685062
 */
@SuppressLint("PrivateApi")
fun watchRotationByReflection(onRotationChanged: (rotation: Int) -> Unit) {
    try {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val serviceBinder =
                serviceManager.getMethod("getService", String::class.java)
                    .invoke(serviceManager, "window") as IBinder
        val stub = Class.forName("android.view.IWindowManager\$Stub")
        windowManagerService =
                stub.getMethod("asInterface", IBinder::class.java).invoke(stub, serviceBinder)!!
        // API 26
        val watchRotation: Method =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) windowManagerService.javaClass.getMethod(
                    "watchRotation",
                    IRotationWatcher::class.java,
                    Int::class.javaPrimitiveType
                ) else windowManagerService.javaClass.getMethod(
                    "watchRotation",
                    IRotationWatcher::class.java
                )

        try {
            removeRotationWatcher =
                    windowManagerService.javaClass.getMethod("removeRotationWatcher",
                        IRotationWatcher::class.java)
        } catch (ignored: NoSuchMethodException) {
            ignored.printStackTrace()
        }

        screenRotationChanged = object : IRotationWatcher.Stub() {
            override fun onRotationChanged(rotation: Int) {
                onRotationChanged.invoke(rotation)
            }
        }

        // Start monitoring for changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) // API 26
            watchRotation.invoke(windowManagerService,
                screenRotationChanged,
                Display.DEFAULT_DISPLAY)
        else
            watchRotation.invoke(windowManagerService, screenRotationChanged)
    } catch (ignored: Exception) {
        ignored.printStackTrace()
    }
}

fun unwatchReflectionRotation() {
    try {
        // Stop monitoring for changes when you're done
        removeRotationWatcher?.invoke(windowManagerService, screenRotationChanged)
    } catch (ignored: Exception) {
        ignored.printStackTrace()
    }
}