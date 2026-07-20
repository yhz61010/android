package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.utils.cipher.RSAUtil
import com.leovp.bytes.toHexString
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Author: Michael Leo
 * Date: 20-8-3 上午11:37
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class RSAUtilTest {
    private val plainText = "I have a dream."
    private val plainBytes = "My auu-".toByteArray()
    private val longPlainText =
        "I have a dream. A song to sing. To help cope with anything. ".repeat(100)

    @Test
    fun encryptAndDecrypt() {
        val keyPair = RSAUtil.getKeyPair()
        val priKey = keyPair.private.encoded
        val pubKey = keyPair.public.encoded
        // println("private key=${priKey.toHexStringLE(true, "")}")
        // println("public  key=${pubKey.toHexStringLE(true, "")}")

        val encrypted = RSAUtil.encrypt(pubKey, plainText)!!
        val encryptedStr = encrypted.toHexString(true, "")
        // println("encrypted=$encryptedStr")

        val decryptBytes = RSAUtil.decrypt(priKey, encrypted)
        // println("decrypted  bytes=${decryptedBytes?.decodeToString()}")
        val decryptString = RSAUtil.decrypt(priKey, encryptedStr.hexToByteArray())
        // println("decrypted string=${decryptedString?.decodeToString()}")

        assertEquals(plainText, decryptBytes?.decodeToString())
        assertEquals(plainText, decryptString?.decodeToString())

        assertContentEquals(
            plainBytes,
            RSAUtil.decrypt(priKey, RSAUtil.encrypt(pubKey, plainBytes)!!)
        )
    }

    @Test fun fragment() {
        val keyPair = RSAUtil.getKeyPair()
        val priKey = keyPair.private.encoded
        val pubKey = keyPair.public.encoded
        // println("private key=${priKey.toHexStringLE(true, "")}")
        // println("public  key=${pubKey.toHexStringLE(true, "")}")

        val encryptedStr = RSAUtil.encryptStringByFragment(pubKey, longPlainText)
        // println("encrypted=${encryptedStr}")
        val decryptedStr = RSAUtil.decryptStringByFragment(priKey, encryptedStr!!)
        // println("decrypted=${decryptedStr}")
        assertEquals(longPlainText, decryptedStr)
    }

    @Test fun `fragment round-trips empty string`() {
        val keyPair = RSAUtil.getKeyPair()
        val priKey = keyPair.private.encoded
        val pubKey = keyPair.public.encoded

        val encryptedStr = RSAUtil.encryptStringByFragment(pubKey, "")
        val decryptedStr = RSAUtil.decryptStringByFragment(priKey, encryptedStr!!)

        assertEquals("", decryptedStr)
    }

    @Test fun signAndVerify() {
        val keyPair = RSAUtil.getKeyPair()
        val priKey = keyPair.private.encoded
        val pubKey = keyPair.public.encoded

        val signature = RSAUtil.sign(priKey, plainText)!!

        // A valid signature over the original data verifies as true.
        assertTrue(RSAUtil.verify(pubKey, plainText.toByteArray(), signature))

        // Tampered data must not verify against the original signature.
        assertFalse(RSAUtil.verify(pubKey, "I have a scheme.".toByteArray(), signature))

        // A tampered signature must not verify either.
        val tamperedSignature = signature.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFalse(RSAUtil.verify(pubKey, plainText.toByteArray(), tamperedSignature))
    }
}
