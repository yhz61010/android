package com.leovp.demo_dex;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;

import com.leovp.dex_sdk.DexHelper;
import com.leovp.dex_sdk.ScreenshotUtil;
import com.leovp.dex_sdk.network.IpUtil;
import com.leovp.dex_sdk.util.CmnUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Usage:
 * ```shell
 * $ adb push dexdemo.dex /data/local/tmp
 * $ adb exec-out CLASSPATH=/data/local/tmp/dexdemo.dex app_process / com.leovp.demo_dex.DexMain
 * ```
 * <p>
 * Author: Michael Leo
 * Date: 2021/12/16 10:25
 */
public final class DexMain {
    private DexMain() {
    }

    public static void main(String[] args) {
        try {
            CmnUtil.println("=====> Enter: " + CmnUtil.getCurrentDateTime() + " <=====");
            Looper.prepare();
            final DexHelper dexHelper = DexHelper.getInstance();

            Context context = dexHelper.getContext();
            assert context != null;
            CmnUtil.println(context.toString());

            List<String> ipList = IpUtil.getIp();
            String ip = ipList.isEmpty() ? "" : ipList.get(0);
            CmnUtil.println("ip=" + ip);

            boolean isWifiActive = dexHelper.isWifiActive();
            WifiInfo wifiInfo = dexHelper.getWifiInfo();
            String ssid = wifiInfo == null ? "NA" : wifiInfo.getSSID().replaceAll("\"", "");
            CmnUtil.println("isWifiActive=" + isWifiActive + " ssid=" + ssid);

            CmnUtil.println("Prepare to take screenshot...");
            long st = SystemClock.elapsedRealtime();
            Bitmap screenshot = ScreenshotUtil.screenshot(1440, 2960, Surface.ROTATION_0);
            CmnUtil.println("Screenshot done. Cost=" + (SystemClock.elapsedRealtime() - st));

            if (screenshot != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                screenshot.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] bitmapBytes = baos.toByteArray();

                FileOutputStream fos = new FileOutputStream("/sdcard/screenshot.jpg");
                fos.write(bitmapBytes);
                fos.flush();
                fos.close();
            }

            CmnUtil.println("Exit");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
