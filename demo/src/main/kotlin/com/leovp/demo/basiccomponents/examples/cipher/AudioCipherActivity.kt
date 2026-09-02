package com.leovp.demo.basiccomponents.examples.cipher

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
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
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioCipherActivity :
    BaseDemonstrationActivity<ActivityAudioCipherBinding>(R.layout.activity_audio_cipher) {
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
        binding.btnEncryptAudio.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val cost = measureTimeMillis {
                saveFile(resources.openRawResource(R.raw.music).readBytes())
            }
            launch(Dispatchers.Main) { binding.btnEncryptAudio.isEnabled = true }
            toast("Music file encrypted! cost=$cost")
        }
    }

    fun onDecryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.btnDecryptAudio.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val cost = measureTimeMillis {
                val mp3File = File(getExternalFilesDir(null), ENCRYPTED_MP3_FILE_NAME)
                runCatching {
                    val encryptedAudio = FileInputStream(mp3File).use { it.readBytes() }
                    playMP3(AESUtil.decrypt(encryptedAudio, secretKey))
                }.onFailure { it.printStackTrace() }
            }
            launch(Dispatchers.Main) { binding.btnDecryptAudio.isEnabled = true }
            toast("Play decrypted music! cost=$cost")
        }
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}
