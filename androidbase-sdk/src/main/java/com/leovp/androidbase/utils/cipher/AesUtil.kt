package com.leovp.androidbase.utils.cipher

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Author: Michael Leo
 * Date: 20-12-21 下午5:39
 */
@Suppress("unused", "WeakerAccess")
object AesUtil {
    private const val ALGORITHM_AES = "AES/CBC/PKCS7Padding"

    // PBKDF2WithHmacSHA1: will produce a hash length of 160 bits
    // PBKDF2WithHmacSHA512: will produce a hash length of 512 bits
    private const val ALGORITHM_SHA = "PBKDF2WithHmacSHA512"

    fun generateKey(): SecretKey {
        return runCatching {
            // Generate a 256-bit key
            // AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte.
            // 32 shl 3 = 256
            val outputKeyLength = 32 shl 3
            val secureRandom = SecureRandom()
            // Do NOT seed secureRandom! Automatically seeded from system entropy.
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(outputKeyLength, secureRandom)
            keyGenerator.generateKey()
        }.getOrThrow()
    }

    /**
     * val random = SecureRandom()
     * val salt = ByteArray(24)
     * random.nextBytes(salt)
     */
    fun generateKey(passphraseOrPin: CharArray, salt: ByteArray): SecretKey {
        return runCatching {
            // Number of PBKDF2 hardening rounds to use. Larger values increase
            // computation time. You should select a value that causes computation
            // to take >100ms.
            val iterations = 1000

            // Generate a 256-bit key
            // AES allows 128, 192 and 256 bit of key length. In other words 16, 24 or 32 byte.
            // 32 shl 3 = 256
            val outputKeyLength = 32 shl 3
            // PBKDF2WithHmacSHA1
            // PBKDF2WithHmacSHA512
            val secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM_SHA)
            val keySpec = PBEKeySpec(passphraseOrPin, salt, iterations, outputKeyLength)
            secretKeyFactory.generateSecret(keySpec)
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