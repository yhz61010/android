package com.leovp.demo_dex;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;

import com.leovp.demo_dex.utils.DexHelper;
import com.leovp.demo_dex.utils.ScreenshotUtil;
import com.leovp.demo_dex.utils.Util;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
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

            println("Prepare to take screenshot...");
            long st = SystemClock.elapsedRealtime();
            Bitmap screenshot = ScreenshotUtil.screenshot(1440, 2960, Surface.ROTATION_0);
            println("Screenshot done. Cost=" + (SystemClock.elapsedRealtime() - st));

            if (screenshot != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                screenshot.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] bitmapBytes = baos.toByteArray();

                FileOutputStream fos = new FileOutputStream("/sdcard/screenshot.jpg");
                fos.write(bitmapBytes);
                fos.flush();
                fos.close();
            }

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
