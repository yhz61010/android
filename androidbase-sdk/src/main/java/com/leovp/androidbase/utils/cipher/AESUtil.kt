package com.leovp.androidbase.utils.cipher

import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.exts.kotlin.toHexStringLE
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec


/**
 * Author: Michael Leo
 * Date: 20-12-21 下午5:39
 *
 * https://stackoverflow.com/questions/13433529/android-4-2-broke-my-encrypt-decrypt-code-and-the-provided-solutions-dont-work/39002997#39002997
 */
@Suppress("unused", "WeakerAccess")
object AESUtil {
    private const val ALGORITHM_AES = "AES"
    private const val CIPHER_AES = "AES/CBC/PKCS7Padding"
    private const val DEFAULT_PRE_SALT_LENGTH = 4

    /**
     * Encrypt string with specified secure key.
     *
     * AES allows 128(16*8), 192(24*8) and 256(32*8) bit of key length.
     * In other words 16, 24 or 32 byte.
     */
    fun encrypt(plainText: String, secKey: String): String = encrypt(plainText.toByteArray(), secKey).toHexStringLE(true, "")

    /**
     * Decrypt string with specified secure key.
     *
     * AES allows 128(16*8), 192(24*8) and 256(32*8) bit of key length.
     * In other words 16, 24 or 32 byte.
     */
    fun decrypt(cipherText: String, secKey: String): String = decrypt(cipherText.hexToByteArray(), secKey).decodeToString()

    // ==============================================================

    /**
     * Encrypt bytes with specified secure key.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey = "I'm a key."
     *
     * val encryptedBytes: ByteArray = AESUtil.encrypt(plainText.toByteArray(), secKey)
     * val decryptedBytes: ByteArray = AESUtil.decrypt(encryptedBytes, secKey)
     * val decryptedAsString: String = decryptedBytes.decodeToString()
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES allows 128(16*8), 192(24*8) and 256(32*8) bit of key length.
     * In other words 16, 24 or 32 byte.
     */
    fun encrypt(plainBytes: ByteArray, secKey: String): ByteArray = encrypt(plainBytes, secKey.toByteArray())

    /**
     * Decrypt bytes with specified secure key.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey = "I'm a key."
     *
     * val encryptedBytes: ByteArray = AESUtil.encrypt(plainText.toByteArray(), secKey)
     * val decryptedBytes: ByteArray = AESUtil.decrypt(encryptedBytes, secKey)
     * val decryptedAsString: String = decryptedBytes.decodeToString()
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES allows 128(16*8), 192(24*8) and 256(32*8) bit of key length.
     * In other words 16, 24 or 32 byte.
     */
    fun decrypt(cipherBytes: ByteArray, secKey: String): ByteArray = decrypt(cipherBytes, secKey.toByteArray())

    // ==============================================================

    /**
     * Encrypt bytes with specified secure key.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey = "I'm a key."
     *
     * val encryptedBytes: ByteArray = AESUtil.encrypt(plainText.toByteArray(), secKey.toByteArray())
     * val decryptedBytes: ByteArray = AESUtil.decrypt(encryptedBytes, secKey.toByteArray())
     * val decryptedAsString: String = decryptedBytes.decodeToString()
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES allows 128(16*8), 192(24*8) and 256(32*8) bit of key length.
     * In other words 16, 24 or 32 byte.
     */
    fun encrypt(plainData: ByteArray, secKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_AES)
        val salt: ByteArray = PBKDF2Util.generateSalt(DEFAULT_PRE_SALT_LENGTH)
//        val iv: ByteArray = generateIv(cipher.blockSize)
        val rawKey: SecretKey = PBKDF2Util.generateKeyWithSHA512(secKey.toHexStringLE(true, ""), salt)
//        val ivParams = IvParameterSpec(iv)
        val cipherBytes: ByteArray = cipher.run {
            init(Cipher.ENCRYPT_MODE, rawKey, IvParameterSpec(ByteArray(blockSize)))
            doFinal(plainData)
        }
        return salt + cipherBytes
    }

    fun decrypt(cipherBytes: ByteArray, secKey: ByteArray): ByteArray {
        val salt: ByteArray = cipherBytes.copyOfRange(0, DEFAULT_PRE_SALT_LENGTH)
        val oriCipherBytes: ByteArray = cipherBytes.copyOfRange(DEFAULT_PRE_SALT_LENGTH, cipherBytes.size)
        val rawKey: SecretKey = PBKDF2Util.generateKeyWithSHA512(secKey.toHexStringLE(true, ""), salt)

        return Cipher.getInstance(CIPHER_AES).run {
            init(Cipher.DECRYPT_MODE, rawKey, IvParameterSpec(ByteArray(blockSize)))
            doFinal(oriCipherBytes)
        }
    }

    // ==============================================================

    private fun generateIv(length: Int): ByteArray {
        val iv = ByteArray(length)
        // Do NOT seed secureRandom! Automatically seeded from system entropy.
        SecureRandom().nextBytes(iv)
        return iv
    }
}