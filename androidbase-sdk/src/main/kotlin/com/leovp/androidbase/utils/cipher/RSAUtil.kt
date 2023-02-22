@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.androidbase.utils.cipher

import android.security.keystore.KeyProperties
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.bytes.toHexStringLE
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher


/**
 * The RSA key MUST BE 2048 bits or higher.
 *
 * Author: Michael Leo
 * Date: 19-12-30 下午6:23
 */
@Suppress("unused")
object RSAUtil {
    /**
     * Recommended for RSA:
     * - RSA/None/OAEPWithSHA-256AndMGF1Padding
     * - RSA/ECB/OAEPWithSHA-1AndMGF1Padding
     * - RSA/ECB/OAEPWithSHA-256AndMGF1Padding
     * - RSA/ECB/OAEPWithSHA-384AndMGF1Padding
     *
     * The ECB mode can be used for RSA when "None" is not available with the security provider used.
     * In that case, ECB will be treated as "None" for RSA.
     *
     * It's better not to use **RSA/ECB/PKCS1Padding**.
     */
    private const val CIPHER_TRANSFORMATION = "RSA"

    // private val sp = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

    // The RSA key MUST BE 2048 bits or higher.
    private const val KEY_SIZE = 2048
    private const val MAX_ENCRYPT_LEN = KEY_SIZE / 8 - 11
    private const val MAX_DECRYPT_LEN = KEY_SIZE / 8

    fun getKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA).apply {
            // initialize(KeyGenParameterSpec.Builder(
            //     "leo-rsa-keypair",
            //     KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            //     .setDigests(
            //         KeyProperties.DIGEST_SHA1,
            //         KeyProperties.DIGEST_SHA256,
            //         KeyProperties.DIGEST_SHA384,
            //         KeyProperties.DIGEST_SHA512,
            //     )
            //     .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            //     .setKeySize(KEY_SIZE)
            //     .build())
            initialize(KEY_SIZE)
        }.generateKeyPair()
    }

    /**
     * Encrypt by public key which can get from [getKeyPair] method.
     *
     * Example:
     * ```
     * val keyPair = RSAUtil.getKeyPair()
     * val priKey = keyPair.private.encoded
     * val pubKey = keyPair.public.encoded
     *
     * val encrypted = RSAUtil.encrypt(pubKey, plainText)!!
     * val encryptedStr = encrypted.toHexStringLE(true, "")
     *
     * val decryptBytes = RSAUtil.decrypt(priKey, encrypted)
     * val decryptString = RSAUtil.decrypt(priKey, encryptedStr.hexToByteArray())
     * ```
     */
    fun encrypt(encodedPubKey: ByteArray, plainText: String): ByteArray? {
        return encrypt(encodedPubKey, plainText.toByteArray())
    }

    /**
     * Encrypt by public key which can get from [getKeyPair] method.
     *
     * Example:
     * ```
     * val keyPair = RSAUtil.getKeyPair()
     * val priKey = keyPair.private.encoded
     * val pubKey = keyPair.public.encoded
     *
     * val encrypted = RSAUtil.encrypt(pubKey, plainBytes)!!
     * val encryptedStr = encrypted.toHexStringLE(true, "")
     *
     * val decryptBytes = RSAUtil.decrypt(priKey, encrypted)
     * val decryptString = RSAUtil.decrypt(priKey, encryptedStr.hexToByteArray())
     * ```
     */
    fun encrypt(encodedPubKey: ByteArray, plainData: ByteArray): ByteArray? {
        return runCatching {
            val spec = X509EncodedKeySpec(encodedPubKey)
            val factory = KeyFactory.getInstance(CIPHER_TRANSFORMATION)
            val pubKey = factory.generatePublic(spec)
            cipherDoFinal(Cipher.ENCRYPT_MODE, pubKey, plainData)
        }.getOrNull()
    }

    /**
     * Decrypt by private key which can get from [getKeyPair] method.
     *
     * Example:
     * ```
     * val keyPair = RSAUtil.getKeyPair()
     * val priKey = keyPair.private.encoded
     * val pubKey = keyPair.public.encoded
     *
     * val encrypted = RSAUtil.encrypt(pubKey, plainText)!!
     * val encryptedStr = encrypted.toHexStringLE(true, "")
     *
     * val decryptBytes = RSAUtil.decrypt(priKey, encrypted)
     * val decryptString = RSAUtil.decrypt(priKey, encryptedStr.hexToByteArray())
     * ```
     */
    fun decrypt(encodedPriKey: ByteArray, encryptedData: ByteArray?): ByteArray? {
        return runCatching {
            val spec = PKCS8EncodedKeySpec(encodedPriKey)
            val factory = KeyFactory.getInstance(CIPHER_TRANSFORMATION)
            val priKey = factory.generatePrivate(spec)
            cipherDoFinal(Cipher.DECRYPT_MODE, priKey, encryptedData)
        }.getOrNull()
    }

    private fun cipherDoFinal(opmode: Int, key: java.security.Key, data: ByteArray?): ByteArray? {
        return runCatching {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(opmode, key)
            cipher.doFinal(data)
        }.getOrNull()
    }

    // ==========

    /**
     * Encrypt by public key which can get from [getKeyPair] method.
     */
    fun encryptStringByFragment(pubKey: ByteArray, plainText: String): String? {
        return if (plainText.length > MAX_ENCRYPT_LEN) {
            val str1 = plainText.substring(0, MAX_ENCRYPT_LEN)
            val str2 = plainText.substring(MAX_ENCRYPT_LEN)
            """
     |${encrypt(pubKey, str1)?.toHexStringLE(true, "")}
     |${encryptStringByFragment(pubKey, str2)}
            """.trimMargin()
        } else {
            encrypt(pubKey, plainText)?.toHexStringLE(true, "")
        }
    }

    /**
     * Decrypt by private key which can get from [getKeyPair] method.
     */
    fun decryptStringByFragment(priKey: ByteArray, encryptedText: String): String? {
        return runCatching {
            val result = StringBuilder()
            val configParts = encryptedText.split('\n')
            var decryptedStr: String?
            for (part in configParts) {
                decryptedStr = decrypt(priKey, part.hexToByteArray())?.decodeToString()
                if (decryptedStr == null) {
                    return null
                }
                result.append(decryptedStr)
            }
            result.toString()
        }.getOrNull()
    }
}
