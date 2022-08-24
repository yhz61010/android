package com.leovp.androidbase.utils.cipher

import android.text.TextUtils
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

/**
 * Author: Michael Leo
 * Date: 19-10-15 下午1:49
 */
object MD5Util {
    /**
     * Encodes a string to MD5
     *
     * @param str String to encode
     * @return Encoded String
     */
    fun encrypt(str: String): String {
        if (str.isBlank()) {
            return ""
        }
        return runCatching {
            val md5: MessageDigest = MessageDigest.getInstance("MD5")
            val bytes = md5.digest(str.toByteArray())
            val result = StringBuilder()
            for (b in bytes) {
                var temp = Integer.toHexString(b.toInt() and 0xFF)
                if (temp.length == 1) {
                    temp = "0$temp"
                }
                result.append(temp)
            }
            result.toString().uppercase(Locale.getDefault())
        }.getOrDefault("")
    }

    fun checkMd5(md5: String, targetFile: File, bufferSize: Int = 256 shl 10) = calculateFileMd5(targetFile, bufferSize).let {
        if (TextUtils.isEmpty(it)) false else it.equals(md5, ignoreCase = true)
    }

    fun calculateFileMd5(file: File, bufferSize: Int = 256 shl 10): String {
        if (!file.isFile) {
            return ""
        }
        val buffer = ByteArray(bufferSize)
        var len: Int
        return runCatching {
            FileInputStream(file).use { fis ->
                val digest = MessageDigest.getInstance("MD5")
                while (fis.read(buffer, 0, buffer.size).also { len = it } != -1) {
                    digest.update(buffer, 0, len)
                }
                val md5sum = digest.digest()
                val bigInt = BigInteger(1, md5sum)
                val output = bigInt.toString(16)
                String.format(Locale.ENGLISH, "%32s", output).replace(' ', '0')
            }
        }.getOrDefault("")
    }
}
