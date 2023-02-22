@file:Suppress("unused")

package com.leovp.android.exts

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
import android.content.res.Configuration
import android.graphics.Rect
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.WindowInsets
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import com.leovp.android.utils.API
import com.leovp.android.utils.DeviceProp
import com.leovp.android.utils.DeviceUtil
import java.util.*
import kotlin.math.max
import kotlin.math.min


/**
 * Author: Michael Leo
 * Date: 20-11-30 下午3:43
 */

const val VENDOR_HUAWEI = "HUAWEI"
const val VENDOR_XIAOMI = "xiaomi"
const val VENDOR_OPPO = "OPPO"
const val VENDOR_VIVO = "vivo"
const val VENDOR_ONE_PLUS = "OnePlus"
const val VENDOR_SAMSUNG = "samsung"
const val VENDOR_GOOGLE = "Google"
const val VENDOR_OTHER = "other"

val Context.isHuaWei: Boolean
    get() = VENDOR_HUAWEI.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isXiaoMi: Boolean
    get() = VENDOR_XIAOMI.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isOppo: Boolean
    get() = VENDOR_OPPO.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isOnePlus: Boolean
    get() = VENDOR_ONE_PLUS.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isVivo: Boolean
    get() = VENDOR_VIVO.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isSamsung: Boolean
    get() = VENDOR_SAMSUNG.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)
val Context.isGoogle: Boolean
    get() = VENDOR_GOOGLE.equals(DeviceUtil.getInstance(this).manufacturer, ignoreCase = true)

val Context.densityDpi get(): Int = this.resources.displayMetrics.densityDpi
val Context.density get(): Float = this.resources.displayMetrics.density

/**
 * @return The returned height value includes the height of status bar but excludes the height of navigation bar.
 */
val Context.screenAvailableResolution: Size
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            // Gets all excluding insets
            val windowInsets = metrics.windowInsets
            val insets =
                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())

            val insetsWidth = insets.right + insets.left
            val insetsHeight = insets.top + insets.bottom

            // Legacy size that Display#getSize reports
            val bounds = metrics.bounds
            Size(bounds.width() - insetsWidth, bounds.height() - insetsHeight)
        } else {
            //            val display = wm.defaultDisplay
            //            val size = Point()
            //            display.getSize(size)
            //            size

            //            val display = wm.defaultDisplay
            //            val displayMetrics = DisplayMetrics()
            //            display.getMetrics(displayMetrics)
            //            return Point(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val displayMetrics = resources.displayMetrics
            return Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

val Context.screenRealResolution: Size
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //        this.display?.getRealSize(size)
            val bounds = windowManager.currentWindowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

val Context.screenWidth: Int get() = screenRealResolution.width

val Context.screenRealHeight: Int get() = screenRealResolution.height

/**
 * This height includes the height of status bar but excludes the height of navigation bar.
 */
val Context.screenAvailableHeight: Int get() = screenAvailableResolution.height

/**
 * @param surfaceRotation The value may be:
 *
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 *
 * @return The screen width in current screen orientation. If parameter `surfaceRotation`
 *         is not a valid value, return available height according to the context.
 */
fun Context.getScreenWidth(surfaceRotation: Int, screenSize: Size = screenRealResolution): Int {
    return when (surfaceRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> min(screenSize.width, screenSize.height)
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> max(screenSize.width, screenSize.height)
        else -> screenSize.width
    }
}

/**
 * @param surfaceRotation The value may be:
 *
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 *
 * @return The screen height in current screen orientation. If parameter `surfaceRotation`
 *         is not a valid value, return available height according to the context.
 */
fun Context.getScreenHeight(surfaceRotation: Int, screenSize: Size = screenRealResolution): Int {
    return when (surfaceRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> max(screenSize.width, screenSize.height)
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> min(screenSize.width, screenSize.height)
        else -> screenSize.height
    }
}

/**
 * @param surfaceRotation The value may be:
 *
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 *
 * @return The screen size in current screen orientation. If parameter `surfaceRotation`
 *         is not a valid value, return available height according to the context.
 */
fun Context.getScreenSize(surfaceRotation: Int, screenSize: Size = screenRealResolution): Size {
    return when (surfaceRotation) {
        Surface.ROTATION_0,
        Surface.ROTATION_180 -> Size(
            min(screenSize.width, screenSize.height),
            max(screenSize.width, screenSize.height)
        )
        Surface.ROTATION_90,
        Surface.ROTATION_270 -> Size(
            max(screenSize.width, screenSize.height),
            min(screenSize.width, screenSize.height)
        )
        else -> Size(
            min(screenSize.width, screenSize.height),
            max(screenSize.width, screenSize.height)
        )
    }
}

val Context.statusBarHeight
    @SuppressLint("DiscouragedApi", "InternalInsetResource") get(): Int {
        var result = 0
        val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

val Context.isFullScreenDevice get(): Boolean = screenRatio >= 1.97f

/**
 * Need to investigate Window.ID_ANDROID_CONTENT
 */
//    fun getTitleHeight(activity: Activity) = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT).top

// val Context.isNavigationGestureEnabled
//    @SuppressLint("ObsoleteSdkInt")
//    get(): Boolean {
//        val value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
//            Settings.System.getInt(contentResolver, getNavigationBarName(), 0)
//        } else {
//            Settings.Global.getInt(contentResolver, getNavigationBarName(), 0)
//        }
//        return value != 0
//    }

private fun Context.getNavigationBarName(): String {
    val brand = Build.BRAND
    if (TextUtils.isEmpty(brand)) return "navigationbar_is_min"
    return when {
        isHuaWei -> "navigationbar_is_min"
        isXiaoMi -> "force_fsg_nav_bar"
        isVivo -> "navigation_gesture_on"
        isOppo -> "navigation_gesture_on"
        else -> "navigationbar_is_min"
    }
}

/**
 * So far, the only valid way to check whether the navigation bar is shown is using the difference between screen real height
 * and available height.
 * This is the only way that I've known works.
 */
val Context.isNavigationBarShown
    get(): Boolean {
        return screenRealHeight - screenAvailableHeight > 0
        // //        val view = activity.findViewById<View>(android.R.id.navigationBarBackground) ?: return false
        // //        val visible = view.visibility
        // //        return !(visible == View.GONE || visible == View.INVISIBLE)
        //
        //        // In full screen(AKA all screen) device, this method will return `true`.
        //        val resourceId = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        //        return if (resourceId > 0) {
        //            resources.getBoolean(resourceId)
        //        } else false
    }

// @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
// fun doesDeviceHasNavigationBar(): Boolean {
//    return runCatching {
//        // IWindowManager windowManagerService = WindowManagerGlobal.getWindowManagerService();
//        val windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal")
//        val getWmServiceMethod: Method = windowManagerGlobalClass.getDeclaredMethod("getWindowManagerService")
//        getWmServiceMethod.isAccessible = true
//        // getWindowManagerService is a static method, so invoke with null
//        val iWindowManager: Any = getWmServiceMethod.invoke(null)!!
//
//        val iWindowManagerClass: Class<*> = iWindowManager.javaClass
//        val hasNavBarMethod: Method = iWindowManagerClass.getDeclaredMethod("hasNavigationBar")
//        hasNavBarMethod.isAccessible = true
//        hasNavBarMethod.invoke(iWindowManager) as Boolean
//    }.getOrDefault(false)
// }

/**
 * In some devices(Like Google Pixel), although I've selected [Gesture navigation],
 * the real height of navigation bar is still the same as the height of [2/3-button navigation].
 * In order to get the exactly height of navigation bar, I can not use the value which get from `navigation_bar_height`.
 *
 * ```
 * ⎸             ⎸
 * ⎸             ⎸ ⎽⎽
 * ⎸     ⎻       ⎸ ⎽⎽   The height of navigation bar in [Gesture navigation] mode in some devices.
 *
 *
 * ⎸             ⎸ ⎺↓⎺
 * ⎸             ⎸        The height of navigation bar in [Gesture navigation] mode in some devices.
 * ⎸     ⎻       ⎸ ⎽↑⎽
 *
 *
 * ⎸             ⎸ ⎺↓⎺
 * ⎸ ◀︎  ●   ◼   ⎸        The height of navigation bar in [2/3-button navigation] mode in some devices.
 * ⎸             ⎸ ⎽↑⎽
 * ```
 */
val Context.navigationBarHeight
    get(): Int {
        //        var result = 0
        //        val resourceId = this.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        //        if (resourceId > 0) {
        //            result = this.resources.getDimensionPixelSize(resourceId)
        //        }
        //        return result

        return screenRealHeight - screenAvailableHeight
    }

fun calculateNotchRect(act: Activity, notchWidth: Int, notchHeight: Int): Rect {
    val screenSize = act.screenRealResolution
    val screenWidth = screenSize.width
    val screenHeight = screenSize.height
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    if (act.isPortrait) {
        left = (screenWidth - notchWidth) / 2
        top = 0
        right = left + notchWidth
        bottom = notchHeight
    } else {
        left = 0
        top = (screenHeight - notchWidth) / 2
        right = notchHeight
        bottom = top + notchWidth
    }
    return Rect(left, top, right, bottom)
}

val Context.screenRatio
    get(): Float {
        val p = screenRealResolution
        return 1.0f * max(p.width, p.height) / min(p.width, p.height)
    }

fun getUuid(): String = UUID.randomUUID().toString()

// https://beltran.work/blog/2018-03-27-device-unique-id-android/
// The Problem
// This ID is not only the same on all apps, but also it is the same for all users of the device.
// So a guest account, for example, will also obtain the same ID, as opposed to the ANDROID_ID.
// As well, no permissions are required to access this ID.
// There’s not much you can do as the user to avoid this.
// Only a factory reset will restart this provisioning profile.
// While Google introduced ways to improve privacy around the ANDROID_ID on Android 8.0,
// making it unique per app, the design of DRM systems does not allow much to do against it.
// Maybe in the future apps should require permissions to access DRM services.
fun getUniqueIdByMediaDrm(): ByteArray? {
    // val COMMON_PSSH_UUID = UUID(0x1077EFECC0B24D02L, -0x531cc3e1ad1d04b5L)
    // val CLEARKEY_UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)
    // val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
    // val PLAYREADY_UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)
    val wideVineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
    return runCatching {
        val wvDrm = MediaDrm(wideVineUuid)
        val wideVineId = wvDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
        wideVineId
        // android.util.Base64.encodeToString(wideVineId, android.util.Base64.NO_WRAP)
        // wideVineId.joinToString("") { "%02X".format(it) }
    }.getOrNull()
}

@SuppressLint("HardwareIds")
@RequiresApi(Build.VERSION_CODES.O)
fun Context.getAndroidId(): String = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

@SuppressLint("HardwareIds")
fun Context.getUniqueID(): String {
    return if (!API.ABOVE_O) {
        val uid: ByteArray? = getUniqueIdByMediaDrm()
        if (uid != null) {
            android.util.Base64.encodeToString(uid, android.util.Base64.NO_WRAP)
        } else {
            getUuid()
        }
    } else {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }
}

fun getImei(ctx: Context): String? {
    val imei0 = getImei(ctx, 0)
    val imei1 = getImei(ctx, 1)
    return if (imei0.isNullOrBlank()) imei1 else imei0
}

fun getImei(ctx: Context, slotId: Int): String? {
    return runCatching {
        val manager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val method = manager.javaClass.getMethod("getImei", Int::class.javaPrimitiveType)
        method.invoke(manager, slotId) as String
    }.getOrNull()
}

@SuppressLint("DiscouragedApi")
fun Context.getDimenInPixel(name: String): Int {
    val resourceId = resources.getIdentifier(name, "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else -1
}

fun isProbablyAnEmulator(): Boolean {
    return (
        Build.FINGERPRINT.startsWith("google/sdk_gphone_") &&
            Build.FINGERPRINT.endsWith(":user/release-keys") &&
            Build.MANUFACTURER == "Google" &&
            Build.PRODUCT.startsWith("sdk_gphone_") &&
            Build.BRAND == "google" &&
            Build.MODEL.startsWith("sdk_gphone_")
        ) ||

        // Android SDK emulator
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("google_sdk") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for x86") ||
        "QC_Reference_Phone" == Build.BOARD ||

        // bluestacks
        Build.MANUFACTURER.contains("Genymotion") ||
        Build.HOST.startsWith("Build") || // MSI App Player
        (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
        Build.PRODUCT == "google_sdk" ||
        // another Android SDK emulator check
        DeviceProp.getSystemProperty("ro.kernel.qemu") == "1"
}

fun Context.isTablet(): Boolean {
    return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
}

// ================================

fun Context.isDeviceInPortrait(
    @IntRange(from = 0, to = 359) degree: Int,
    prevOrientation: Int = -1
): Boolean {
    return isNormalPortrait(degree, prevOrientation) || isReversePortrait(degree, prevOrientation)
}

fun Context.isDeviceInLandscape(
    @IntRange(from = 0, to = 359) degree: Int,
    prevOrientation: Int = -1
): Boolean {
    return isNormalLandscape(degree, prevOrientation) || isReverseLandscape(degree, prevOrientation)
}

// ---------------

/**
 * **Attention:**
 * Only if the device is in portrait mode regardless of **Normal Portrait** or **Reverse Portrait**,
 * `true` will be returned.
 *
 * @param surfaceRotation The value may be
 * - Surface.ROTATION_0 (no rotation),
 * - Surface.ROTATION_90,
 * - Surface.ROTATION_180,
 * - Surface.ROTATION_270.
 */
fun isPortrait(surfaceRotation: Int): Boolean =
    Surface.ROTATION_0 == surfaceRotation || Surface.ROTATION_180 == surfaceRotation

/**
 * **Attention:**
 * Only if the device is in landscape mode regardless of **Normal Landscape** or **Reverse Landscape**,
 * `true` will be returned.
 *
 * @param surfaceRotation The value may be
 * - Surface.ROTATION_0 (no rotation),
 * - Surface.ROTATION_90,
 * - Surface.ROTATION_180,
 * - Surface.ROTATION_270.
 */
fun isLandscape(surfaceRotation: Int): Boolean =
    Surface.ROTATION_90 == surfaceRotation || Surface.ROTATION_270 == surfaceRotation

// ---------------

/**
 * Only if the device is just in **Normal Portrait** mode, `true` will be returned.
 *
 * @param prevOrientation The previous orientation value:
 * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
 * - Any other value will be ignored.
 */
fun Context.isNormalPortrait(
    @IntRange(from = 0, to = 359) degree: Int,
    prevOrientation: Int = -1
): Boolean {
    // If device is already in normal portrait mode, the wide range is:
    // [300, 359], [0, 60]

    // The narrow range is used to check the device real orientation.
    // [330, 359], [0, 30]

    return if (Surface.ROTATION_0 == screenSurfaceRotation || SCREEN_ORIENTATION_PORTRAIT == prevOrientation) {
        (degree in 301..359) || (degree in 0 until 60) // wide range
    } else
        (degree in 330..359) || (degree in 0..30) // narrow range

    //    val ssr = screenSurfaceRotation
    //    return if (Surface.ROTATION_0 == ssr || SCREEN_ORIENTATION_PORTRAIT == prevOrientation) {
    //        if (Surface.ROTATION_270 == ssr || Surface.ROTATION_90 == ssr)
    //            Surface.ROTATION_270 == ssr && degree == 60
    //        else if (300 == ssr || Surface.ROTATION_0 == ssr) true
    //        else
    //            (degree in 301..359) || (degree in 0..60) // wide range
    //    } else
    //        (degree in 330..359) || (degree in 0..30) // narrow range
}

/**
 * Only if the device is just in **Normal Landscape** mode, `true` will be returned.
 *
 * @param prevOrientation The previous orientation value:
 * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
 * - Any other value will be ignored.
 */
fun Context.isNormalLandscape(
    @IntRange(from = 0, to = 359) degree: Int,
    prevOrientation: Int = -1
): Boolean {
    // If device is already in normal landscape mode, the wide range is:
    // [210, 270], [270, 330]

    // The narrow range is used to check the device real orientation.
    // [240, 270], [270, 300]

    return if (Surface.ROTATION_90 == screenSurfaceRotation || SCREEN_ORIENTATION_LANDSCAPE == prevOrientation) {
        degree in 211 until 330 // wide range
    } else
        degree in 240..300 // narrow range
}

/**
 * Only if the device is just in **Reverse Landscape** mode, `true` will be returned.
 *
 * @param prevOrientation The previous orientation value:
 * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
 * - Any other value will be ignored.
 */
fun Context.isReverseLandscape(
    @IntRange(from = 0, to = 359) degree: Int,
    prevOrientation: Int = -1
): Boolean {
    // If device is already in reverse landscape mode, the wide range is:
    // [30, 90], [90, 150]

    // The narrow range is used to check the device real orientation.
    // [60, 90], [90, 120]

    return if (Surface.ROTATION_270 == screenSurfaceRotation || SCREEN_ORIENTATION_REVERSE_LANDSCAPE == prevOrientation) {
        degree in 31 until 150 // wide range
    } else
        degree in 60..120 // narrow range
}

/**
 * Only if the device is just in **Reverse Portrait** mode, `true` will be returned.
 *
 * @param prevOrientation The previous orientation value:
 * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
 * - Any other value will be ignored.
 */
fun Context.isReversePortrait(
    @IntRange(from = 0, to = 359) degree: Int,
    prevOrientation: Int = -1
): Boolean {
    // If device is already in reverse portrait mode, the wide range is:
    // [120, 180], [180, 240]

    // The narrow range is used to check the device real orientation.
    // [150, 180], [180, 210]

    return if (Surface.ROTATION_180 == screenSurfaceRotation || SCREEN_ORIENTATION_REVERSE_PORTRAIT == prevOrientation) {
        degree in 121 until 240 // wide range
    } else
        degree in 150..210 // narrow range

    //    val ssr = screenSurfaceRotation
    //    return if (Surface.ROTATION_180 == ssr || SCREEN_ORIENTATION_REVERSE_PORTRAIT == prevOrientation) {
    //        if (Surface.ROTATION_180 == ssr && degree == 240) true
    //        else if (Surface.ROTATION_90 == ssr) false
    //        else degree in 121 until 240 // wide range
    //    } else
    //        degree in 150..210 // narrow range
}

// ---------------

/**
 * @return The result is one of the following value:
 * - ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
 * - ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * - ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
 * - -1 means unknown or the orientation is not changed.
 */
fun Context.getDeviceOrientation(
    @IntRange(from = 0, to = 359) degree: Int,
    prevOrientation: Int = -1
): Int {
    return when {
        isNormalPortrait(degree, prevOrientation) -> SCREEN_ORIENTATION_PORTRAIT
        isReversePortrait(degree, prevOrientation) -> SCREEN_ORIENTATION_REVERSE_PORTRAIT
        isNormalLandscape(degree, prevOrientation) -> SCREEN_ORIENTATION_LANDSCAPE
        isReverseLandscape(degree, prevOrientation) -> SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> -1
    }
}

/**
 * The context can only be either Activity(Fragment) or Service.
 *
 * @return Return the screen rotation(**NOT** device rotation).
 *         The result is one of the following value:
 *
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 */
val Context.screenSurfaceRotation: Int
    @Suppress("DEPRECATION")
    get() {
        if (this !is Activity && this !is Service) error("Context can be either Activity(Fragment) or Service.")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (this is Service) {
                // On Android 11+, we can't get `display` directly from Service, it will cause
                // the following exception:
                // Tried to obtain display from a Context not associated with one.
                // Only visual Contexts (such as Activity or one created with Context#createWindowContext)
                // or ones created with Context#createDisplayContext are associated with displays.
                // Other types of Contexts are typically related to background entities
                // and may return an arbitrary display.
                //
                // So we need to get screen rotation from `DisplayManager`.
                displayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation
            } else {
                display!!.rotation
            }
        } else windowManager.defaultDisplay.rotation
    }
// =================

/**
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 */
val SURFACE_ROTATION_TO_DEGREE = mapOf(
    Surface.ROTATION_0 to 0,
    Surface.ROTATION_90 to 90,
    Surface.ROTATION_180 to 180,
    Surface.ROTATION_270 to 270
)

/**
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 */
val DEGREE_TO_SURFACE_ROTATION = mapOf(
    0 to Surface.ROTATION_0,
    90 to Surface.ROTATION_90,
    180 to Surface.ROTATION_180,
    270 to Surface.ROTATION_270
)

/**
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 */
val SCREEN_ORIENTATION_TO_SURFACE_ORIENTATIONS = mapOf(
    SCREEN_ORIENTATION_PORTRAIT to Surface.ROTATION_0,
    SCREEN_ORIENTATION_LANDSCAPE to Surface.ROTATION_90,
    SCREEN_ORIENTATION_REVERSE_PORTRAIT to Surface.ROTATION_180,
    SCREEN_ORIENTATION_REVERSE_LANDSCAPE to Surface.ROTATION_270
)

val Int.screenOrientationName: String
    get() = when (this) {
        SCREEN_ORIENTATION_PORTRAIT -> "Portrait"
        SCREEN_ORIENTATION_LANDSCAPE -> "Landscape"
        SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "Reverse Portrait"
        SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "Reverse Landscape"
        else -> "Unknown"
    }

/**
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 */
val Int.surfaceRotationLiteralName: String
    get() = when (this) {
        Surface.ROTATION_0 -> "ROTATION_0"
        Surface.ROTATION_90 -> "ROTATION_90"
        Surface.ROTATION_180 -> "ROTATION_180"
        Surface.ROTATION_270 -> "ROTATION_270"
        else -> "Unknown"
    }

/**
 * - Surface.ROTATION_0 (no rotation)
 * - Surface.ROTATION_90 (90 degrees counter-clockwise)
 * - Surface.ROTATION_180
 * - Surface.ROTATION_270 (90 degrees clockwise)
 */
val Int.surfaceRotationName: String
    get() = when (this) {
        Surface.ROTATION_0 -> "Portrait"
        Surface.ROTATION_90 -> "Landscape"
        Surface.ROTATION_180 -> "Reverse Portrait"
        Surface.ROTATION_270 -> "Reverse Landscape"
        else -> "Unknown"
    }
