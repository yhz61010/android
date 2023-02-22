package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.utils.cipher.RSAUtil
import com.leovp.bytes.toHexStringLE
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.Assertions.assertEquals
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
    private val longPlainText = "I have a dream. A song to sing. To help cope with anything. ".repeat(100)

    @Test
    fun encryptAndDecrypt() {
        val keyPair = RSAUtil.getKeyPair()
        val priKey = keyPair.private.encoded
        val pubKey = keyPair.public.encoded
        // println("private key=${priKey.toHexStringLE(true, "")}")
        // println("public  key=${pubKey.toHexStringLE(true, "")}")

        val encrypted = RSAUtil.encrypt(pubKey, plainText)!!
        val encryptedStr = encrypted.toHexStringLE(true, "")
        // println("encrypted=$encryptedStr")

        val decryptBytes = RSAUtil.decrypt(priKey, encrypted)
        // println("decrypted  bytes=${decryptedBytes?.decodeToString()}")
        val decryptString = RSAUtil.decrypt(priKey, encryptedStr.hexToByteArray())
        // println("decrypted string=${decryptedString?.decodeToString()}")

        assertEquals(plainText, decryptBytes?.decodeToString())
        assertEquals(plainText, decryptString?.decodeToString())

        assertContentEquals(plainBytes, RSAUtil.decrypt(priKey, RSAUtil.encrypt(pubKey, plainBytes)!!))
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

    @Test fun signAndVerify() {
        val keyPair = RSAUtil.getKeyPair()
        val priKey = keyPair.private.encoded
        val pubKey = keyPair.public.encoded
        // println("private key=${priKey.toHexStringLE(true, "")}")
        // println("public  key=${pubKey.toHexStringLE(true, "")}")

        val encrypted = RSAUtil.sign(priKey, plainText)!!
        val encryptedStr = encrypted.toHexStringLE(true, "")
        // println("encrypted=$encryptedStr")

        val decryptBytes = RSAUtil.verify(pubKey, encrypted)
        // println("decrypted  bytes=${decryptedBytes?.decodeToString()}")
        val decryptString = RSAUtil.verify(pubKey, encryptedStr.hexToByteArray())
        // println("decrypted string=${decryptedString?.decodeToString()}")

        assertEquals(plainText, decryptBytes?.decodeToString())
        assertEquals(plainText, decryptString?.decodeToString())

        // assertContentEquals(plainBytes, RSAUtil.verify(priKey, RSAUtil.sign(pubKey, plainBytes)!!))
    }
}
