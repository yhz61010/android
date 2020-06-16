package com.ho1ho.androidbase.utils.cipher

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * Author: Michael Leo
 * Date: 19-10-15 下午1:49
 */
@Suppress("unused")
object MD5Util {
    /**
     * Encodes a string to MD5
     *
     * @param str String to encode
     * @return Encoded String
     */
    @Suppress("unused")
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
}