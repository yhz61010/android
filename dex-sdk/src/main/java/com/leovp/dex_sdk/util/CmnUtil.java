package com.leovp.dex_sdk.util;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Author: Michael Leo
 * Date: 2022/1/18 10:20
 */
public class CmnUtil {
    private static final String TAG = "CmnUtil";
    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void println(Integer num) {
        log(TAG, String.valueOf(num));
    }

    public static void println(String tag, Integer num) {
        log(tag, String.valueOf(num));
    }

    public static void println(String str) {
        log(TAG, str);
    }

    public static void println(String tag, String str) {
        log(tag, str);
    }

    public static void println(String str, @Nullable Throwable tr) {
        log(TAG, str, tr);
    }

    public static void println(String tag, String str, @Nullable Throwable tr) {
        log(tag, str, tr);
    }

    // =======================================

    private static void log(String tag, String str) {
        log(tag, str, null);
    }

    private static void log(String tag, String str, @Nullable Throwable tr) {
        System.out.printf("[%s]: %s%n%s", TAG, str, Log.getStackTraceString(tr));
        Log.e(tag, str, tr);
    }

    public static String getCurrentDateTime() {
        return SDF.format(new Date());
    }
}
