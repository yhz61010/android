package com.leovp.leoandroidbaseutil.basic_components.examples.cipher

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AudioCipherActivity : BaseDemonstrationActivity() {
    companion object {
        private const val encryptedFileName = "encrypted_audio.mp3"
        private const val algorithm = "AES"
    }

    private var secretKey: SecretKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_cipher)
    }

    private fun generateKey(passphraseOrPin: CharArray, salt: ByteArray): SecretKey? {
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
        }.getOrNull()
    }

    private fun generateKey(): SecretKey? {
        return runCatching {
            // Generate a 256-bit key
            val outputKeyLength = 256
            val secureRandom = SecureRandom()
            // Do *not* seed secureRandom! Automatically seeded from system entropy.
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(outputKeyLength, secureRandom)
            secretKey = keyGenerator.generateKey()
            secretKey
        }.getOrNull()
    }

    private fun encodeFile(secretKey: SecretKey, fileData: ByteArray): ByteArray? {
        return runCatching {
            val data = secretKey.encoded
            val secKeySpec = SecretKeySpec(data, 0, data.size, algorithm)
            val cipher: Cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.ENCRYPT_MODE, secKeySpec, IvParameterSpec(ByteArray(cipher.blockSize)))
            cipher.doFinal(fileData)
        }.getOrNull()
    }

    private fun decodeFile(secretKey: SecretKey, fileData: ByteArray): ByteArray? {
        return runCatching {
            val cipher: Cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ByteArray(cipher.blockSize)))
            cipher.doFinal(fileData)
        }.getOrNull()
    }

    private fun saveFile(stringToSave: ByteArray) {
        runCatching {
            val file = File(Environment.getExternalStorageDirectory().absolutePath + File.separator, encryptedFileName)
            val bos = BufferedOutputStream(FileOutputStream(file))
            secretKey = generateKey()
            val filesBytes = encodeFile(secretKey!!, stringToSave)
            bos.write(filesBytes)
            bos.flush()
            bos.close()
        }.onFailure { it.printStackTrace() }
    }

    private fun decodeFile() {
        try {
            val decodedData = decodeFile(secretKey!!, readFile()!!)
            // String str = new String(decodedData);
            //System.out.println("DECODED FILE CONTENTS : " + str);
            playMp3(decodedData)
        } catch (e: Exception) {
            e.printStackTrace()
            e.printStackTrace()
        }
    }

    private fun readFile(): ByteArray? {
        val file = File(Environment.getExternalStorageDirectory().absolutePath + File.separator, encryptedFileName)
        val size = file.length().toInt()
        val contents = ByteArray(size)
        try {
            val buf = BufferedInputStream(FileInputStream(file))
            try {
                buf.read(contents)
                buf.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        }
        return contents
    }

    private fun getAudioFile(): ByteArray? {
        return try {
            // use recorded file instead of getting file from assets folder.
            val ins: InputStream = resources.openRawResource(R.raw.music)
            val length: Int = ins.available()
            val audioData = ByteArray(length)
            var bytesRead: Int
            val output = ByteArrayOutputStream()
            while (ins.read(audioData).also { bytesRead = it } != -1) {
                output.write(audioData, 0, bytesRead)
            }
            output.toByteArray()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun playMp3(mp3SoundByteArray: ByteArray?) {
        runCatching {
            // create temp file that will hold byte array
            val tempMp3: File = File.createTempFile("temp", "mp3", cacheDir)
            tempMp3.deleteOnExit()
            val fos = FileOutputStream(tempMp3)
            fos.write(mp3SoundByteArray)
            fos.close()
            // Tried reusing instance of media player
            // but that resulted in system crashes...
            val mediaPlayer = MediaPlayer()
            val fis = FileInputStream(tempMp3)
            mediaPlayer.setDataSource(fis.fd)
            mediaPlayer.prepare()
            mediaPlayer.start()
        }.onFailure { it.printStackTrace() }
    }

    fun onEncryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        //saveFile("Hello From CoderzHeaven asaksjalksjals")
        saveFile(getAudioFile()!!)
        toast("onEncryptAudioClick")
    }

    fun onDecryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        decodeFile()
        toast("onDecryptAudioClick")
    }
}