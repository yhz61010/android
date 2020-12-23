package com.leovp.androidbase.utils.cipher

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * The RSA key MUST BE 2048 bits or higher.
 *
 * Author: Michael Leo
 * Date: 19-12-30 下午6:23
 */
@Suppress("unused")
object RSAUtil {
    private const val KEY_SIZE = 2048
    private const val MAX_ENCRYPT_LEN = KEY_SIZE / 8 - 11

    private const val MAX_DECRYPT_LEN = KEY_SIZE / 8

    // The RSA key MUST BE 2048 bits or higher.
    fun decrypt(pubKey: String, encryptedData: ByteArray?): String? {
        return try {
            val keyBytes = Base64.decode(pubKey.toByteArray(), Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val key = keyFactory.generatePublic(spec)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            String(cipher.doFinal(encryptedData))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // The RSA key MUST BE 2048 bits or higher.
    fun encrypt(priKey: String, plainText: String): String? {
        return try {
            val data =
                PKCS8EncodedKeySpec(Base64.decode(priKey.toByteArray(), Base64.NO_WRAP))
            val factory = KeyFactory.getInstance("RSA")
            val key = factory.generatePrivate(data)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            Base64.encodeToString(cipher.doFinal(plainText.toByteArray()), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // The RSA key MUST BE 2048 bits or higher.
    fun encryptStringByFragment(priKey: String, wholeText: String): String? {
        return if (wholeText.length > MAX_ENCRYPT_LEN) {
            val str1 = wholeText.substring(0, MAX_ENCRYPT_LEN)
            val str2 = wholeText.substring(MAX_ENCRYPT_LEN)
            """
     ${encrypt(priKey, str1)}
     ${encryptStringByFragment(priKey, str2)}
     """.trimIndent()
        } else {
            encrypt(priKey, wholeText)
        }
    }

    // The RSA key MUST BE 2048 bits or higher.
    fun decryptStringByFragment(pubKey: String, wholeText: String): String {
        val result = StringBuilder()
        val configParts = wholeText.split("\n").toTypedArray()
        for (part in configParts) {
            result.append(decrypt(pubKey, Base64.decode(part, Base64.NO_WRAP)))
        }
        return result.toString()
    }
}