package com.leovp.androidbase.utils.cipher

import androidx.annotation.IntRange
import com.leovp.androidbase.exts.kotlin.toHexStringLE
import java.security.SecureRandom
import javax.crypto.SecretKey
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
        plainPassphraseOrPin: CharArray,
        salt: ByteArray,
        iterations: Int = 1000,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): SecretKey {
        return runCatching {
            // PBKDF2WithHmacSHA1
            // PBKDF2WithHmacSHA512
            val secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM_SHA)
            val keySpec = PBEKeySpec(plainPassphraseOrPin, salt, iterations, outputKeyLengthInBits)
            secretKeyFactory.generateSecret(keySpec)
        }.getOrThrow()
    }

    fun generateKey(
        plainPassphraseOrPin: CharArray,
        saltLength: Int = 32,
        iterations: Int = 1000,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3
    ): SecretKey {
        val random = SecureRandom()
        val salt = ByteArray(saltLength)
        random.nextBytes(salt)
        return generateKey(plainPassphraseOrPin, salt, iterations, outputKeyLengthInBits)
    }

    fun hash(plainText: String): String = generateKey(plainText.toCharArray()).encoded.toHexStringLE(true, "")
}