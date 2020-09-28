package com.leovp.androidbase.utils.cipher

import android.text.TextUtils
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
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
        val md5: MessageDigest
        try {
            md5 = MessageDigest.getInstance("MD5")
            val bytes = md5.digest(str.toByteArray())
            val result = StringBuilder()
            for (b in bytes) {
                var temp = Integer.toHexString(b.toInt() and 0xFF)
                if (temp.length == 1) {
                    temp = "0$temp"
                }
                result.append(temp)
            }
            return result.toString().toUpperCase(Locale.getDefault())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return ""
    }

    fun checkMd5(md5: String, updateFile: File) = calculateFileMd5(updateFile).let {
        if (TextUtils.isEmpty(it)) false else it.equals(md5, ignoreCase = true)
    }

    fun calculateFileMd5(file: File): String {
        if (!file.isFile) {
            return ""
        }
        val buffer = ByteArray(8 shl 10)
        var len: Int
        return kotlin.runCatching {
            FileInputStream(file).use { fis ->
                val digest = MessageDigest.getInstance("MD5")
                while (fis.read(buffer, 0, buffer.size).also { len = it } != -1) {
                    digest.update(buffer, 0, len)
                }
                val md5sum = digest.digest()
                val bigInt = BigInteger(1, md5sum)
                val output = bigInt.toString(16)
                String.format("%32s", output).replace(' ', '0')
            }
        }.getOrDefault("")
    }
}