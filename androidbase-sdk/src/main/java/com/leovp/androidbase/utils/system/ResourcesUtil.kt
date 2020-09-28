package com.leovp.androidbase.utils.system

import android.content.res.Resources
import com.leovp.androidbase.utils.file.FileUtil
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Author: Michael Leo
 * Date: 20-7-30 下午7:14
 */
object ResourcesUtil {
    fun readAssetsFileAsString(resources: Resources, subdirectory: String?, filename: String): String {
        return resources.assets.open(if (subdirectory.isNullOrBlank()) filename else "$subdirectory${File.separatorChar}$filename").use {
            it.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    fun saveRawResourceToFile(resources: Resources, id: Int, storagePath: String, fileName: String): String {
        val inputStream: InputStream = resources.openRawResource(id)
        val file = File(storagePath)
        if (!file.exists()) {
            file.mkdirs()
        }
        FileUtil.copyInputStreamToFile(inputStream, storagePath + File.separator + fileName)
        return storagePath + File.separatorChar + fileName
    }
}