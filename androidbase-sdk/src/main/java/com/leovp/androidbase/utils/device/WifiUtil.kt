package com.leovp.androidbase.utils.device

/**
 * Need following permission:
 * On Android 8.0 and Android 8.1:
 * <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
 * Each background app can scan one time in a 30-minute period.
 *
 * On Android 9 besides Android 8.x permission, you also need to add following permission:
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 * or
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 * Each foreground app can scan four times in a 2-minute period. This allows for a burst of scans in a short time.
 * All background apps combined can scan one time in a 30-minute period.
 *
 * On Android 10 besides Android 8.x permission, you also need to add following permission:
 * <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 * The same throttling limits from Android 9 apply.
 * There is a new developer option to toggle the throttling off for local testing (under Developer Options > Networking > Wi-Fi scan throttling).
 *
 * Author: Michael Leo
 * Date: 21-3-6 下午4:41
 */
object WifiUtil {
}