package com.leovp.dex_sdk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.Nullable;

import com.leovp.dex_sdk.util.CmnUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * // Some devices internally create a Handler when creating an input Surface, causing an exception:
 * //   "Can't create handler inside thread that has not called Looper.prepare()"
 * // <https://github.com/Genymobile/scrcpy/issues/240>
 * //
 * // Use Looper.prepareMainLooper() instead of Looper.prepare() to avoid a NullPointerException:
 * //   "Attempt to read from field 'android.os.MessageQueue android.os.Looper.mQueue'
 * //    on a null object reference"
 * // <https://github.com/Genymobile/scrcpy/issues/921>
 * <p>
 * <p>
 * Author: Michael Leo
 * Date: 2022/1/6 13:31
 */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
public class DexHelper {
    private static final String TAG = "DexHelper";

    @SuppressLint("StaticFieldLeak")
    private static final DexHelper ourInstance = new DexHelper();

    public static DexHelper getInstance() {
        return ourInstance;
    }

    private Context context;
    private Method getServiceMethod;

    private ConnectivityManager connectivityManager;

    private Object wifiManagerService;

    private DexHelper() {
        try {
            context = getContext();
            getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
        } catch (Exception e) {
            CmnUtil.println(TAG, "DexHelper() constructor exception.", e);
        }
    }

    @Nullable
    synchronized public Context getContext() {
        if (context != null) return context;
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext");
            Method getInstanceMethod = activityThreadClass.getDeclaredMethod("systemMain");

            Constructor<?> activityThreadConstructor = activityThreadClass.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            Object activityThread;
            Object context;
            activityThread = getInstanceMethod.invoke(activityThreadClass);
            context = getSystemContextMethod.invoke(activityThread);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                try {
                    // As of Android 11, we have to set mOpPackageName or else we can't access network state.
                    Class<?> implClass = Class.forName("android.app.ContextImpl");
                    Field field = implClass.getDeclaredField("mOpPackageName");
                    field.setAccessible(true);
                    field.set(context, "shell");
                } catch (Exception e) {
                    CmnUtil.println(TAG, "getContext()-1 exception.", e);
                }
            }

            this.context = (Context) getSystemContextMethod.invoke(activityThread);
            return this.context;
        } catch (Exception e) {
            CmnUtil.println(TAG, "getContext()-2 exception.", e);
            return null;
        }
    }

    public IInterface getService(String service, String type) {
        try {
            IBinder binder = (IBinder) getServiceMethod.invoke(null, service);
            Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
            return (IInterface) asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public Object getServiceObject(String service) {
        try {
            return getServiceMethod.invoke(null, service);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Nullable
    synchronized public Object getWifiManagerService() {
        if (wifiManagerService == null) {
            wifiManagerService = getService("wifi", "android.net.wifi.IWifiManager");
        }
        return wifiManagerService;
    }

    @Nullable
    synchronized public ConnectivityManager getConnectivityManager() {
        try {
            if (connectivityManager != null) return connectivityManager;
            Object connectivityManagerService = getService("connectivity", "android.net.IConnectivityManager");

            Class<?> c = Class.forName("android.net.IConnectivityManager");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Constructor<?> constructor = ConnectivityManager.class.getDeclaredConstructor(Context.class, c);
                constructor.setAccessible(true);
                connectivityManager = (ConnectivityManager) constructor.newInstance(context, connectivityManagerService);
            } else {
                Constructor<?> constructor = ConnectivityManager.class.getDeclaredConstructor(c);
                constructor.setAccessible(true);
                connectivityManager = (ConnectivityManager) constructor.newInstance(connectivityManagerService);
            }
        } catch (Exception e) {
            CmnUtil.println(TAG, "getConnectivityManager() exception.", e);
        }
        return connectivityManager;
    }

    @Nullable
    public WifiInfo getWifiInfo() {
        try {
            Class<?> wifiClass = Class.forName("android.net.wifi.IWifiManager");
            Method method = wifiClass.getMethod("getConnectionInfo");
            return (WifiInfo) method.invoke(getWifiManagerService());
        } catch (Exception e) {
            try {
                Class<?> wifiClass = Class.forName("android.net.wifi.IWifiManager");
                Method method = wifiClass.getMethod("getConnectionInfo", String.class);
                return (WifiInfo) method.invoke(getWifiManagerService(), "shell");
            } catch (Exception ee) {
                try {
                    Class<?> wifiClass = Class.forName("android.net.wifi.IWifiManager");
                    Method method = wifiClass.getMethod("getConnectionInfo", String.class, String.class);
                    // As of Android, we have to set package name to "shell" just same as UID.
                    return (WifiInfo) method.invoke(getWifiManagerService(), "shell", "shell");
                } catch (Exception eee) {
                    CmnUtil.println(TAG, "getWifiInfo() exception.", eee);
                }
            }
        }
        return null;
    }

    public boolean isWifiActive() {
        try {
            ConnectivityManager cm = getConnectivityManager();
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network nw = cm.getActiveNetwork();
                NetworkCapabilities nc = cm.getNetworkCapabilities(nw);
                return nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            } else {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                return ni.getType() == ConnectivityManager.TYPE_WIFI;
            }
        } catch (Exception e) {
            CmnUtil.println(TAG, "isWifiActive() exception.", e);
            return false;
        }
    }
}