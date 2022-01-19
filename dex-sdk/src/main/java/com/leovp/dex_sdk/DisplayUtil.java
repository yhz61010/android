package com.leovp.dex_sdk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Display;
import android.view.IRotationWatcher;
import android.view.IWindowManager;

import com.leovp.dex_sdk.util.CmnUtil;

/**
 * Author: Michael Leo
 * Date: 2022/1/18 10:51
 */
public final class DisplayUtil {
    private static final String TAG = "DisplayUtil";
    private IWindowManager iWindowManager;

    @SuppressLint("PrivateApi")
    public DisplayUtil() {
        try {
            Object objWindowManager = DexHelper.getInstance().getServiceObject(Context.WINDOW_SERVICE);
            iWindowManager = IWindowManager.Stub.asInterface((IBinder) objWindowManager);
        } catch (Exception e) {
            CmnUtil.println(TAG, "DisplayUtil() error.", e);
        }
    }

    /**
     * * Retrieves the device actual display size.
     *
     * @return {@link Point}
     */
    public Point getCurrentDisplaySize() {
        Point out = new Point();
        try {
            iWindowManager.getInitialDisplaySize(Display.DEFAULT_DISPLAY, out);
            return out;
        } catch (Exception e) {
            CmnUtil.println(TAG, "getCurrentDisplaySize() error.", e);
            return out;
        }
    }

    /**
     * Retrieve the current orientation of the primary screen.
     *
     * @return Constant as per `android.view.Surface.Rotation`
     * @see android.view.Display#DEFAULT_DISPLAY
     */
    public int getScreenRotation() {
        int rotation = 0;

        try {
            Class<?> cls = iWindowManager.getClass();
            try {
                rotation = (Integer) cls.getMethod("getRotation").invoke(iWindowManager);
            } catch (NoSuchMethodException e) {
                rotation = (Integer) cls.getMethod("getDefaultDisplayRotation").invoke(iWindowManager);
            }
        } catch (Exception e) {
            CmnUtil.println(TAG, "getScreenRotation() error.", e);
        }
        return rotation;
    }

    public void setRotateListener(RotateListener listener) {
        try {
            Class<?> clazz = iWindowManager.getClass();

            IRotationWatcher watcher = new IRotationWatcher.Stub() {
                @Override
                public void onRotationChanged(int rotation) throws RemoteException {
                    if (listener != null) {
                        listener.onRotate(rotation);
                    }
                }
            };

            try {
                clazz.getMethod("watchRotation", IRotationWatcher.class, int.class)
                        .invoke(iWindowManager, watcher, Display.DEFAULT_DISPLAY); // 26+
            } catch (NoSuchMethodException ex) {
                clazz.getMethod("watchRotation", IRotationWatcher.class)
                        .invoke(iWindowManager, watcher);
            }

        } catch (Exception e) {
            CmnUtil.println(TAG, "setRotateListener() error.", e);
        }
    }

    public Bitmap rotateBitmap(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public interface RotateListener {
        void onRotate(int rotate);
    }
}
