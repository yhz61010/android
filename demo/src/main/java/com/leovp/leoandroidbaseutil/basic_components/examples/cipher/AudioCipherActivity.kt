package com.leovp.leoandroidbaseutil.basic_components.examples.cipher

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.utils.cipher.AesUtil
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.SecretKey

class AudioCipherActivity : BaseDemonstrationActivity() {
    companion object {
        private const val ENCRYPTED_MP3_FILE_NAME = "encrypted_audio.mp3"
    }

    private lateinit var secretKey: SecretKey

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_cipher)
        secretKey = AesUtil.generateKey()
    }

    private fun saveFile(dataToEncode: ByteArray) {
        runCatching {
            val encodedData = AesUtil.encode(secretKey, dataToEncode)
            val encodedOutFile = File(getExternalFilesDir(null), ENCRYPTED_MP3_FILE_NAME)
            BufferedOutputStream(FileOutputStream(encodedOutFile)).use { it.write(encodedData) }
        }.onFailure { it.printStackTrace() }
    }

    private fun playMP3(mp3SoundByteArray: ByteArray) {
        runCatching {
            // Create temp file that will hold byte array
            val tempMp3 = File.createTempFile("decrypted_temp_music_file", "mp3", cacheDir)
            tempMp3.deleteOnExit()
            FileOutputStream(tempMp3).use { it.write(mp3SoundByteArray) }
            // Tried reusing instance of media player
            // but that resulted in system crashes...
            val fis = FileInputStream(tempMp3)
            MediaPlayer().run {
                setDataSource(fis.fd)
                prepare()
                start()
            }
        }.onFailure { it.printStackTrace() }
    }

    fun onEncryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        // saveFile("Hello World")
        saveFile(resources.openRawResource(R.raw.music).readBytes())
        toast("onEncryptAudioClick")
    }

    fun onDecryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val mp3File = File(getExternalFilesDir(null), ENCRYPTED_MP3_FILE_NAME)
        runCatching { playMP3(AesUtil.decode(secretKey, FileInputStream(mp3File).use { it.readBytes() })) }.onFailure { it.printStackTrace() }
        toast("onDecryptAudioClick")
    }
}