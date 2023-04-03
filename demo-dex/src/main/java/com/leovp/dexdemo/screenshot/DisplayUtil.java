package com.leovp.dexdemo.screenshot;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/**
 * Author: Michael Leo
 * Date: 2022/1/18 10:51
 */
public final class DisplayUtil {
    public Bitmap rotateBitmap(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
