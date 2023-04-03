package com.leovp.dex;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import com.leovp.dex.util.CmnUtil;
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
            getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
        } catch (Exception e) {
            CmnUtil.println(TAG, "DexHelper() constructor exception.", e);
        }
    }

    @Nullable
    synchronized public Context getContext(final String packageName) {
        try {
//            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
//            Method getInstanceMethod = activityThreadClass.getDeclaredMethod("systemMain");
//            Constructor<?> activityThreadConstructor = activityThreadClass.getDeclaredConstructor();
//            activityThreadConstructor.setAccessible(true);
//            Object activityThread;
//            activityThread = getInstanceMethod.invoke(activityThreadClass);
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Constructor<?> activityThreadConstructor = activityThreadClass.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            Object activityThread = activityThreadConstructor.newInstance();

            // ActivityThread.AppBindData appBindData = new ActivityThread.AppBindData();
            Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> appBindDataConstructor = appBindDataClass.getDeclaredConstructor();
            appBindDataConstructor.setAccessible(true);
            Object appBindData = appBindDataConstructor.newInstance();

            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.packageName = packageName;

            // appBindData.appInfo = applicationInfo;
            Field appInfoField = appBindDataClass.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            appInfoField.set(appBindData, applicationInfo);

            // activityThread.mBoundApplication = appBindData;
            Field mBoundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            mBoundApplicationField.set(activityThread, appBindData);

            Application app = Application.class.newInstance();
            Field baseField = ContextWrapper.class.getDeclaredField("mBase");
            baseField.setAccessible(true);
            baseField.set(app, FakeContext.get());

            // activityThread.mInitialApplication = app;
            Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(activityThread, app);

//            Method getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext");
//            CmnUtil.println("getSystemContextMethod=" + getSystemContextMethod);
//
//            Object context = getSystemContextMethod.invoke(activityThread);
//            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
//                try {
//                    CmnUtil.println("---> context=" + context);
//                    // As of Android 11, we have to set mOpPackageName or else we can't access network state.
//                    Class<?> implClass = Class.forName("android.app.ContextImpl");
//                    Field field = implClass.getDeclaredField("mOpPackageName");
//                    field.setAccessible(true);
//                    field.set(context, packageName);
//                } catch (Exception e) {
//                    CmnUtil.println(TAG, "getContext()-1 exception.", e);
//                }
//            }

            this.context = app;
            return this.context;
        } catch (Exception e) {
//            CmnUtil.println(TAG, "getContext()-2 exception.", e);
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

    @SuppressLint("ObsoleteSdkInt")
    @Nullable
    synchronized public ConnectivityManager getConnectivityManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10
            return null;
        }
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
            try { // Android 10 or below
                Class<?> wifiClass = Class.forName("android.net.wifi.IWifiManager");
                Method method = wifiClass.getMethod("getConnectionInfo", String.class);
                return (WifiInfo) method.invoke(getWifiManagerService(), FakeContext.PACKAGE_NAME);
            } catch (Exception ee) {
                try { // Android 11
                    Class<?> wifiClass = Class.forName("android.net.wifi.IWifiManager");
                    // String callingPackage, String callingFeatureId
                    Method method = wifiClass.getMethod("getConnectionInfo", String.class, String.class);
                    // As of Android 11, we have to set package name to "shell" just same as UID.
                    // String callingPackage, String callingFeatureId
                    return (WifiInfo) method.invoke(getWifiManagerService(), FakeContext.PACKAGE_NAME, "shell");
                } catch (Exception eee) {
                    CmnUtil.println(TAG, "getWifiInfo() exception.", eee);
                }
            }
        }
        return null;
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
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
            e.printStackTrace();
            CmnUtil.println(TAG, "isWifiActive() exception.", e);
            return false;
        }
    }
}
