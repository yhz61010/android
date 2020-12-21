package com.leovp.leoandroidbaseutil.basic_components.examples.cipher

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.cipher.AesUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import java.io.*
import javax.crypto.SecretKey

class AudioCipherActivity : BaseDemonstrationActivity() {
    companion object {
        private const val ENCRYPTED_MP3_FILE_NAME = "encrypted_audio.mp3"

    }

    private var secretKey: SecretKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_cipher)
    }

    private fun saveFile(stringToSave: ByteArray) {
        runCatching {
            val file = File(Environment.getExternalStorageDirectory().absolutePath + File.separator, ENCRYPTED_MP3_FILE_NAME)
            val bos = BufferedOutputStream(FileOutputStream(file))
            secretKey = AesUtil.generateKey()
            val filesBytes = AesUtil.encode(secretKey!!, stringToSave)
            bos.write(filesBytes)
            bos.flush()
            bos.close()
        }.onFailure { it.printStackTrace() }
    }

    private fun decode(mp3File: File) {
        runCatching { playMP3(AesUtil.decode(secretKey!!, FileInputStream(mp3File).use { it.readBytes() })) }.onFailure { it.printStackTrace() }
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

    private fun playMP3(mp3SoundByteArray: ByteArray?) {
        runCatching {
            // Create temp file that will hold byte array
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
        // saveFile("Hello World")
        saveFile(getAudioFile()!!)
        toast("onEncryptAudioClick")
    }

    fun onDecryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val file = File(Environment.getExternalStorageDirectory().absolutePath + File.separator, ENCRYPTED_MP3_FILE_NAME)
        decode(file)
        toast("onDecryptAudioClick")
    }
}