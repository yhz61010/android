package com.leovp.androidbase.exts.android.utils

import android.content.Context
import androidx.annotation.RawRes
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.utils.file.FileUtil
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Author: Michael Leo
 * Date: 20-7-30 下午7:14
 */
object ResourcesUtil {
    fun readAssetsFileAsString(subdirectory: String?, filename: String): String {
        return app.resources.assets.open(if (subdirectory.isNullOrBlank()) filename else "$subdirectory${File.separatorChar}$filename").use {
            it.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    fun saveRawResourceToFile(@RawRes id: Int, storagePath: String, outFileName: String): String {
        val inputStream: InputStream = app.resources.openRawResource(id)
        val file = File(storagePath)
        if (!file.exists()) {
            file.mkdirs()
        }
        FileUtil.copyInputStreamToFile(inputStream, storagePath + File.separator + outFileName)
        return storagePath + File.separatorChar + outFileName
    }

    fun saveAssetToFile(ctx: Context, assetFileName: String, storagePath: String, outFileName: String): Boolean {
        return runCatching {
            FileUtil.copyInputStreamToFile(ctx.assets.open(assetFileName), File(storagePath, outFileName).absolutePath)
            true
        }.getOrElse {
            it.printStackTrace()
            false
        }
    }
}