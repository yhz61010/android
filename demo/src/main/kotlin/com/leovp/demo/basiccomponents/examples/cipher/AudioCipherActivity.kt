package com.leovp.demo.basiccomponents.examples.cipher

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import com.leovp.android.exts.toast
import com.leovp.androidbase.utils.cipher.AESUtil
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityAudioCipherBinding
import com.leovp.log.base.ITAG
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.SecretKey

class AudioCipherActivity : BaseDemonstrationActivity<ActivityAudioCipherBinding>(R.layout.activity_audio_cipher) {
    override fun getTagName(): String = ITAG

    companion object {
        private const val ENCRYPTED_MP3_FILE_NAME = "encrypted_audio.mp3"
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAudioCipherBinding =
        ActivityAudioCipherBinding.inflate(layoutInflater)

    private lateinit var secretKey: SecretKey

    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secretKey = AESUtil.generateKey()
    }

    private fun saveFile(dataToEncode: ByteArray) {
        runCatching {
            val encodedData = AESUtil.encrypt(dataToEncode, secretKey)
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
            val fis = FileInputStream(tempMp3)
            player = MediaPlayer().apply {
                setDataSource(fis.fd)
                prepare()
                start()
            }
        }.onFailure { it.printStackTrace() }
    }

    fun onEncryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        saveFile(resources.openRawResource(R.raw.music).readBytes())
        toast("Music file encrypted!")
    }

    fun onDecryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val mp3File = File(getExternalFilesDir(null), ENCRYPTED_MP3_FILE_NAME)
        runCatching {
            playMP3(AESUtil.decrypt(FileInputStream(mp3File).use { it.readBytes() }, secretKey))
        }.onFailure { it.printStackTrace() }
        toast("Play decrypted music!")
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}
