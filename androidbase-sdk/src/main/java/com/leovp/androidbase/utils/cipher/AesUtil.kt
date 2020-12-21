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

    fun generateKey(): SecretKey {
        return runCatching {
            // Generate a 256-bit key
            val outputKeyLength = 256
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
            val outputKeyLength = 256
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
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