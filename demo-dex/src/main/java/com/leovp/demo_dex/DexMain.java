package com.leovp.demo_dex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.wifi.WifiInfo;
import android.os.Looper;
import android.os.SystemClock;

import com.leovp.dex_sdk.DexHelper;
import com.leovp.dex_sdk.DisplayUtil;
import com.leovp.dex_sdk.SurfaceControl;
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

            // Some devices internally create a Handler when creating an input Surface, causing an exception:
            //   "Can't create handler inside thread that has not called Looper.prepare()"
            // <https://github.com/Genymobile/scrcpy/issues/240>
            //
            // Use Looper.prepareMainLooper() instead of Looper.prepare() to avoid a NullPointerException:
            //   "Attempt to read from field 'android.os.MessageQueue android.os.Looper.mQueue'
            //    on a null object reference"
            // <https://github.com/Genymobile/scrcpy/issues/921>
//            Looper.prepareMainLooper();
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

            DisplayUtil displayUtil = new DisplayUtil();
            Point displaySize = displayUtil.getCurrentDisplaySize();
            CmnUtil.println("width=" + displaySize.x + " height=" + displaySize.y);

            int rotation = displayUtil.getScreenRotation();
            CmnUtil.println("rotation=" + rotation);

            CmnUtil.println("Prepare to take screenshot...");
            Bitmap screenshot = null;
            long st;
            for (int i = 0; i < 1; i++) {
                st = SystemClock.elapsedRealtime();
                screenshot = SurfaceControl.screenshot(displaySize.x, displaySize.y, rotation);
                CmnUtil.println("Screenshot done. Cost=" + (SystemClock.elapsedRealtime() - st));
            }

            if (screenshot != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                screenshot.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                baos.flush();
                screenshot.recycle();
                byte[] bitmapBytes = baos.toByteArray();

                FileOutputStream fos = new FileOutputStream("/sdcard/screenshot.jpg");
                fos.write(bitmapBytes);
                fos.flush();
                fos.close();
            }


            CmnUtil.println("Exit");
        } catch (Exception e) {
            CmnUtil.println("DexMain error.", e);
        }
    }
}
