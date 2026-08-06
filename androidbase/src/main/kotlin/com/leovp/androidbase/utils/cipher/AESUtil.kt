package com.leovp.androidbase.utils.cipher

import android.os.Build
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.bytes.toHexString
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Author: Michael Leo
 * Date: 20-12-21 下午5:39
 *
 * Encryption uses AES-GCM authenticated encryption (AEAD). Each message gets a fresh random
 * salt and IV that are prepended to the ciphertext, together with a 1-byte format version.
 * Cipher layout (new format): `[version:1][salt:16][iv:12][ciphertext+tag]`.
 *
 * Data produced by older versions of this library (CBC with a static zero IV and a 4-byte salt)
 * can still be read through the legacy decrypt path, selected automatically by the version byte.
 *
 * https://stackoverflow.com/questions/13433529/android-4-2-broke-my-encrypt-decrypt-code-and-the-provided-solutions-dont-work/39002997#39002997
 */
@Suppress("unused", "WeakerAccess")
object AESUtil {
    private const val ALGORITHM_AES = "AES"
    private const val CIPHER_GCM = "AES/GCM/NoPadding"

    /** GCM authentication tag length in bits. */
    private const val GCM_TAG_LENGTH_BITS = 128

    /** GCM recommended IV length in bytes. Never reuse an IV under the same key. */
    private const val GCM_IV_LENGTH = 12

    /** Salt length in bytes for the new format (feeds PBKDF2). */
    private const val SALT_LEN = 16

    /** New format version: AES-GCM, PBKDF2-HMAC-SHA256, high iteration count (API 26+). */
    private const val VERSION_GCM_SHA256: Byte = 0x01

    /** New format version: AES-GCM, PBKDF2-HMAC-SHA1, high iteration count (API 21-25 fallback). */
    private const val VERSION_GCM_SHA1: Byte = 0x02

    // ---- Legacy (insecure) format, kept only for decrypting old data ----
    private const val CIPHER_AES_LEGACY = "AES/CBC/PKCS7Padding"
    private const val LEGACY_PRE_SALT_LENGTH = 4

    /**
     * Encrypt string with specified secure key.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return AES-GCM cipher data: [version:1][salt:16][iv:12][ciphertext+tag].
     */
    fun encrypt(plainText: String, secKey: String): String =
        encrypt(plainText.toByteArray(), secKey).toHexString(true, "")

    /**
     * Decrypt string with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherText may be either the new AES-GCM format or a legacy
     * ciphertext; the format is detected automatically from the leading version byte.
     */
    fun decryptLegacy(cipherText: String, secKey: String, useSHA512: Boolean = true): String =
        decryptLenient(
            cipherText.hexToByteArray(),
            secKey.toByteArray(),
            useSHA512
        ).decodeToString()

    // ==============================================================

    /**
     * Encrypt string with specified secure key.
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
     * @return AES-GCM cipher data: [version:1][salt:16][iv:12][ciphertext+tag].
     */
    fun encrypt(plainText: String, secKey: SecretKey): String =
        encrypt(plainText.toByteArray(), secKey).toHexString(true, "")

    /**
     * Decrypt string with specified secure key.
     *
     * If the API level is lower than Android 8.0 (Oreo), API level 26,
     * the [useSHA512] parameter is ignored and SHA-1 is used.
     *
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherText may be either the new AES-GCM format or a legacy
     * ciphertext; the format is detected automatically from the leading version byte.
     * @param secKey You must use the same SecretKey or else the decryption will fail.
     */
    fun decryptLegacy(cipherText: String, secKey: SecretKey, useSHA512: Boolean = true): String =
        decryptLenient(cipherText.hexToByteArray(), secKey.encoded, useSHA512).decodeToString()

    // ==============================================================

    /**
     * Encrypt string with specified secure key.
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
     * @return AES-GCM cipher data: [version:1][salt:16][iv:12][ciphertext+tag].
     */
    fun encrypt(plainText: String, secKey: ByteArray): String =
        encrypt(plainText.toByteArray(), secKey).toHexString(true, "")

    /**
     * Decrypt string with specified secure key.
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
     * @param cipherText may be either the new AES-GCM format or a legacy
     * ciphertext; the format is detected automatically from the leading version byte.
     */
    fun decryptLegacy(cipherText: String, secKey: ByteArray, useSHA512: Boolean = true): String =
        decryptLenient(cipherText.hexToByteArray(), secKey, useSHA512).decodeToString()

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
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @return AES-GCM cipher data: [version:1][salt:16][iv:12][ciphertext+tag].
     */
    fun encrypt(plainBytes: ByteArray, secKey: String): ByteArray =
        encrypt(plainBytes, secKey.toByteArray())

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
     * AES supports 128-bit (16-byte), 192-bit (24-byte), and 256-bit (32-byte) keys.
     * In other words, 16, 24, or 32 bytes.
     *
     * @param cipherBytes may be either the new AES-GCM format or a legacy
     * ciphertext; the format is detected automatically from the leading version byte.
     */
    fun decryptLegacy(
        cipherBytes: ByteArray,
        secKey: String,
        useSHA512: Boolean = true,
    ): ByteArray = decryptLenient(cipherBytes, secKey.toByteArray(), useSHA512)

    // ==============================================================

    /**
     * Encrypt bytes with specified secure key.
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
     * @return AES-GCM cipher data: [version:1][salt:16][iv:12][ciphertext+tag].
     */
    fun encrypt(plainData: ByteArray, secKey: SecretKey): ByteArray =
        encrypt(plainData, secKey.encoded)

    /**
     * Decrypt bytes with specified secure key.
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
     * @param cipherBytes may be either the new AES-GCM format or a legacy
     * ciphertext; the format is detected automatically from the leading version byte.
     */
    fun decryptLegacy(
        cipherBytes: ByteArray,
        secKey: SecretKey,
        useSHA512: Boolean = true,
    ): ByteArray = decryptLenient(cipherBytes, secKey.encoded, useSHA512)

    // ==============================================================
    /**
     * Encrypt bytes with specified secure key.
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
     * @return AES-GCM cipher data: [version:1][salt:16][iv:12][ciphertext+tag].
     */
    fun encrypt(plainData: ByteArray, secKey: ByteArray): ByteArray {
        // AES-GCM authenticated encryption. The KDF is chosen by API level (SHA256 on API 26+,
        // SHA1 on API 21-25) and recorded in the version byte so decryption can reproduce it.
        val useSha256: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val version: Byte = if (useSha256) VERSION_GCM_SHA256 else VERSION_GCM_SHA1
        val salt: ByteArray = PBKDF2Util.generateSalt(SALT_LEN)
        val iv: ByteArray = generateIv(GCM_IV_LENGTH)
        val passphrase = secKey.toHexChars()
        val cipherBytes: ByteArray = try {
            val rawKey: SecretKey = deriveKey(passphrase, salt, useSha256)
            Cipher.getInstance(CIPHER_GCM).run {
                init(Cipher.ENCRYPT_MODE, rawKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                // Authenticate the non-secret framing (version + salt) so it cannot be tampered
                // with independently of the ciphertext.
                updateAAD(byteArrayOf(version) + salt)
                doFinal(plainData)
            }
        } finally {
            passphrase.fill('\u0000')
        }
        return byteArrayOf(version) + salt + iv + cipherBytes
    }

    /**
     * Derive an AES key from [passphrase] and [salt]. [useSha256] selects the KDF: when `true`,
     * PBKDF2-HMAC-SHA256 (provider-backed when available, with a standard HMAC-SHA256 fallback for
     * API 21-25 so version-1 data stays cross-device readable); otherwise PBKDF2-HMAC-SHA1. The
     * caller derives [useSha256] from the format version byte; this function does not read it.
     */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray, useSha256: Boolean): SecretKey {
        val derived: SecretKey = if (useSha256) {
            PBKDF2Util.generateKeyWithSHA256(passphrase, salt, PBKDF2Util.ITERATIONS_SHA256)
        } else {
            PBKDF2Util.generateKeyWithSHA1(passphrase, salt, PBKDF2Util.ITERATIONS_SHA1)
        }
        val encoded = derived.encoded
        return try {
            SecretKeySpec(encoded, ALGORITHM_AES)
        } finally {
            encoded.fill(0)
        }
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
     * @param cipherBytes may be either the new AES-GCM format or a legacy
     * ciphertext; the format is detected automatically from the leading version byte.
     * @param useSHA512 affects ONLY the deprecated legacy decrypt path (selecting the SHA-512 vs
     * SHA-1 legacy derivation on API 26+); it has no effect on the new AES-GCM format, whose KDF
     * is chosen by the recorded version byte.
     *
     * Note: each call runs a full-strength PBKDF2 derivation (hundreds of thousands of rounds).
     * That is intentional, but avoid calling this in a tight loop over attacker-supplied blobs;
     * for known new-format data that must fail on tampering, prefer [decrypt].
     */
    fun decryptLegacy(
        cipherBytes: ByteArray,
        secKey: ByteArray,
        useSHA512: Boolean = true,
    ): ByteArray = decryptLenient(cipherBytes, secKey, useSHA512)

    private fun decryptLenient(
        cipherBytes: ByteArray,
        secKey: ByteArray,
        useSHA512: Boolean,
    ): ByteArray {
        val version: Byte? = cipherBytes.firstOrNull()
        if (version == VERSION_GCM_SHA256 || version == VERSION_GCM_SHA1) {
            // Try the new AES-GCM format first. If authentication fails, the byte stream is
            // most likely a legacy ciphertext whose first byte happened to match a version
            // marker, so fall back to the legacy path instead of failing outright.
            runCatching { return decryptGcm(cipherBytes, secKey) }
        }
        return legacyDecrypt(cipherBytes, secKey, useSHA512)
    }

    /**
     * Strict AES-GCM decryption. Requires the new versioned AES-GCM format and NEVER falls back
     * to the legacy path, so a successful return always means the data was authenticated (AEAD).
     * Throws [IllegalArgumentException] if the input is not the AES-GCM format, or a
     * [javax.crypto.AEADBadTagException] if authentication fails.
     *
     * Prefer this over [decryptLegacy] whenever the data is known to be in the new format, and
     * you want tamper detection rather than the lenient legacy fallback.
     */
    fun decrypt(cipherText: String, secKey: String): String =
        decrypt(cipherText.hexToByteArray(), secKey.toByteArray()).decodeToString()

    /**
     * Strict AES-GCM decryption. Requires the new versioned AES-GCM format and NEVER falls back
     * to the legacy path, so a successful return always means the data was authenticated (AEAD).
     */
    fun decrypt(cipherText: String, secKey: SecretKey): String =
        decrypt(cipherText.hexToByteArray(), secKey.encoded).decodeToString()

    /**
     * Strict AES-GCM decryption. Requires the new versioned AES-GCM format and NEVER falls back
     * to the legacy path, so a successful return always means the data was authenticated (AEAD).
     */
    fun decrypt(cipherText: String, secKey: ByteArray): String =
        decrypt(cipherText.hexToByteArray(), secKey).decodeToString()

    /**
     * Strict AES-GCM decryption. Requires the new versioned AES-GCM format and NEVER falls back
     * to the legacy path, so a successful return always means the data was authenticated (AEAD).
     */
    fun decrypt(cipherBytes: ByteArray, secKey: String): ByteArray =
        decrypt(cipherBytes, secKey.toByteArray())

    /**
     * Strict AES-GCM decryption. Requires the new versioned AES-GCM format and NEVER falls back
     * to the legacy path, so a successful return always means the data was authenticated (AEAD).
     */
    fun decrypt(cipherBytes: ByteArray, secKey: SecretKey): ByteArray =
        decrypt(cipherBytes, secKey.encoded)

    /**
     * Strict AES-GCM decryption. Requires the new versioned AES-GCM format and NEVER falls back
     * to the legacy path, so a successful return always means the data was authenticated (AEAD).
     * Throws [IllegalArgumentException] if the input is not the AES-GCM format, or a
     * [javax.crypto.AEADBadTagException] if authentication fails.
     *
     * Prefer this over [decryptLegacy] whenever the data is known to be in the new format, and
     * you want tamper detection rather than the lenient legacy fallback.
     */
    fun decrypt(cipherBytes: ByteArray, secKey: ByteArray): ByteArray {
        val version: Byte? = cipherBytes.firstOrNull()
        require(version == VERSION_GCM_SHA256 || version == VERSION_GCM_SHA1) {
            "Not the AES-GCM format: unexpected leading version byte."
        }
        return decryptGcm(cipherBytes, secKey)
    }

    private fun decryptGcm(cipherBytes: ByteArray, secKey: ByteArray): ByteArray {
        val useSha256: Boolean = cipherBytes[0] == VERSION_GCM_SHA256
        val saltEnd = 1 + SALT_LEN
        val ivEnd = saltEnd + GCM_IV_LENGTH
        require(cipherBytes.size > ivEnd) { "Cipher data too short for the new AES-GCM format." }
        val salt: ByteArray = cipherBytes.copyOfRange(1, saltEnd)
        val iv: ByteArray = cipherBytes.copyOfRange(saltEnd, ivEnd)
        val ct: ByteArray = cipherBytes.copyOfRange(ivEnd, cipherBytes.size)
        val passphrase = secKey.toHexChars()
        return try {
            val rawKey: SecretKey = deriveKey(passphrase, salt, useSha256)
            Cipher.getInstance(CIPHER_GCM).run {
                init(Cipher.DECRYPT_MODE, rawKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                // Must match the AAD bound at encryption time: version byte + salt.
                updateAAD(byteArrayOf(cipherBytes[0]) + salt)
                doFinal(ct)
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Decrypt data produced by older versions of this library: AES/CBC with a static zero IV,
     * a 4-byte salt prefix and 1000 PBKDF2 iterations. Retained only for backward compatibility.
     */
    private fun legacyDecrypt(
        cipherBytes: ByteArray,
        secKey: ByteArray,
        useSHA512: Boolean,
    ): ByteArray {
        val salt: ByteArray = cipherBytes.copyOfRange(0, LEGACY_PRE_SALT_LENGTH)
        val oriCipherBytes: ByteArray =
            cipherBytes.copyOfRange(LEGACY_PRE_SALT_LENGTH, cipherBytes.size)
        // Reproduce the original legacy derivation: 1000 iterations. Must pass this explicitly
        // now that the SHA512/SHA1 overloads default to the higher OWASP iteration counts.
        val passphrase = secKey.toHexChars()
        val rawKey: SecretKey = try {
            if (useSHA512 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PBKDF2Util.generateKeyWithSHA512(
                    passphrase,
                    salt,
                    PBKDF2Util.ITERATIONS_LEGACY
                )
            } else {
                PBKDF2Util.generateKeyWithSHA1(
                    passphrase,
                    salt,
                    PBKDF2Util.ITERATIONS_LEGACY
                )
            }
        } finally {
            passphrase.fill('\u0000')
        }
        return Cipher.getInstance(CIPHER_AES_LEGACY).run {
            init(Cipher.DECRYPT_MODE, rawKey, IvParameterSpec(ByteArray(blockSize)))
            doFinal(oriCipherBytes)
        }
    }

    // ==============================================================

    /**
     * Generate a random AES [SecretKey] using [KeyGenerator], seeded from system entropy.
     *
     * @param bits AES key size: 128, 192 or 256. Defaults to 256.
     */
    fun generateKey(bits: Int = 256): SecretKey =
        KeyGenerator.getInstance(ALGORITHM_AES).apply { init(bits) }.generateKey()

    // ==============================================================

    private fun generateIv(@Suppress("SameParameterValue") length: Int): ByteArray {
        val iv = ByteArray(length)
        // Do NOT seed secureRandom! Automatically seeded from system entropy.
        SecureRandom().nextBytes(iv)
        return iv
    }

    private fun ByteArray.toHexChars(): CharArray = CharArray(size * 2).also { output ->
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            output[index * 2] = HEX_CHARS[value ushr 4]
            output[index * 2 + 1] = HEX_CHARS[value and 0x0F]
        }
    }

    private const val HEX_CHARS = "0123456789ABCDEF"
}
