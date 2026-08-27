@file:Suppress("unused", "MemberVisibilityCanBePrivate", "WeakerAccess")

package com.leovp.androidbase.utils.cipher

import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.leovp.android.utils.API
import com.leovp.bytes.toBytes
import java.nio.CharBuffer
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * - PBKDF(Password-based-Key-Derivative-Function, a successor of PBKDF1)
 *
 * This class is suit for generating a secure key.
 * In other words, you can use this class to get a secure key for other cipher.
 *
 * https://stackoverflow.com/questions/13433529/android-4-2-broke-my-encrypt-decrypt-code-and-the-pr
 * ovided-solutions-dont-work/39002997#39002997
 *
 * ### Cross-API portability of non-ASCII passphrases
 *
 * On API 26+ the provider-backed `PBKDF2WithHmacSHA256` encodes the passphrase internally, whereas
 * the API 21-25 fallback encodes it as UTF-8. For ASCII passphrases the two are identical, but a
 * non-ASCII passphrase could in theory derive different key material across API levels/providers,
 * making data non-portable. Callers needing cross-device SHA256 interop should restrict passphrases
 * to ASCII (e.g. a hex/Base64 string), which is exactly what [AESUtil] does — so AESUtil is
 * unaffected. Only direct callers passing raw non-ASCII passphrases need to take note.
 *
 * Author: Michael Leo
 * Date: 20-12-21 下午8:15
 */
object PBKDF2Util {
    // PBKDF2WithHmacSHA1: will produce a hash length of 160 bits
    // PBKDF2WithHmacSHA512: will produce a hash length of 512 bits
    private const val ALGORITHM_SHA1 = "PBKDF2WithHmacSHA1"

    /**
     * As of Android 8.0 (Oreo), API level 26
     *
     * Check this [link](https://developer.android.com/reference/javax/crypto/SecretKeyFactory)
     * to see supported SecretKeyFactory algorithms.
     */
    private const val ALGORITHM_SHA512 = "PBKDF2WithHmacSHA512"

    private const val ALGORITHM_SHA256 = "PBKDF2WithHmacSHA256"
    private const val HMAC_SHA256 = "HmacSHA256"

    private const val DEFAULT_SALT_LENGTH = 32

    /**
     * Derived key length in bits. AES allows 128/192/256-bit keys; we default to 256.
     * Kept independent from [DEFAULT_SALT_LENGTH] which happens to share the value 32.
     */
    private const val DEFAULT_KEY_LENGTH_BITS = 256

    /**
     * Legacy iteration count. Far below current guidance; retained only so that data
     * encrypted by older versions of this library can still be decrypted.
     */
    private const val DEFAULT_ITERATIONS = 1000

    /**
     * Current OWASP Password Storage Cheat Sheet (2025) minimums:
     * - PBKDF2-HMAC-SHA256: 600,000
     * - PBKDF2-HMAC-SHA512: 220,000
     * - PBKDF2-HMAC-SHA1:   1,400,000 (legacy scenarios only)
     */
    const val ITERATIONS_SHA256 = 600_000
    const val ITERATIONS_SHA512 = 220_000
    const val ITERATIONS_SHA1 = 1_400_000

    /**
     * Legacy iteration count (1000) exposed only so [AESUtil]'s deprecated legacy decrypt path
     * can reproduce the original derivation and read old data. New derivations must NOT use this;
     * the SHA256/SHA512/SHA1 overloads default to the [ITERATIONS_SHA256]/[ITERATIONS_SHA512]/
     * [ITERATIONS_SHA1] OWASP minimums instead.
     */
    const val ITERATIONS_LEGACY = DEFAULT_ITERATIONS

    // ===== ALGORITHM_SHA512 - Start
    // ================================================================

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    @RequiresApi(api = API.O)
    fun generateKeyWithSHA512(
        plainPassphrase: CharArray,
        salt: ByteArray,
        iterations: Int = ITERATIONS_SHA512,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKey(
        plainPassphrase,
        salt,
        iterations,
        outputKeyLengthInBits,
        ALGORITHM_SHA512
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    @RequiresApi(api = API.O)
    fun generateKeyWithSHA512(
        plainPassphrase: CharArray,
        salt: String,
        iterations: Int = ITERATIONS_SHA512,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA512(
        plainPassphrase,
        salt.toByteArray(),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    @RequiresApi(api = API.O)
    fun generateKeyWithSHA512(
        plainPassphrase: String,
        salt: String,
        iterations: Int = ITERATIONS_SHA512,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA512(
        plainPassphrase.toCharArray(),
        salt.toByteArray(),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    @RequiresApi(api = API.O)
    fun generateKeyWithSHA512(
        plainPassphrase: String,
        salt: ByteArray,
        iterations: Int = ITERATIONS_SHA512,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA512(
        plainPassphrase.toCharArray(),
        salt,
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    @RequiresApi(api = API.O)
    fun generateKeyWithSHA512(
        plainPassphrase: CharArray,
        saltLength: Int = DEFAULT_SALT_LENGTH,
        iterations: Int = ITERATIONS_SHA512,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA512(
        plainPassphrase,
        generateSalt(saltLength),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    @RequiresApi(api = API.O)
    fun generateKeyWithSHA512(
        plainPassphrase: String,
        saltLength: Int = DEFAULT_SALT_LENGTH,
        iterations: Int = ITERATIONS_SHA512,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA512(
        plainPassphrase.toCharArray(),
        saltLength,
        iterations,
        outputKeyLengthInBits
    )

    // ===== ALGORITHM_SHA512 - End ================================================================

    // ===== ALGORITHM_SHA256 - Start ==============================================================

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA256(
        plainPassphrase: CharArray,
        salt: ByteArray,
        iterations: Int = ITERATIONS_SHA256,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKey(
        plainPassphrase,
        salt,
        iterations,
        outputKeyLengthInBits,
        ALGORITHM_SHA256
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA256(
        plainPassphrase: CharArray,
        salt: String,
        iterations: Int = ITERATIONS_SHA256,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA256(
        plainPassphrase,
        salt.toByteArray(),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA256(
        plainPassphrase: String,
        salt: String,
        iterations: Int = ITERATIONS_SHA256,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA256(
        plainPassphrase.toCharArray(),
        salt.toByteArray(),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA256(
        plainPassphrase: String,
        salt: ByteArray,
        iterations: Int = ITERATIONS_SHA256,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA256(
        plainPassphrase.toCharArray(),
        salt,
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA256(
        plainPassphrase: CharArray,
        saltLength: Int = DEFAULT_SALT_LENGTH,
        iterations: Int = ITERATIONS_SHA256,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA256(
        plainPassphrase,
        generateSalt(saltLength),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA256(
        plainPassphrase: String,
        saltLength: Int = DEFAULT_SALT_LENGTH,
        iterations: Int = ITERATIONS_SHA256,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA256(
        plainPassphrase.toCharArray(),
        saltLength,
        iterations,
        outputKeyLengthInBits
    )

    // ===== ALGORITHM_SHA256 - End ================================================================

    // ===== ALGORITHM_SHA1 - Start ================================================================

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA1(
        plainPassphrase: CharArray,
        salt: ByteArray,
        iterations: Int = ITERATIONS_SHA1,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKey(
        plainPassphrase,
        salt,
        iterations,
        outputKeyLengthInBits,
        ALGORITHM_SHA1
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA1(
        plainPassphrase: CharArray,
        salt: String,
        iterations: Int = ITERATIONS_SHA1,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA1(
        plainPassphrase,
        salt.toByteArray(),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA1(
        plainPassphrase: String,
        salt: String,
        iterations: Int = ITERATIONS_SHA1,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA1(
        plainPassphrase.toCharArray(),
        salt.toByteArray(),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA1(
        plainPassphrase: String,
        salt: ByteArray,
        iterations: Int = ITERATIONS_SHA1,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA1(
        plainPassphrase.toCharArray(),
        salt,
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA1(
        plainPassphrase: CharArray,
        saltLength: Int = DEFAULT_SALT_LENGTH,
        iterations: Int = ITERATIONS_SHA1,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA1(
        plainPassphrase,
        generateSalt(saltLength),
        iterations,
        outputKeyLengthInBits
    )

    /**
     * @return The generated SecretKey. If you just want to get the result in ByteArray, just call
     * [SecretKey#encoded]
     */
    fun generateKeyWithSHA1(
        plainPassphrase: String,
        saltLength: Int = DEFAULT_SALT_LENGTH,
        iterations: Int = ITERATIONS_SHA1,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): SecretKey = generateKeyWithSHA1(
        plainPassphrase.toCharArray(),
        saltLength,
        iterations,
        outputKeyLengthInBits
    )

    // ===== ALGORITHM_SHA1 - End ================================================================

    fun generateSalt(saltLength: Int = DEFAULT_SALT_LENGTH): ByteArray {
        // Do NOT seed secureRandom! Automatically seeded from system entropy.
        val salt = ByteArray(saltLength)
        SecureRandom().nextBytes(salt)
        return salt
    }

    // =====================================
    /**
     * @param iterations Default value 1000.
     * Number of PBKDF2 hardening rounds to use. Larger values increase
     * computation time. You should select a value that causes computation
     * to take >100ms.
     *
     * @param outputKeyLengthInBits Default value 32 shl 3 = 256
     * AES allows 128, 192, and 256 bits of key length. In other words, 16, 24, or 32 bytes.
     *
     * val random = SecureRandom()
     * val salt = ByteArray(32)
     * random.nextBytes(salt)
     *
     * @return The encrypted byte array. To get string result, just call ```toHexStringLE(true,
     * "")```
     */
    private fun generateKey(
        plainPassphrase: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        @IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = DEFAULT_KEY_LENGTH_BITS,
        algorithm: String
    ): SecretKey {
        // LogContext.log.w(ITAG, "salt=${salt.toHexStringLE()} iterations=$iterations
        // outputKeyLengthInBits=$outputKeyLengthInBits")

        // PBKDF2WithHmacSHA1
        // PBKDF2WithHmacSHA512
        val providerDerivation: () -> SecretKey = {
            val secretKeyFactory = SecretKeyFactory.getInstance(algorithm)
            val keySpec = PBEKeySpec(plainPassphrase, salt, iterations, outputKeyLengthInBits)
            try {
                secretKeyFactory.generateSecret(keySpec)
            } finally {
                keySpec.clearPassword()
            }
        }
        return if (algorithm == ALGORITHM_SHA256) {
            sha256KeyWithFallback(
                plainPassphrase,
                salt,
                iterations,
                outputKeyLengthInBits,
                providerDerivation
            )
        } else {
            providerDerivation()
        }
    }

    /**
     * Derive a PBKDF2-HMAC-SHA256 key via [providerDerivation]; only when the JCA provider does not
     * expose `PBKDF2WithHmacSHA256` (API 21-25, [NoSuchAlgorithmException]) fall back to the manual
     * [pbkdf2HmacSha256Fallback]. Any other failure (e.g. a malformed spec) propagates unchanged so
     * genuine errors are not masked by the fallback.
     */
    @VisibleForTesting
    internal fun sha256KeyWithFallback(
        plainPassphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
        outputKeyLengthInBits: Int,
        providerDerivation: () -> SecretKey
    ): SecretKey = runCatching { providerDerivation() }.getOrElse {
        if (it !is NoSuchAlgorithmException) throw it
        val keyBytes = pbkdf2HmacSha256Fallback(
            plainPassphrase,
            salt,
            iterations,
            outputKeyLengthInBits
        )
        try {
            SecretKeySpec(
                keyBytes,
                ALGORITHM_SHA256
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    /**
     * Standard PBKDF2-HMAC-SHA256 fallback for Android API 21-25, where
     * SecretKeyFactory may not expose PBKDF2WithHmacSHA256 but HmacSHA256 is available.
     */
    private fun pbkdf2HmacSha256Fallback(
        plainPassphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
        outputKeyLengthInBits: Int
    ): ByteArray {
        require(iterations > 0) { "iterations must be positive" }
        require(outputKeyLengthInBits > 0 && outputKeyLengthInBits % 8 == 0) {
            "outputKeyLengthInBits must be a positive multiple of 8"
        }

        val passwordBytes = charsToUtf8Bytes(plainPassphrase)
        try {
            val mac = Mac.getInstance(HMAC_SHA256)
            mac.init(SecretKeySpec(passwordBytes, HMAC_SHA256))

            val hLen = mac.macLength
            val dkLen = outputKeyLengthInBits / 8
            val blocks = (dkLen + hLen - 1) / hLen
            val output = ByteArray(blocks * hLen)
            try {
                for (blockIndex in 1..blocks) {
                    val block = blockIndex.toBytes()
                    mac.update(salt)
                    var u = mac.doFinal(block)
                    val t = u.copyOf()
                    try {
                        repeat(iterations - 1) {
                            val previousU = u
                            u = mac.doFinal(previousU)
                            previousU.fill(0)
                            for (i in t.indices) {
                                t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                            }
                        }
                        System.arraycopy(t, 0, output, (blockIndex - 1) * hLen, hLen)
                    } finally {
                        t.fill(0)
                        u.fill(0)
                    }
                }
                return output.copyOf(dkLen)
            } finally {
                output.fill(0)
            }
        } finally {
            passwordBytes.fill(0)
        }
    }

    /**
     * Encode [chars] to UTF-8 bytes WITHOUT materialising an intermediate [String]. A `String`
     * built from the passphrase would be an immutable heap object that cannot be wiped and would
     * linger until GC; encoding through NIO lets the caller zero every transient buffer afterward.
     */
    private fun charsToUtf8Bytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = Charsets.UTF_8.encode(charBuffer)
        val bytes = byteBuffer.array().copyOfRange(byteBuffer.position(), byteBuffer.limit())
        byteBuffer.array().fill(0) // wipe the transient encoding buffer
        return bytes
    }

    // =====================================

    //    fun encryptWithSHA512(plainText: String): String {
    //        val preSalt = ByteArray(DEFAULT_PRE_SALT_LENGTH)
    //        // Do NOT seed secureRandom! Automatically seeded from system entropy.
    //        SecureRandom().nextBytes(preSalt)
    //        val preSaltHex = preSalt.toHexStringLE(true, "")
    //
    //        val suffixSalt = ByteArray(DEFAULT_SUFFIX_SALT_LENGTH)
    //        // Do NOT seed secureRandom! Automatically seeded from system entropy.
    //        SecureRandom().nextBytes(suffixSalt)
    //        val suffixSaltHex = suffixSalt.toHexStringLE(true, "")
    //
    // // LogContext.log.w(ITAG, "encrypt preSaltHex=$preSaltHex suffixSaltHex=$suffixSaltHex")
    //
    // val onlyHash = generateKeyWithSHA512(plainText.toCharArray(), preSalt,
    // DEFAULT_ITERATIONS).toHexStringLE(true, "")
    // //        LogContext.log.w(ITAG, "encrypt onlyHash=$onlyHash")
    //
    //        return "$preSaltHex$onlyHash$suffixSaltHex"
    //    }

    //    /**
    // * This method can valid the encrypted hash which is encrypted by [encryptWithSHA512] method.
    //     *
    //     * Usage:
    //     * ```kotlin
    // * PBKDF2Util.validate("1",
    // "724C135B1AD210C216EF40E9C5230D7BF30FC2FEBCAAB75986EAE1356464DF292486B158ADD7")
    //     * ```
    //     *
    //     * @param plainText The hex plain text should be padded.
    //     */
    //    fun validate(plainText: String, encryptedHash: String): Boolean {
    // //        LogContext.log.w(ITAG, "encryptedHash=encryptedHash")
    //        val preSalt = encryptedHash.substring(0, DEFAULT_PRE_SALT_LENGTH * 2)
    // // LogContext.log.w(ITAG, "preSalt=$preSalt | ${preSalt.hexToByteArray().toJsonString()}")
    // val onlyHash = encryptedHash.substring(DEFAULT_PRE_SALT_LENGTH * 2, encryptedHash.length -
    // DEFAULT_SUFFIX_SALT_LENGTH * 2)
    // //        LogContext.log.w(ITAG, "onlyHash=$onlyHash")
    // val testHash = generateKeyWithSHA512(plainText.toCharArray(), preSalt.hexToByteArray(),
    // DEFAULT_ITERATIONS).toHexStringLE(true, "")
    // //        LogContext.log.w(ITAG, "testHash=$testHash")
    //        return onlyHash == testHash
    //    }
}
