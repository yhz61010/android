package com.leovp.dex_sdk;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.leovp.dex_sdk.util.CmnUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Author: Michael Leo
 * Date: 2022/1/17 14:43
 */
public final class SurfaceControl {
    private static final String TAG = "SurfaceControl";

    private static final Class<?> SURFACE_CONTROL_CLASS;
    private static Method getBuiltInDisplayMethod;

    static {
        try {
            SURFACE_CONTROL_CLASS = Class.forName("android.view.SurfaceControl");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param rotation 0: 0 degree rotation (natural orientation)<br/>
     *                 1: 90 degree rotation<br/>
     *                 2: 180 degree rotation<br/>
     *                 3: 270 degree rotation
     */
    @SuppressLint({"PrivateApi", "BlockedPrivateApi"})
    @Nullable
    public static Bitmap screenshot(int width, int height, int rotation) {
        Bitmap bitmap = null;
        try {
            Method declaredMethod;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
                // See: ScreenshotController#captureScreenshot
//            final IBinder displayToken = SurfaceControl.getPhysicalDisplayToken(
//                    physicalAddress.getPhysicalDisplayId());
//            final SurfaceControl.DisplayCaptureArgs captureArgs =
//                    new SurfaceControl.DisplayCaptureArgs.Builder(displayToken)
//                            .setSourceCrop(crop)
//                            .setSize(width, height)
//                            .build();
//            final SurfaceControl.ScreenshotHardwareBuffer screenshotBuffer =
//                    SurfaceControl.captureDisplay(captureArgs);
//            Bitmap screenshot = screenshotBuffer == null ? null : screenshotBuffer.asBitmap();

                final Class<?> captureArgsClass = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs");
                final Class<?> innerBuilderClass = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs$Builder");
                final Method setSzMethod = innerBuilderClass.getDeclaredMethod("setSize", int.class, int.class);
                final Method buildMethod = innerBuilderClass.getDeclaredMethod("build");
                final Class<?> screenshotBufferClass = Class.forName("android.view.SurfaceControl$ScreenshotHardwareBuffer");
                final Method asBitmapMethod = screenshotBufferClass.getDeclaredMethod("asBitmap");

                Constructor<?> captureArgsBuilderConstructor = innerBuilderClass.getDeclaredConstructor(IBinder.class);
                Object captureArgsBuilder = captureArgsBuilderConstructor.newInstance(getBuiltInDisplay());
                setSzMethod.invoke(captureArgsBuilder, width, height);
                Object captureArgs = buildMethod.invoke(captureArgsBuilder);

                Method captureDisplayMethod = SURFACE_CONTROL_CLASS.getDeclaredMethod("captureDisplay", captureArgsClass);
                final Object screenshotBuffer = captureDisplayMethod.invoke(null, captureArgs);
                bitmap = screenshotBuffer == null ? null : (Bitmap) asBitmapMethod.invoke(screenshotBuffer);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android 9, 10, 11
                declaredMethod = SURFACE_CONTROL_CLASS.getDeclaredMethod("screenshot", Rect.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
                bitmap = (Bitmap) declaredMethod.invoke(null, new Rect(), width, height, rotation);
            } else { // Android 8 or lower
                declaredMethod = SURFACE_CONTROL_CLASS.getDeclaredMethod("screenshot", Integer.TYPE, Integer.TYPE);
                bitmap = (Bitmap) declaredMethod.invoke(null, new Object[]{width, height});
            }
        } catch (Exception e) {
            CmnUtil.println(TAG, "screenshot error", e);
        }
        return bitmap;
    }

    private static Method getGetBuiltInDisplayMethod() throws NoSuchMethodException {
        if (getBuiltInDisplayMethod == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                getBuiltInDisplayMethod = SURFACE_CONTROL_CLASS.getMethod("getBuiltInDisplay", Integer.TYPE);
            } else {
                // The method signature has been changed in Android Q+
                getBuiltInDisplayMethod = SURFACE_CONTROL_CLASS.getMethod("getInternalDisplayToken");
            }
        }
        return getBuiltInDisplayMethod;
    }

    public static IBinder getBuiltInDisplay() {
        try {
            Method method = getGetBuiltInDisplayMethod();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // default display 0
                return (IBinder) method.invoke(null, 0);
            }

            return (IBinder) method.invoke(null);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            CmnUtil.println(TAG, "getBuiltInDisplay() error.", e);
            return null;
        }
    }
}
