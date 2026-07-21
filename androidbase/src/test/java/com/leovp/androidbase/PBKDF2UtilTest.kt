package com.leovp.androidbase

import com.leovp.androidbase.utils.cipher.PBKDF2Util
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Author: Michael Leo
 *
 * Regression guard for the PBKDF2 iteration hardening (review finding H1): the public
 * SHA1 convenience overload must default to the OWASP [PBKDF2Util.ITERATIONS_SHA1] count,
 * not the weak legacy 1000. PBKDF2WithHmacSHA1 is available on plain JVM, so this runs as a
 * standard unit test.
 */
class PBKDF2UtilTest {
    @Test
    fun `SHA1 overload default iterations differ from the legacy 1000 count`() {
        val salt = ByteArray(16) { it.toByte() }

        val hardened = PBKDF2Util.generateKeyWithSHA1("passphrase", salt).encoded
        val legacy = PBKDF2Util
            .generateKeyWithSHA1("passphrase", salt, PBKDF2Util.ITERATIONS_LEGACY)
            .encoded

        // Different iteration counts must yield different derived key material; equality would
        // mean the default silently regressed back to 1000 iterations.
        assertFalse(hardened.contentEquals(legacy))
    }

    @Test
    fun `SHA256 fallback matches JCA PBKDF2 output`() {
        val passphrase = "passphrase".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val iterations = 1024
        val keyLengthBits = 256
        val providerKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase, salt, iterations, keyLengthBits))
            .encoded
        val fallbackMethod = PBKDF2Util::class.java.getDeclaredMethod(
            "pbkdf2HmacSha256Fallback",
            CharArray::class.java,
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )

        fallbackMethod.isAccessible = true
        val fallbackKey = fallbackMethod.invoke(
            PBKDF2Util,
            passphrase,
            salt,
            iterations,
            keyLengthBits
        ) as ByteArray

        assertContentEquals(providerKey, fallbackKey)
    }

    @Test
    fun `charsToUtf8Bytes encodes multi-byte passphrase identically to String toByteArray`() {
        // Guards the wipe-friendly NIO encoding (review finding F4) against the previous
        // String(passphrase).toByteArray(UTF_8) path: output must be byte-for-byte identical,
        // including multi-byte (CJK / symbol / emoji) code points, so key material never shifts.
        val passphrase = "pa55wörd—✓密码🔐".toCharArray()
        val expected = String(passphrase).toByteArray(Charsets.UTF_8)

        val method = PBKDF2Util::class.java.getDeclaredMethod(
            "charsToUtf8Bytes",
            CharArray::class.java
        )
        method.isAccessible = true
        val actual = method.invoke(PBKDF2Util, passphrase) as ByteArray

        assertContentEquals(expected, actual)
    }

    @Test
    fun `sha256KeyWithFallback uses manual PBKDF2 when provider is unavailable`() {
        val passphrase = "passphrase".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val iterations = 1024
        val keyLengthBits = 256

        // Reference: the real JCA provider result for the same inputs (available on plain JVM).
        val providerKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase, salt, iterations, keyLengthBits))
            .encoded

        // Simulate an API 21-25 device whose provider does not expose PBKDF2WithHmacSHA256.
        // The dispatch must route to the manual fallback and produce the same key material.
        val derived = PBKDF2Util.sha256KeyWithFallback(
            passphrase,
            salt,
            iterations,
            keyLengthBits
        ) {
            throw NoSuchAlgorithmException("PBKDF2WithHmacSHA256 not available")
        }

        assertContentEquals(providerKey, derived.encoded)
    }

    @Test
    fun `sha256KeyWithFallback rethrows non-availability failures instead of masking them`() {
        val passphrase = "passphrase".toCharArray()
        val salt = ByteArray(16) { it.toByte() }

        // A malformed-spec style failure is NOT "provider unavailable"; it must propagate rather
        // than be silently swallowed by the SHA256 fallback path.
        assertThrows<InvalidKeySpecException> {
            PBKDF2Util.sha256KeyWithFallback(passphrase, salt, 1024, 256) {
                throw InvalidKeySpecException("malformed spec")
            }
        }
    }
}
