package com.leovp.androidbase.utils.cipher

import android.os.SystemClock
import androidx.annotation.IntRange
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.exts.kotlin.toHexStringLE
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


/**
 * Author: Michael Leo
 * Date: 20-12-21 下午5:39
 */
@Suppress("unused", "WeakerAccess")
object AESUtil {
    private const val ALGORITHM_AES = "AES"
    private const val CIPHER_AES = "AES/CBC/PKCS7Padding"

    /**
     * @param outputKeyLengthInBits Default value 32 shl 3 = 256
     * AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte.
     */
    fun generateKey(
        secKey: String = SystemClock.elapsedRealtimeNanos().toString(),
        salt: String,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): SecretKey {
        val secKeyBytes = PBKDF2Util.generateKeyWithSHA1(plainPassphrase = secKey, salt = salt, outputKeyLengthInBits = outputKeyLengthInBits)
        return SecretKeySpec(secKeyBytes, ALGORITHM_AES)
    }

    /**
     * @param plainData Bytes to be encrypted
     */
    fun encrypt(secretKey: SecretKey, plainData: ByteArray): ByteArray = doEncrypt(secretKey.encoded, plainData)

    /**
     * @param encryptedData Bytes to be decoded
     */
    fun decrypt(secretKey: SecretKey, encryptedData: ByteArray): ByteArray = doDecrypt(secretKey.encoded, encryptedData)

    private fun doEncrypt(keyBytes: ByteArray, plainData: ByteArray): ByteArray {
        return runCatching {
            val secKeySpec = SecretKeySpec(keyBytes, ALGORITHM_AES)
            Cipher.getInstance(CIPHER_AES).run {
                init(Cipher.ENCRYPT_MODE, secKeySpec, IvParameterSpec(ByteArray(blockSize)))
                doFinal(plainData)
            }
        }.getOrThrow()
    }

    private fun doDecrypt(keyBytes: ByteArray, encryptedData: ByteArray): ByteArray {
        return runCatching {
            val secKeySpec = SecretKeySpec(keyBytes, ALGORITHM_AES)
            Cipher.getInstance(CIPHER_AES).run {
                init(Cipher.DECRYPT_MODE, secKeySpec, IvParameterSpec(ByteArray(blockSize)))
                doFinal(encryptedData)
            }
        }.getOrThrow()
    }

    // ===================================================

    /**
     * Encrypt data with specified secure key.
     *
     * @param secKey AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte.
     */
    fun encrypt(secKey: String, salt: String, plainText: String): String {
        val rawKey: SecretKey = generateKey(secKey, salt)
        return encrypt(rawKey, plainText.toByteArray()).toHexStringLE(true, "")
    }

    /**
     * Decrypt data with specified secure key.
     *
     * @param secKey AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte.
     */
    fun decrypt(secKey: String, salt: String, encryptedString: String): String {
        val rawKey: SecretKey = generateKey(secKey, salt)
        return decrypt(rawKey, encryptedString.hexToByteArray()).decodeToString()
    }
}