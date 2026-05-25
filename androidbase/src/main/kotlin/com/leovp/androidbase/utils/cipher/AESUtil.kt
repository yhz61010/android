package com.leovp.androidbase.utils.cipher

import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.bytes.toHexString
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
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return The result includes a salt prefix of DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun encrypt(plainText: String, secKey: String, useSHA512: Boolean = true): String =
        encrypt(plainText.toByteArray(), secKey, useSHA512).toHexString(true, "")

    /**
     * Decrypt string with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherText Notice that the cipher data includes the salt prefix whose length is
     * DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun decrypt(cipherText: String, secKey: String, useSHA512: Boolean = true): String =
        decrypt(cipherText.hexToByteArray(), secKey, useSHA512).decodeToString()

    // ==============================================================

    /**
     * Encrypt string with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * You can use your SecretKey or generate it like this:
     * ```
     * val secKey: SecretKey = AESUtil.generateKey()
     * // or
     * // val secKey: SecretKey = PBKDF2Util.generateKeyWithSHA512("password")
     * ```
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return The result includes a salt prefix of DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun encrypt(plainText: String, secKey: SecretKey, useSHA512: Boolean = true): String =
        encrypt(plainText.toByteArray(), secKey, useSHA512).toHexString(true, "")

    /**
     * Decrypt string with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherText Notice that the cipher data includes the salt prefix whose length is
     * DEFAULT_PRE_SALT_LENGTH (4 bytes).
     * @param secKey You must use the same SecretKey or else the decryption will fail.
     */
    fun decrypt(cipherText: String, secKey: SecretKey, useSHA512: Boolean = true): String =
        decrypt(cipherText.hexToByteArray(), secKey, useSHA512).decodeToString()

    // ==============================================================

    /**
     * Encrypt string with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey = "I'm a key."
     *
     * val encryptedString: String = AESUtil.encrypt(plainText, secKey.toByteArray())
     * val decryptedString: String = AESUtil.decrypt(encryptedString, secKey.toByteArray())
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return The result includes a salt prefix of DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun encrypt(plainText: String, secKey: ByteArray, useSHA512: Boolean = true): String =
        encrypt(plainText.toByteArray(), secKey, useSHA512).toHexString(true, "")

    /**
     * Decrypt string with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey = "I'm a key."
     *
     * val encryptedString: String = AESUtil.encrypt(plainText, secKey.toByteArray())
     * val decryptedString: String = AESUtil.decrypt(encryptedString, secKey.toByteArray())
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherText Notice that the cipher data includes the salt prefix whose length is
     * DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun decrypt(cipherText: String, secKey: ByteArray, useSHA512: Boolean = true): String =
        decrypt(cipherText.hexToByteArray(), secKey, useSHA512).decodeToString()

    // ==============================================================

    /**
     * Encrypt bytes with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
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
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return The result includes a salt prefix of DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun encrypt(plainBytes: ByteArray, secKey: String, useSHA512: Boolean = true): ByteArray =
        encrypt(plainBytes, secKey.toByteArray(), useSHA512)

    /**
     * Decrypt bytes with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
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
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherBytes Notice that the cipher data includes the salt prefix whose length is
     * DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun decrypt(cipherBytes: ByteArray, secKey: String, useSHA512: Boolean = true): ByteArray =
        decrypt(cipherBytes, secKey.toByteArray(), useSHA512)

    // ==============================================================

    /**
     * Encrypt bytes with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey: SecretKey = AESUtil.generateKey()
     * // or
     * // val secKey: SecretKey = PBKDF2Util.generateKeyWithSHA512("password")
     *
     * val encryptedBytes: ByteArray = AESUtil.encrypt(plainText.toByteArray(), secKey)
     * val decryptedBytes: ByteArray = AESUtil.decrypt(encryptedBytes, secKey)
     * val decryptedAsString: String = decryptedBytes.decodeToString()
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return The result includes a salt prefix of DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun encrypt(plainData: ByteArray, secKey: SecretKey, useSHA512: Boolean = true): ByteArray =
        encrypt(plainData, secKey.encoded, useSHA512)

    /**
     * Decrypt bytes with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey: SecretKey = AESUtil.generateKey()
     * // or
     * // val secKey: SecretKey = PBKDF2Util.generateKeyWithSHA512("password")
     *
     * val encryptedBytes: ByteArray = AESUtil.encrypt(plainText.toByteArray(), secKey)
     * val decryptedBytes: ByteArray = AESUtil.decrypt(encryptedBytes, secKey)
     * val decryptedAsString: String = decryptedBytes.decodeToString()
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherBytes Notice that the cipher data includes the salt prefix whose length is
     * DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun decrypt(cipherBytes: ByteArray, secKey: SecretKey, useSHA512: Boolean = true): ByteArray =
        decrypt(cipherBytes, secKey.encoded, useSHA512)

    // ==============================================================
    /**
     * Encrypt bytes with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey = "I'm a key."
     *
     * val encryptedBytes: ByteArray = AESUtil.encrypt(plainText.toByteArray(),
     * secKey.toByteArray())
     * val decryptedBytes: ByteArray = AESUtil.decrypt(encryptedBytes, secKey.toByteArray())
     * val decryptedAsString: String = decryptedBytes.decodeToString()
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return The result includes a salt prefix of DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun encrypt(plainData: ByteArray, secKey: ByteArray, useSHA512: Boolean = true): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_AES)
        val salt: ByteArray = PBKDF2Util.generateSalt(DEFAULT_PRE_SALT_LENGTH)
//        val iv: ByteArray = generateIv(cipher.blockSize)
        val rawKey: SecretKey = if (useSHA512 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PBKDF2Util.generateKeyWithSHA512(secKey.toHexString(true, ""), salt)
        } else {
            PBKDF2Util.generateKeyWithSHA1(secKey.toHexString(true, ""), salt)
        }

//        val ivParams = IvParameterSpec(iv)
        val cipherBytes: ByteArray = cipher.run {
            init(Cipher.ENCRYPT_MODE, rawKey, IvParameterSpec(ByteArray(blockSize)))
            doFinal(plainData)
        }
        return salt + cipherBytes
    }

    /**
     * Encrypt bytes with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * Example:
     * ```
     * val plainText = "I have a dream."
     * val secKey = "I'm a key."
     *
     * val encryptedBytes: ByteArray = AESUtil.encrypt(plainText.toByteArray(),
     * secKey.toByteArray())
     * val decryptedBytes: ByteArray = AESUtil.decrypt(encryptedBytes, secKey.toByteArray())
     * val decryptedAsString: String = decryptedBytes.decodeToString()
     * ```
     *
     * You can encrypt and decrypt any binary data.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherBytes Notice that the cipher data includes the salt prefix whose length is
     * DEFAULT_PRE_SALT_LENGTH (4 bytes).
     */
    fun decrypt(cipherBytes: ByteArray, secKey: ByteArray, useSHA512: Boolean = true): ByteArray {
        val salt: ByteArray = cipherBytes.copyOfRange(0, DEFAULT_PRE_SALT_LENGTH)
        val oriCipherBytes: ByteArray = cipherBytes.copyOfRange(
            DEFAULT_PRE_SALT_LENGTH,
            cipherBytes.size
        )
        val rawKey: SecretKey = if (useSHA512 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PBKDF2Util.generateKeyWithSHA512(secKey.toHexString(true, ""), salt)
        } else {
            PBKDF2Util.generateKeyWithSHA1(secKey.toHexString(true, ""), salt)
        }

        return Cipher.getInstance(CIPHER_AES).run {
            init(Cipher.DECRYPT_MODE, rawKey, IvParameterSpec(ByteArray(blockSize)))
            doFinal(oriCipherBytes)
        }
    }

    // ==============================================================

    @RequiresApi(Build.VERSION_CODES.O)
    fun generateKeyBySHA512(): SecretKey =
        PBKDF2Util.generateKeyWithSHA512(SystemClock.elapsedRealtimeNanos().toString())

    fun generateKeyBySHA1(): SecretKey =
        PBKDF2Util.generateKeyWithSHA1(SystemClock.elapsedRealtimeNanos().toString())

    fun generateKey(): SecretKey = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    ) {
        generateKeyBySHA512()
    } else {
        generateKeyBySHA1()
    }

    // ==============================================================

    private fun generateIv(length: Int): ByteArray {
        val iv = ByteArray(length)
        // Do NOT seed secureRandom! Automatically seeded from system entropy.
        SecureRandom().nextBytes(iv)
        return iv
    }
}
