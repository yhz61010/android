package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.androidbase.utils.cipher.RSAUtil
import com.leovp.bytes.toHexStringLE
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

        val decryptedBytes = RSAUtil.decrypt(priKey, encrypted)
        // println("decrypted  bytes=${decryptedBytes?.decodeToString()}")
        val decryptedString = RSAUtil.decrypt(priKey, encryptedStr.hexToByteArray())
        // println("decrypted string=${decryptedString?.decodeToString()}")

        assertEquals(plainText, decryptedBytes?.decodeToString())
        assertEquals(plainText, decryptedString?.decodeToString())
    }

    @Test fun fragment() {
        val keyPair = RSAUtil.getKeyPair()
        val priKey = keyPair.private.encoded
        val pubKey = keyPair.public.encoded
        // println("private key=${priKey.toHexStringLE(true, "")}")
        // println("public  key=${pubKey.toHexStringLE(true, "")}")

        val encrypted = RSAUtil.encryptStringByFragment(pubKey, longPlainText)
        // println("encrypted=${encrypted}")
        val decrypted = RSAUtil.decryptStringByFragment(priKey, encrypted!!)
        // println("decrypted=${decrypted}")
        assertEquals(longPlainText, decrypted)
    }
}
