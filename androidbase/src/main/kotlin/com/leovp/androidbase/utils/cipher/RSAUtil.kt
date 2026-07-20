@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.androidbase.utils.cipher

import android.security.keystore.KeyProperties
import com.leovp.androidbase.exts.kotlin.hexToByteArray
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * The RSA key MUST BE 2048 bits or higher.
 *
 * Author: Michael Leo
 * Date: 19-12-30 下午6:23
 */
@Suppress("unused")
object RSAUtil {
    private const val TAG = "RSAUtil"

    /**
     * Recommended for RSA:
     * - RSA/None/OAEPWithSHA-256AndMGF1Padding
     * - RSA/ECB/OAEPWithSHA-1AndMGF1Padding
     * - RSA/ECB/OAEPWithSHA-256AndMGF1Padding
     * - RSA/ECB/OAEPWithSHA-384AndMGF1Padding
     *
     * The ECB mode can be used for RSA when "None" is not available with the security provider
     * used.
     * In that case, ECB will be treated as "None" for RSA.
     *
     * It's better not to use **RSA/ECB/PKCS1Padding**.
     */
    private const val CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    /** Key algorithm for [KeyFactory] / [KeyPairGenerator] — must stay the bare "RSA". */
    private const val KEY_ALGORITHM = "RSA"

    /** Digital signature algorithm: SHA-256 digest signed with RSA. */
    private const val SIGN_ALGORITHM = "SHA256withRSA"

    // The RSA key MUST BE 2048 bits or higher.
    private const val KEY_SIZE = 2048

    // OAEP-SHA256 overhead: 2 * hLen (32) + 2 bytes. For a 2048-bit key: 256 - 66 = 190.
    private const val MAX_ENCRYPT_LEN = KEY_SIZE / 8 - 2 * 32 - 2

    /**
     * Explicit OAEP parameters so BOTH the primary digest and the MGF1 mask digest are SHA-256.
     * The transformation string alone leaves MGF1 at its SHA-1 default on standard JCA providers,
     * which is a config mismatch and an interop hazard.
     */
    private val OAEP_SPEC = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT
    )

    fun getKeyPair(): KeyPair =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA).apply {
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
    fun encrypt(encodedPubKey: ByteArray, plainText: String): ByteArray? =
        encrypt(encodedPubKey, plainText.toByteArray())

    /**
     * Encrypt by public key which can get from [getKeyPair] method.
     *
     * Example:
     * ```
     * val keyPair = RSAUtil.getKeyPair()
     * val priKey = keyPair.private.encoded
     * val pubKey = keyPair.public.encoded
     *
     * val encrypted = RSAUtil.encrypt(pubKey, plainData)!!
     * val encryptedStr = encrypted.toHexStringLE(true, "")
     *
     * val decryptBytes = RSAUtil.decrypt(priKey, encrypted)
     * val decryptString = RSAUtil.decrypt(priKey, encryptedStr.hexToByteArray())
     * ```
     */
    fun encrypt(encodedPubKey: ByteArray, plainData: ByteArray): ByteArray? = runCatching {
        val spec = X509EncodedKeySpec(encodedPubKey)
        val factory = KeyFactory.getInstance(KEY_ALGORITHM)
        val pubKey = factory.generatePublic(spec)
        cipherDoFinal(Cipher.ENCRYPT_MODE, pubKey, plainData)
    }.onFailure { LogContext.log.e(TAG, "encrypt error", it) }.getOrNull()

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
    fun decrypt(encodedPriKey: ByteArray, encryptedData: ByteArray?): ByteArray? = runCatching {
        val spec = PKCS8EncodedKeySpec(encodedPriKey)
        val factory = KeyFactory.getInstance(KEY_ALGORITHM)
        val priKey = factory.generatePrivate(spec)
        cipherDoFinal(Cipher.DECRYPT_MODE, priKey, encryptedData)
    }.onFailure { LogContext.log.e(TAG, "decrypt error", it) }.getOrNull()

    // ----------

    /**
     * Create a real RSA digital signature ([SIGN_ALGORITHM]) over [plainText] using the
     * private key from [getKeyPair].
     *
     * Example:
     * ```
     * val keyPair = RSAUtil.getKeyPair()
     * val priKey = keyPair.private.encoded
     * val pubKey = keyPair.public.encoded
     *
     * val signature = RSAUtil.sign(priKey, plainText)!!
     * val valid = RSAUtil.verify(pubKey, plainText.toByteArray(), signature)
     * ```
     */
    fun sign(encodedPriKey: ByteArray, plainText: String): ByteArray? =
        sign(encodedPriKey, plainText.toByteArray())

    /**
     * Create a real RSA digital signature ([SIGN_ALGORITHM]) over [data] using the private
     * key from [getKeyPair]. Returns `null` if signing fails.
     */
    fun sign(encodedPriKey: ByteArray, data: ByteArray): ByteArray? = runCatching {
        val priKey = KeyFactory.getInstance(KEY_ALGORITHM)
            .generatePrivate(PKCS8EncodedKeySpec(encodedPriKey))
        Signature.getInstance(SIGN_ALGORITHM).run {
            initSign(priKey)
            update(data)
            sign()
        }
    }.onFailure { LogContext.log.e(TAG, "sign error", it) }.getOrNull()

    /**
     * Verify an RSA digital signature ([SIGN_ALGORITHM]) produced by [sign].
     *
     * @param encodedPubKey The public key from [getKeyPair].
     * @param data The original signed data.
     * @param signature The signature bytes to verify.
     * @return `true` only when [signature] is a valid signature of [data]; `false` otherwise.
     */
    fun verify(encodedPubKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean =
        runCatching {
            val pubKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(encodedPubKey))
            Signature.getInstance(SIGN_ALGORITHM).run {
                initVerify(pubKey)
                update(data)
                verify(signature)
            }
        }.getOrDefault(false)

    // =====

    private fun cipherDoFinal(opmode: Int, key: java.security.Key, data: ByteArray?): ByteArray? =
        runCatching {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(opmode, key, OAEP_SPEC)
            cipher.doFinal(data)
        }.onFailure { LogContext.log.e(TAG, "cipherDoFinal error", it) }.getOrNull()

    // ==========

    /**
     * Encrypt by public key which can get from [getKeyPair] method.
     *
     * The plain text is fragmented by **bytes** (not characters) so multi-byte UTF-8
     * characters are never split in a way that exceeds [MAX_ENCRYPT_LEN]. Each fragment's
     * hex is joined with a newline separator.
     */
    fun encryptStringByFragment(pubKey: ByteArray, plainText: String): String? = runCatching {
        val bytes: ByteArray = plainText.toByteArray()
        val sb = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + MAX_ENCRYPT_LEN, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val encHex = encrypt(pubKey, chunk)?.toHexString(true, "") ?: return null
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(encHex)
            offset = end
        }
        sb.toString()
    }.getOrNull()

    /**
     * Decrypt by private key which can get from [getKeyPair] method.
     *
     * The decrypted bytes of every fragment are reassembled first and decoded to a String
     * only once, so multi-byte characters split across fragments are reconstructed correctly.
     */
    fun decryptStringByFragment(priKey: ByteArray, encryptedText: String): String? = runCatching {
        val out = ByteArrayOutputStream()
        for (part in encryptedText.split('\n')) {
            val decrypted = decrypt(priKey, part.hexToByteArray()) ?: return null
            out.write(decrypted)
        }
        out.toByteArray().decodeToString()
    }.getOrNull()
}
