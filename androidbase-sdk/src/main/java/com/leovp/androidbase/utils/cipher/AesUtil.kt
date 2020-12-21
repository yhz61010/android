package com.leovp.androidbase.utils.cipher

import androidx.annotation.IntRange
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Author: Michael Leo
 * Date: 20-12-21 下午5:39
 */
@Suppress("unused", "WeakerAccess")
object AesUtil {
    const val ALGORITHM_AES = "AES/CBC/PKCS7Padding"

    /**
     * @param outputKeyLengthInBits Default value 32 shl 3 = 256
     * AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte.
     */
    fun generateKey(@IntRange(from = 128, to = 256) outputKeyLengthInBits: Int = 32 shl 3): SecretKey {
        return runCatching {
            val secureRandom = SecureRandom()
            // Do NOT seed secureRandom! Automatically seeded from system entropy.
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(outputKeyLengthInBits, secureRandom)
            keyGenerator.generateKey()
        }.getOrThrow()
    }

    /**
     * @param data Bytes to be encrypted
     */
    fun encode(secretKey: SecretKey, data: ByteArray): ByteArray {
        return runCatching {
            val encodedKeyBytes = secretKey.encoded
            val secKeySpec = SecretKeySpec(encodedKeyBytes, 0, encodedKeyBytes.size, ALGORITHM_AES)
            val cipher: Cipher = Cipher.getInstance(ALGORITHM_AES)
            cipher.init(Cipher.ENCRYPT_MODE, secKeySpec, IvParameterSpec(ByteArray(cipher.blockSize)))
            cipher.doFinal(data)
        }.getOrThrow()
    }

    /**
     * @param data Bytes to be decoded
     */
    fun decode(secretKey: SecretKey, data: ByteArray): ByteArray {
        return runCatching {
            Cipher.getInstance(ALGORITHM_AES).run {
                init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ByteArray(blockSize)))
                doFinal(data)
            }
        }.getOrThrow()
    }

}