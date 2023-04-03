package com.leovp.dexdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import com.leovp.dex.DexHelper;
import com.leovp.dex.SurfaceControl;
import com.leovp.dex.network.IpUtil;
import com.leovp.dex.util.CmnUtil;
import com.leovp.reflection.wrappers.ServiceManager;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Usage:
 * ```shell
 * $ adb push dexdemo.dex /data/local/tmp
 * $ adb exec-out CLASSPATH=/data/local/tmp/dexdemo.dex app_process / com.leovp.demodex.DexMain
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

            final String packageName = BuildConfig.APPLICATION_ID;
            final DexHelper dexHelper = DexHelper.getInstance();

            Context context = dexHelper.getContext(packageName);
            assert context != null;
            CmnUtil.println(context.toString());

            List<String> ipList = IpUtil.getIp();
            String ip = ipList.isEmpty() ? "" : ipList.get(0);
            CmnUtil.println("ip=" + ip);

            @SuppressLint("MissingPermission")
            boolean isWifiActive = dexHelper.isWifiActive();
            CmnUtil.println("isWifiActive=" + isWifiActive);
            WifiInfo wifiInfo = dexHelper.getWifiInfo();
            String ssid = wifiInfo == null ? "NA" : wifiInfo.getSSID().replaceAll("\"", "");
            CmnUtil.println("ssid=" + ssid);

            Context ctx = dexHelper.getContext(packageName);
            CmnUtil.println("context=" + ctx);

            Size displaySize = ServiceManager.INSTANCE.getWindowManager().getCurrentDisplaySize();
            CmnUtil.println("width=" + displaySize.getWidth() + " height=" + displaySize.getHeight());

            int rotation = ServiceManager.INSTANCE.getWindowManager().getRotation();
            CmnUtil.println("rotation=" + rotation);

            CmnUtil.println("Prepare to take screenshot...");
            Bitmap screenshot = null;
            long st;
            for (int i = 0; i < 1; i++) {
                st = SystemClock.elapsedRealtime();
                screenshot = SurfaceControl.screenshot(displaySize.getWidth(), displaySize.getHeight(), rotation);
                CmnUtil.println("Screenshot done. Cost=" + (SystemClock.elapsedRealtime() - st));
            }

            if (screenshot != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                screenshot.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                baos.flush();
                screenshot.recycle();
                byte[] bitmapBytes = baos.toByteArray();

                @SuppressLint("SdCardPath")
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
