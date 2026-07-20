package com.leovp.androidbase

import com.leovp.androidbase.utils.cipher.PBKDF2Util
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

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
}
