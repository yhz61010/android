package com.leovp.dex_sdk.network;

import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Looper;

import com.leovp.dex_sdk.DexHelper;
import com.leovp.dex_sdk.util.CmnUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;

public class NetworkMonitor {
    private Method getServiceMethod;
    private Object wifiManagerService;
    private Object connectivityManagerService;
    private ConnectivityManager connectivityManager;
    private Context context;
    private HashMap<String, String> networks;

    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
    public NetworkMonitor() {
        Looper.prepare();
        try {
            networks = new HashMap<>();
            getServiceMethod = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
            context = DexHelper.getInstance().getContext();

            wifiManagerService = DexHelper.getInstance().getService("wifi", "android.net.wifi.IWifiManager");
            connectivityManagerService = DexHelper.getInstance().getService("connectivity", "android.net.IConnectivityManager");

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
            CmnUtil.println("Init NetworkMonitor() error.", e);
        }
    }

    public void registerNetworkCallback() {
        try {
            NetworkRequest request = new NetworkRequest.Builder().build();
            final CustomConnectivityManager customConnectivityManager = new CustomConnectivityManager((IConnectivityManager) connectivityManagerService);
            customConnectivityManager.registerNetworkCallback(request, new CustomConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    CmnUtil.println("=====> onAvailable <=====");
                    networkAvail(network);
                }

                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    CmnUtil.println("=====> onLost <=====");
                    networkLost(network);
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
        Looper.loop();
    }

    private void networkLost(Network network) {
        String networkType = "unknown";
        if (networks.containsKey(network.toString())) {
            networkType = networks.get(network.toString());
            networks.remove(network.toString());
        }
        CmnUtil.println("networkType=" + networkType);
    }

    private void networkAvail(Network network) {
        CmnUtil.println(">>> networkAvail <<<");
//        WifiInfo wifiInfo = DexHelper.getInstance().getWifiInfo();
//        String networkType = getType(network);
    }

    private String getType(Network network) {
        try {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);

            Method getTransportTypes = NetworkCapabilities.class.getDeclaredMethod("getTransportTypes");
            getTransportTypes.setAccessible(true);
            Object object = getTransportTypes.invoke(networkCapabilities);
            assert object != null;
            int types = ((int[]) object)[0];
            String transport = "";
            switch (types) {
                case TRANSPORT_CELLULAR:
                    transport = "CELLULAR";
                    break;
                case TRANSPORT_WIFI:
                    transport = "WIFI";
                    break;
                case TRANSPORT_BLUETOOTH:
                    transport = "BLUETOOTH";
                    break;
                case TRANSPORT_ETHERNET:
                    transport = "ETHERNET";
                    break;
                case TRANSPORT_VPN:
                    transport = "VPN";
                    break;
            }
            if (!networks.containsKey(network.toString()))
                networks.put(network.toString(), transport);
            return transport;

        } catch (Exception e) {
            CmnUtil.println("getType() error.", e);
        }
        return null;
    }

    private String getSSID(String srcSSID, Network network) {
        try {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            Method method = NetworkCapabilities.class.getMethod("getSSID");
            method.setAccessible(true);
            String ssid = (String) method.invoke(networkCapabilities);
            if (ssid == null)
                return srcSSID;

            return ssid.substring(1, ssid.length() - 1);
        } catch (Exception e) {
            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            String wifiName = wifiInfo.getExtraInfo();
            if (wifiName != null && wifiName.length() > 1)
                srcSSID = wifiName.substring(1, wifiName.length() - 1);
        }

        return srcSSID;
    }
}