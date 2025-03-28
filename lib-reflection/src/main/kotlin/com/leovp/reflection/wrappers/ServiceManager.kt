@file:Suppress("unused")

package com.leovp.reflection.wrappers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import androidx.annotation.RequiresApi
import java.lang.reflect.Method

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
object ServiceManager {
    const val PACKAGE_NAME = "com.android.shell"
    const val USER_ID = 0

    private var getServiceMethod: Method? = null

    init {
        getServiceMethod = try {
            Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String::class.java)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    var windowManager: WindowManager? = null
        get() {
            if (field == null) {
                field = WindowManager(getService(Context.WINDOW_SERVICE, "android.view.IWindowManager"))
            }
            return field
        }
        private set

    var displayManager: DisplayManager? = null
        get() {
            if (field == null) {
                field =
                    DisplayManager(
                        getService(
                            Context.DISPLAY_SERVICE,
                            "android.hardware.display.IDisplayManager"
                        )
                    )
            }
            return field
        }
        private set

    var inputManager: InputManager? = null
        get() {
            if (field == null) {
                field = InputManager(getService(Context.INPUT_SERVICE, "android.hardware.input.IInputManager"))
            }
            return field
        }
        private set

    var powerManager: PowerManager? = null
        get() {
            if (field == null) {
                field = PowerManager(getService(Context.POWER_SERVICE, "android.os.IPowerManager"))
            }
            return field
        }
        private set

    var statusBarManager: StatusBarManager? = null
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        get() {
            if (field == null) {
                field =
                    StatusBarManager(
                        getService(
                            Context.STATUS_BAR_SERVICE,
                            "com.android.internal.statusbar.IStatusBarService"
                        )
                    )
            }
            return field
        }
        private set

    // On old Android versions, the ActivityManager is not exposed via AIDL,
    // so use ActivityManagerNative.getDefault()
    var activityManager: ActivityManager? = null
        get() {
            if (field == null) {
                field = try {
                    // On old Android versions, the ActivityManager is not exposed via AIDL,
                    // so use ActivityManagerNative.getDefault()
                    val cls = Class.forName("android.app.ActivityManagerNative")
                    val getDefaultMethod = cls.getDeclaredMethod("getDefault")
                    val am = getDefaultMethod.invoke(null) as IInterface
                    ActivityManager(am)
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            }
            return field
        }
        private set

    private fun getService(service: String, type: String): IInterface {
        return try {
            val binder: IBinder = getServiceMethod!!.invoke(null, service) as IBinder
            val asInterfaceMethod: Method =
                Class.forName("$type\$Stub").getMethod("asInterface", IBinder::class.java)
            asInterfaceMethod.invoke(null, binder) as IInterface
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
}
