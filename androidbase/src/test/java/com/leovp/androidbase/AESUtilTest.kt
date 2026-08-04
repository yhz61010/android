package com.leovp.androidbase

import com.leovp.androidbase.utils.cipher.AESUtil
import com.leovp.androidbase.utils.cipher.PBKDF2Util
import com.leovp.bytes.toHexString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Author: Michael Leo
 *
 * Unit tests for the new AES-GCM authenticated-encryption format.
 *
 * Under plain JVM unit tests `Build.VERSION.SDK_INT` resolves to 0, so encryption exercises
 * the API 21-25 fallback path (VERSION_GCM_SHA1). Round-trip correctness is identical across
 * both KDF paths, so the behavior under test is fully covered here.
 */
class AESUtilTest {
    private val secKey = "I'm a secret key."

    @Test fun `string round-trip returns the original plain text`() {
        val plain = "I have a dream. 我有一个梦想。🚀"

        val encrypted = AESUtil.encrypt(plain, secKey)
        val decrypted = AESUtil.decrypt(encrypted, secKey)

        assertEquals(plain, decrypted)
    }

    @Test fun `byte round-trip returns the original data`() {
        val data = ByteArray(64) { it.toByte() }

        val encrypted = AESUtil.encrypt(data, secKey.toByteArray())
        val decrypted = AESUtil.decrypt(encrypted, secKey.toByteArray())

        assertTrue(data.contentEquals(decrypted))
    }

    @Test fun `same plain text produces different cipher text each time`() {
        val data = "repeat me".toByteArray()

        val first = AESUtil.encrypt(data, secKey.toByteArray())
        val second = AESUtil.encrypt(data, secKey.toByteArray())

        // Random per-message salt and IV guarantee distinct ciphertexts.
        assertFalse(first.contentEquals(second))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `tampered cipher text never decrypts back to the original`() {
        val data = "sensitive payload".toByteArray()
        val encrypted = AESUtil.encrypt(data, secKey.toByteArray())

        // Flip the last byte, which lives inside the GCM ciphertext+tag region.
        val tampered = encrypted.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }

        // GCM authentication must reject the change: decryption either throws or, via the
        // legacy fallback, yields non-original bytes. In no case may it reproduce the plaintext.
        val result = runCatching { AESUtil.decryptLegacy(tampered, secKey.toByteArray()) }
            .getOrNull()
        assertFalse(result != null && result.contentEquals(data))
    }

    @Test fun `decrypt round-trips new-format data`() {
        val data = "strict payload 严格".toByteArray()
        val encrypted = AESUtil.encrypt(data, secKey.toByteArray())

        val decrypted = AESUtil.decrypt(encrypted, secKey.toByteArray())

        assertTrue(data.contentEquals(decrypted))
    }

    @Test fun `decrypt overloads round-trip new-format data`() {
        val plain = "strict overload payload"
        val keyBytes = secKey.toByteArray()
        val secretKey = AESUtil.generateKey()

        assertEquals(plain, AESUtil.decrypt(AESUtil.encrypt(plain, secKey), secKey))
        assertEquals(plain, AESUtil.decrypt(AESUtil.encrypt(plain, keyBytes), keyBytes))
        assertEquals(plain, AESUtil.decrypt(AESUtil.encrypt(plain, secretKey), secretKey))

        val data = plain.toByteArray()
        assertTrue(data.contentEquals(AESUtil.decrypt(AESUtil.encrypt(data, secKey), secKey)))
        assertTrue(
            data.contentEquals(AESUtil.decrypt(AESUtil.encrypt(data, secretKey), secretKey))
        )
    }

    @Test fun `decrypt reads SHA256 version data when SDK is below O`() {
        val data = "sha256 version payload".toByteArray()
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 16).toByte() }
        val version = 0x01.toByte()
        val passphrase = secKey.toByteArray().toHexString(true, "")
        val derived = PBKDF2Util
            .generateKeyWithSHA256(passphrase, salt, PBKDF2Util.ITERATIONS_SHA256)
            .encoded

        val cipherText = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, iv))
            updateAAD(byteArrayOf(version) + salt)
            doFinal(data)
        }
        val encrypted = byteArrayOf(version) + salt + iv + cipherText

        // JVM unit tests have Build.VERSION.SDK_INT == 0, matching the API 21-25 branch.
        // Version 0x01 must still select SHA256, not silently fall back to SHA1.
        val decrypted = AESUtil.decrypt(encrypted, secKey.toByteArray())

        assertTrue(data.contentEquals(decrypted))
    }

    @Test fun `decrypt throws on a tampered ciphertext instead of falling back`() {
        val data = "sensitive payload".toByteArray()
        val encrypted = AESUtil.encrypt(data, secKey.toByteArray())
        val tampered = encrypted.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }

        // Unlike decryptLegacy(), strict decryption must never silently fall back to the legacy path.
        val result = runCatching { AESUtil.decrypt(tampered, secKey.toByteArray()) }
        assertTrue(result.isFailure)
    }

    @Test fun `generateKey produces a 256-bit AES key`() {
        val key = AESUtil.generateKey()

        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size)
    }
}
