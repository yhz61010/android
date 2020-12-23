package com.leovp.androidbase.utils.cipher

import androidx.annotation.IntRange
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.exts.kotlin.toHexStringLE
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Author: Michael Leo
 * Date: 20-12-21 下午8:15
 */
@Suppress("unused", "WeakerAccess")
object PBKDF2Util {
    // PBKDF2WithHmacSHA1: will produce a hash length of 160 bits
    // PBKDF2WithHmacSHA512: will produce a hash length of 512 bits
    const val ALGORITHM_SHA = "PBKDF2WithHmacSHA512"

    private const val DEFAULT_PRE_SALT_LENGTH = 4
    private const val DEFAULT_SUFFIX_SALT_LENGTH = 2
    private const val DEFAULT_ITERATIONS = 1000

    /**
     * @param iterations Default value 1000.
     * Number of PBKDF2 hardening rounds to use. Larger values increase
     * computation time. You should select a value that causes computation
     * to take >100ms.
     *
     * @param outputKeyLengthInBits Default value 32 shl 3 = 256
     * AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte.
     *
     * val random = SecureRandom()
     * val salt = ByteArray(32)
     * random.nextBytes(salt)
     */
    fun generateKey(
        plainPassphrase: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): ByteArray {
        return runCatching {
            // PBKDF2WithHmacSHA1
            // PBKDF2WithHmacSHA512
            val secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM_SHA)
            val keySpec = PBEKeySpec(plainPassphrase, salt, iterations, outputKeyLengthInBits)
            secretKeyFactory.generateSecret(keySpec).encoded
        }.getOrThrow()
    }

    fun generateKey(
        plainPassphrase: CharArray,
        salt: String,
        iterations: Int = DEFAULT_ITERATIONS,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): ByteArray = generateKey(plainPassphrase, salt.toByteArray(), iterations, outputKeyLengthInBits)

    fun generateKey(
        plainPassphrase: String,
        salt: String,
        iterations: Int = DEFAULT_ITERATIONS,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): ByteArray = generateKey(plainPassphrase.toCharArray(), salt.toByteArray(), iterations, outputKeyLengthInBits)

    fun generateKey(
        plainPassphrase: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): ByteArray = generateKey(plainPassphrase.toCharArray(), salt, iterations, outputKeyLengthInBits)

    // =====================================

    fun generateKey(
        plainPassphrase: CharArray,
        saltLength: Int = 32,
        iterations: Int = DEFAULT_ITERATIONS,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): ByteArray {
        val salt = ByteArray(saltLength)
        SecureRandom().nextBytes(salt)
        return generateKey(plainPassphrase, salt, iterations, outputKeyLengthInBits)
    }

    fun generateKey(
        plainPassphrase: String,
        saltLength: Int = 32,
        iterations: Int = DEFAULT_ITERATIONS,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): ByteArray = generateKey(plainPassphrase, saltLength, iterations, outputKeyLengthInBits)

    // =====================================

    fun encrypt(plainText: String): String {
        val preSalt = ByteArray(DEFAULT_PRE_SALT_LENGTH)
        SecureRandom().nextBytes(preSalt)
        val preSaltHex = preSalt.toHexStringLE(true, "")

        val suffixSalt = ByteArray(DEFAULT_SUFFIX_SALT_LENGTH)
        SecureRandom().nextBytes(suffixSalt)
        val suffixSaltHex = suffixSalt.toHexStringLE(true, "")

//        if (BuildConfig.DEBUG) LogContext.log.w(ITAG, "preSaltHex=$preSaltHex suffixSaltHex=$suffixSaltHex")

        val onlyHash = generateKey(plainText.toCharArray(), preSalt, DEFAULT_ITERATIONS).toHexStringLE(true, "")
//        if (BuildConfig.DEBUG) LogContext.log.v(ITAG, "onlyHash=$onlyHash")

        return "$preSaltHex$onlyHash$suffixSaltHex"
    }

    /**
     * Usage:
     * ```kotlin
     * PBKDF2Util.validate("1", "724C135B1AD210C216EF40E9C5230D7BF30FC2FEBCAAB75986EAE1356464DF292486B158ADD7")}
     * ```
     */
    fun validate(plainText: String, correctHash: String): Boolean {
//        if (BuildConfig.DEBUG) LogContext.log.v(ITAG, "correctHash=$correctHash")
        val preSalt = correctHash.substring(0, DEFAULT_PRE_SALT_LENGTH * 2)
//        if (BuildConfig.DEBUG) LogContext.log.v(ITAG, "preSalt=$preSalt")
        val onlyHash = correctHash.substring(DEFAULT_PRE_SALT_LENGTH * 2, correctHash.length - DEFAULT_SUFFIX_SALT_LENGTH * 2)
//        if (BuildConfig.DEBUG) LogContext.log.v(ITAG, "onlyHash=$onlyHash")
        val testHash = generateKey(plainText.toCharArray(), preSalt.hexToByteArray(), DEFAULT_ITERATIONS).toHexStringLE(true, "")
//        if (BuildConfig.DEBUG) LogContext.log.v(ITAG, "testHash=$testHash")
        return onlyHash == testHash
    }
}