package com.leovp.androidbase.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Process
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.leovp.androidbase.BuildConfig
import com.leovp.androidbase.utils.device.DeviceProp
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import java.io.File
import kotlin.system.exitProcess

/**
 * Author: Michael Leo
 * Date: 19-7-22 下午7:34
 */
@Suppress("unused")
object AppUtil {
    private const val TAG = "AppUtil"

    /**
     * Return the version name of empty string if can't get version string.
     */
    fun getVersionName(ctx: Context) =
        ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_CONFIGURATIONS).versionName ?: ""

    /**
     * Return the version code or 0 if can't get version code.
     */
    fun getVersionCode(ctx: Context): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ctx.packageManager.getPackageInfo(
                ctx.packageName,
                PackageManager.GET_CONFIGURATIONS
            ).longVersionCode
        } else {
            ctx.packageManager.getPackageInfo(
                ctx.packageName,
                PackageManager.GET_CONFIGURATIONS
            ).versionCode.toLong()
        }
    }

    fun ignoreDuplicateStartSplash(act: Activity): Boolean {
        return if (act.intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT > 0) {
            act.finish()
            true
        } else false
    }

    @SuppressLint("PrivateApi")
    fun hasNavigationBar(ctx: Context): Boolean {
        var hasNavigationBar = false
        val rs = ctx.resources
        val id = rs.getIdentifier("config_showNavigationBar", "bool", "android")
        if (id > 0) hasNavigationBar = rs.getBoolean(id)
        val navBarOverride = DeviceProp.getAndroidProperty("qemu.hw.mainkeys")
        if ("1" == navBarOverride) {
            hasNavigationBar = false
        } else if ("0" == navBarOverride) {
            hasNavigationBar = true
        }
        return hasNavigationBar
    }

    /**
     * This method must be called before setContentView.
     *
     * @param act The activity
     *
     * Example:
     * ```kotlin
     * AppUtil.requestFullScreen(this)
     * AppUtil.hideNavigationBar(this)
     * setContentView(R.layout.activity_splash)
     * ```
     */
    fun requestFullScreen(act: Activity) {
        act.requestWindowFeature(Window.FEATURE_NO_TITLE)
        act.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // or
        // act.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * This method must be called before setContentView.
     *
     * @param act The activity
     *
     * Example:
     * ```kotlin
     * AppUtil.requestFullScreen(this)
     * AppUtil.hideNavigationBar(this)
     * setContentView(R.layout.activity_splash)
     * ```
     */
    @SuppressLint("ObsoleteSdkInt")
    fun hideNavigationBar(act: Activity) {
        hideNavigationBar(act.window)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun hideNavigationBar(win: Window) {
        // Translucent virtual NavigationBar
        win.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        // https://blog.csdn.net/c15522627353/article/details/52452490
        // https://blog.csdn.net/lyabc123456/article/details/88683425
        // Always hide virtual navigation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            win.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            // https://blog.csdn.net/qiyei2009/article/details/74435809
            win.navigationBarColor = Color.TRANSPARENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // https://blog.csdn.net/weixin_37997371/article/details/83536953
                val lp = win.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                win.attributes = lp
            }
        } else {
            val v = win.decorView
            v.systemUiVisibility = View.GONE
        }
    }

    fun dp2px(ctx: Context, dipValue: Float) = (dipValue * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun px2dp(ctx: Context, pxValue: Float) = (pxValue / ctx.resources.displayMetrics.density + 0.5f).toInt()

    /**
     * Get meta data in Activity or Application.<br></br>
     * Notice that, if you want to get meta data in Service or Broadcast using [.getMetaData] instead.
     *
     * **Attention:** It's not working at all.
     *
     * @param ctx The context of Activity or Application
     * @param key The meta data key
     * @return The value of meta data
     */
    @Suppress("unused")
    fun getMetaData(ctx: Context, key: String): String? {
        return getMetaData<Any>(ctx, key, null)
    }

    /**
     * Get meta data in all scope including Activity, Application, Service and Broadcast.<br></br>
     *
     * **Attention:** It's not working at all.
     * @param ctx   The context
     * @param key   The meta data key
     * @param clazz The class of Service and Broadcast. For Activity or Application, set `null` to this parameter.
     * @return The value of meta data
     */
    @Suppress("unused")
    fun <T> getMetaData(ctx: Context, key: String, clazz: Class<T>?): String? {
        var metaData: String? = ""
        try {
            if (ctx is Activity) {
                val info = ctx.getPackageManager().getActivityInfo(ctx.componentName, PackageManager.GET_META_DATA)
                metaData = info.metaData.getString(key)
                return metaData
            }
            if (ctx is Application) {
                val info =
                    ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA)
                metaData = info.metaData.getString(key)
                return metaData
            }
            if (clazz != null && ctx is Service) {
                val cn = ComponentName(ctx, clazz)
                val info = ctx.getPackageManager().getServiceInfo(cn, PackageManager.GET_META_DATA)
                metaData = info.metaData.getString(key)
                return metaData
            }

            // BroadcastReceiver
            if (clazz != null && "android.content.BroadcastReceiver" == clazz.simpleName) {
                val cn = ComponentName(ctx, clazz)
                val info = ctx.packageManager.getReceiverInfo(cn, PackageManager.GET_META_DATA)
                metaData = info.metaData.getString(key)
                return metaData
            }
        } catch (e: Exception) {
            metaData = ""
            return metaData
        } finally {
            LogContext.log.d(TAG, "metaData=$metaData")
        }
        return metaData
    }

    // https://stackoverflow.com/questions/4604239/install-application-programmatically-on-android
    @Suppress("unused")
    fun installApk(ctx: Context, file: File) {
        try {
            LogContext.log.i(TAG, "installApk uri: $file")
            val intent = Intent(Intent.ACTION_VIEW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val downloadedApkUri = FileUtil.getFileUri(ctx, file)
                intent.setDataAndType(downloadedApkUri, "application/vnd.android.package-archive")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            LogContext.log.e(TAG, "installApk error", e)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Suppress("unused")
    fun exitApp(ctx: Context) {
        LogContext.log.w(TAG, "=====> exitApp() <=====")
        try {
            val activityManager = ctx.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appTaskList = activityManager.appTasks
            for (appTask in appTaskList) {
                appTask.finishAndRemoveTask()
            }
            exitProcess(0)
        } catch (e: Exception) {
            LogContext.log.e(TAG, "exitApp error.", e)
        }
    }

    @Suppress("unused")
    fun startApp(ctx: Context) {
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(BuildConfig.LIBRARY_PACKAGE_NAME)
        launchIntent?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            ctx.startActivity(it)
        }
    }

    @Suppress("unused")
    fun restartApp(context: Context, targetIntent: Intent) {
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(targetIntent)
        Process.killProcess(Process.myPid())
//        Runtime.getRuntime().exit(0)
    }

    /**
     * Remove Android P warning dialog.
     *
     * Detected problems with API compatibility(visit g.co/dev/appcompat for more info
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun closeAndroidPDialog() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
            LogContext.log.w(TAG, "Not Android 9. Do not closeAndroidPDialog")
            return
        }
        LogContext.log.w(TAG, "closeAndroidPDialog on Android 9")
        try {
            val aClass =
                Class.forName("android.content.pm.PackageParser\$Package")
            val declaredConstructor =
                aClass.getDeclaredConstructor(
                    String::class.java
                )
            declaredConstructor.isAccessible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val cls = Class.forName("android.app.ActivityThread")
            val declaredMethod = cls.getDeclaredMethod("currentActivityThread")
            declaredMethod.isAccessible = true
            val activityThread = declaredMethod.invoke(null)
            val mHiddenApiWarningShown =
                cls.getDeclaredField("mHiddenApiWarningShown")
            mHiddenApiWarningShown.isAccessible = true
            mHiddenApiWarningShown.setBoolean(activityThread, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =============================================================================================

    fun openSoftKeyboard(context: Activity) {
        val imm =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    fun closeSoftKeyboard(context: Activity) {
        val view = context.window.peekDecorView()
        if (view != null) {
            val imm =
                context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}