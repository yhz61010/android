package com.leovp.dexdemo.screenshot;

import android.text.TextUtils;

import androidx.annotation.NonNull;

public enum ImageFormat {
    UNKNOWN(""),
    PNG("png"),
    JPEG("jpeg"),
    WEBP("webp");
    private final String value;

    ImageFormat(String value) {
        this.value = value;
    }

    @NonNull
    public static ImageFormat resolveFormat(String format) {
        if (TextUtils.isEmpty(format)) {
            return UNKNOWN;
        }

        for (ImageFormat fmt : values()) {
            if (fmt.value.equalsIgnoreCase(format)) {
                return fmt;
            }
        }

        return UNKNOWN;
    }
}
