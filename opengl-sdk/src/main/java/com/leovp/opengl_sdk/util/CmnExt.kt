package com.leovp.opengl_sdk.util

import android.content.Context
import androidx.annotation.RawRes
import java.nio.charset.StandardCharsets

internal fun Context.readAssetsFileAsString(@RawRes rawId: Int): String {
    return resources.openRawResource(rawId).use { it.readBytes().toString(StandardCharsets.UTF_8) }
}