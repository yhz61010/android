package com.leovp.androidbase.exts.android

import android.accounts.AccountManager
import android.app.*
import android.app.admin.DevicePolicyManager
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.ClipboardManager
import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.hardware.ConsumerIrManager
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.midi.MidiManager
import android.media.projection.MediaProjectionManager
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.IpSecManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.nfc.NfcManager
import android.os.*
import android.os.health.SystemHealthManager
import android.os.storage.StorageManager
import android.preference.PreferenceManager
import android.print.PrintManager
import android.telecom.TelecomManager
import android.telephony.CarrierConfigManager
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.CaptioningManager
import android.view.inputmethod.InputMethodManager
import android.view.textclassifier.TextClassificationManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

/**
 * Application Context
 *
 * You **MUST** initialize `app` in your custom Application
 */
lateinit var app: Application

/**
 * Get the package name
 */
val Context.id get() = this.packageName!!

/**
 * Get package uri
 */
val Context.packageUri get() = Uri.fromParts("package", this.packageName!!, null)!!

/**
 * Return the version name of empty string if can't get version string.
 */
val Context.versionName get() = this.packageManager.getPackageInfo(this.packageName, PackageManager.GET_CONFIGURATIONS).versionName ?: ""

/**
 * Return the version code or 0 if can't get version code.
 */
val Context.versionCode
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        this.packageManager.getPackageInfo(
            this.packageName,
            PackageManager.GET_CONFIGURATIONS
        ).longVersionCode
    } else {
        this.packageManager.getPackageInfo(
            this.packageName,
            PackageManager.GET_CONFIGURATIONS
        ).versionCode.toLong()
    }
val Context.isPortrait get() = this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
val Context.isLandscape get() = this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
val Context.sharedPrefs: SharedPreferences get() = PreferenceManager.getDefaultSharedPreferences(this)
fun Context.sharedPrefs(name: String): SharedPreferences = this.getSharedPreferences(name, AppCompatActivity.MODE_PRIVATE)!!

val Context.densityDpi get(): Int = this.resources.displayMetrics.densityDpi
val Context.density get(): Float = this.resources.displayMetrics.density

val Context.accessibilityManager get() = this.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
val Context.accountManager get() = this.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
val Context.activityManager get() = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
val Context.alarmManager get() = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
val Context.audioManager get() = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val Context.clipboardManager get() = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
val Context.connectivityManager get() = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
val Context.devicePolicyManager get() = this.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
val Context.downloadManager get() = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
val Context.dropBoxManager get() = this.getSystemService(Context.DROPBOX_SERVICE) as DropBoxManager
val Context.inputManager get() = this.getSystemService(Context.INPUT_SERVICE) as InputManager
val Context.inputMethodManager get() = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
val Context.keyguardManager get() = this.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
val Context.locationManager get() = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
val Context.nfcManager get() = this.getSystemService(Context.NFC_SERVICE) as NfcManager
val Context.notificationManager get() = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
val Context.powerManager get() = this.getSystemService(Context.POWER_SERVICE) as PowerManager
val Context.searchManager get() = this.getSystemService(Context.SEARCH_SERVICE) as SearchManager
val Context.sensorManager get() = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager
val Context.storageManager get() = this.getSystemService(Context.STORAGE_SERVICE) as StorageManager
val Context.telephonyManager get() = this.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
val Context.uiModeManager get() = this.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
val Context.usbManager get() = this.getSystemService(Context.USB_SERVICE) as UsbManager
val Context.wallpaperManager get() = this.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
val Context.wifiManager get() = this.getSystemService(Context.WIFI_SERVICE) as WifiManager
val Context.wifiP2pManager get() = this.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
val Context.windowManager get() = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
val Context.userManager @RequiresApi(API.J_MR1) get() = this.getSystemService(Context.USER_SERVICE) as UserManager
val Context.displayManager @RequiresApi(API.J_MR1) get() = this.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
val Context.bluetoothManager @RequiresApi(API.J_MR2) get() = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
val Context.captioningManager @RequiresApi(API.K) get() = this.getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager
val Context.appOpsManager @RequiresApi(API.K) get() = this.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
val Context.printManager @RequiresApi(API.K) get() = this.getSystemService(Context.PRINT_SERVICE) as PrintManager
val Context.consumerManager @RequiresApi(API.K) get() = this.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
val Context.telecomManager @RequiresApi(API.L) get() = this.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
val Context.cameraManager @RequiresApi(API.L) get() = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
val Context.batteryManager @RequiresApi(API.L) get() = this.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
val Context.appWidgetManager @RequiresApi(API.L) get() = this.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
val Context.restrictionsManager @RequiresApi(API.L) get() = this.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
val Context.mediaSessionManager @RequiresApi(API.L) get() = this.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
val Context.mediaProjectionManager @RequiresApi(API.L) get() = this.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
val Context.usageStatsManager @RequiresApi(API.L_MR1) get() = this.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
val Context.midiManager @RequiresApi(API.M) get() = this.getSystemService(Context.MIDI_SERVICE) as MidiManager
val Context.networkStatusManager @RequiresApi(API.M) get() = this.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
val Context.carrierConfigManager @RequiresApi(API.M) get() = this.getSystemService(Context.CARRIER_CONFIG_SERVICE) as CarrierConfigManager
val Context.systemHealthManager @RequiresApi(API.N) get() = this.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as SystemHealthManager
val Context.hardwarePropertiesManager @RequiresApi(API.N) get() = this.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as HardwarePropertiesManager
val Context.shortcutManager @RequiresApi(API.N_MR1) get() = this.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
val Context.storageStatsManager @RequiresApi(API.O) get() = this.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
val Context.companionDeviceManager @RequiresApi(API.O) get() = this.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
val Context.textClassificationManager @RequiresApi(API.O) get() = this.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE) as TextClassificationManager
val Context.euiccManager @RequiresApi(API.P) get() = this.getSystemService(Context.EUICC_SERVICE) as EuiccManager
val Context.ipSecManager @RequiresApi(API.P) get() = this.getSystemService(Context.IPSEC_SERVICE) as IpSecManager