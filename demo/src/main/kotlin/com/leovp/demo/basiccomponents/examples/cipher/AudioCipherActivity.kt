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
import com.leovp.log.base.d
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.SecretKey
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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

    private suspend fun playMP3(mp3SoundByteArray: ByteArray) {
        // The main coroutine owns the candidate until IO preparation has completed. Destruction
        // cancels the handoff; finally then releases it on main, after prepare has returned.
        val candidate = MediaPlayer()
        var published = false
        try {
            withContext(Dispatchers.IO) {
                val tempMp3 = File.createTempFile("decrypted_temp_music_file", "mp3", cacheDir)
                try {
                    FileOutputStream(tempMp3).use { it.write(mp3SoundByteArray) }
                    FileInputStream(tempMp3).use { input ->
                        candidate.setDataSource(input.fd)
                        candidate.prepare()
                    }
                    currentCoroutineContext().ensureActive()
                } finally {
                    tempMp3.delete()
                }
            }
            currentCoroutineContext().ensureActive()
            candidate.start()
            player?.release()
            player = candidate
            published = true
        } finally {
            if (!published) candidate.release()
        }
    }

    fun onEncryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.btnEncryptAudio.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val cost = measureTimeMillis {
                saveFile(resources.openRawResource(R.raw.music).readBytes())
            }
            launch(Dispatchers.Main) { binding.btnEncryptAudio.isEnabled = true }
            toast("Music file encrypted! cost=$cost")
            d(ITAG) { "Music file encrypted! cost=$cost" }
        }
    }

    fun onDecryptAudioClick(@Suppress("UNUSED_PARAMETER") view: View) {
        binding.btnDecryptAudio.isEnabled = false
        lifecycleScope.launch {
            try {
                val cost = measureTimeMillis {
                    val decryptedAudio = withContext(Dispatchers.IO) {
                        val mp3File = File(getExternalFilesDir(null), ENCRYPTED_MP3_FILE_NAME)
                        val encryptedAudio = FileInputStream(mp3File).use { it.readBytes() }
                        AESUtil.decrypt(encryptedAudio, secretKey)
                    }
                    playMP3(decryptedAudio)
                }
                toast("Play decrypted music! cost=$cost")
                d(ITAG) { "Play decrypted music! cost=$cost" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
            } finally {
                if (!isDestroyed) binding.btnDecryptAudio.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        val activePlayer = player
        player = null
        activePlayer?.release()
        super.onDestroy()
    }
}
