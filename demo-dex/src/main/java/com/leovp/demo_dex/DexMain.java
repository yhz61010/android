package com.leovp.demo_dex;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.os.Looper;

import com.leovp.demo_dex.utils.DexHelper;
import com.leovp.demo_dex.utils.Util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Author: Michael Leo
 * Date: 2021/12/16 10:25
 */
public final class DexMain {
    //    private static final String TAG = "leo-dex";
    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private DexMain() {

    }

    public static void main(String[] args) {
        try {
            println("=====> Enter: " + getCurrentDateTime() + " <=====");
            Looper.prepare();
            final DexHelper dexHelper = DexHelper.getInstance();

            Context context = dexHelper.getContext();
            assert context != null;
            println(context.toString());

            List<String> ipList = Util.getInstance().getIp();
            String ip = ipList.isEmpty() ? "" : ipList.get(0);
            println("ip=" + ip);

            boolean isWifiActive = dexHelper.isWifiActive();
            WifiInfo wifiInfo = dexHelper.getWifiInfo();
            String ssid = wifiInfo == null ? "NA" : wifiInfo.getSSID().replaceAll("\"", "");
            println("isWifiActive=" + isWifiActive + " ssid=" + ssid);

            println("Exit");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void println(String msg) {
        System.out.println(msg);
    }

    public static String getCurrentDateTime() {
        return SDF.format(new Date());
    }
}
