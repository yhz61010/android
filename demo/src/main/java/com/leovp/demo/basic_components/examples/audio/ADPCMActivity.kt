package com.leovp.demo.basic_components.examples.audio

import android.media.AudioFormat
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import com.leovp.lib_common_android.exts.toast
import com.leovp.audio.AudioPlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityADPCMBinding
import com.leovp.ffmpeg.audio.adpcm.AdpcmImaQtDecoder
import com.leovp.ffmpeg.audio.adpcm.AdpcmImaQtEncoder
import com.leovp.ffmpeg.audio.base.EncodeAudioCallback
import com.leovp.lib_common_android.exts.createFile
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class ADPCMActivity : BaseDemonstrationActivity<ActivityADPCMBinding>() {
    override fun getTagName(): String = ITAG

    companion object {
        private const val OUTPUT_IMA_FILE_NAME = "raw_adpcm_ima_qt.raw"
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_CHANNELS = 2
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityADPCMBinding {
        return ActivityADPCMBinding.inflate(layoutInflater)
    }

    private var player: AudioPlayer? = null

    fun onEncodeToADPCMClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val inputStream = resources.openRawResource(R.raw.raw_pcm_44100_2ch_s16le)
        val pcmData = inputStream.readBytes()
        inputStream.close()

        val outFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
        val os = BufferedOutputStream(FileOutputStream(outFile))

        thread {
            val adpcmImaQtEncoder = AdpcmImaQtEncoder(AUDIO_SAMPLE_RATE, AUDIO_CHANNELS, 64000)
            adpcmImaQtEncoder.encodedCallback = object : EncodeAudioCallback {
                override fun onEncodedUpdate(encodedAudio: ByteArray) {
                    os.write(encodedAudio)
                }
            }
            adpcmImaQtEncoder.encode(pcmData)
            adpcmImaQtEncoder.release()
            os.close()
            toast("Encode done!")
        }
    }

    fun onPlayADPCMClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val decoderInfo =
                AudioDecoderInfo(AUDIO_SAMPLE_RATE,
                    if (AUDIO_CHANNELS == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT)
        player = AudioPlayer(this, decoderInfo, AudioType.PCM)

        val adpcmQT = AdpcmImaQtDecoder(decoderInfo.sampleRate, decoderInfo.channelCount)
        thread {
            //            val inputStream = resources.openRawResource(R.raw.out_adpcm_44100_2ch_64kbps)
            val inFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
            val inputStream = FileInputStream(inFile)
            val musicBytes = inputStream.readBytes()
            inputStream.close()
            val chunkSize: Int = adpcmQT.chunkSize()
            for (i in musicBytes.indices step chunkSize) {
                val chunk = musicBytes.copyOfRange(i, i + chunkSize)
                val st = SystemClock.elapsedRealtimeNanos()
                val pcmBytes = adpcmQT.decode(chunk)
                if (LogContext.enableLog) LogContext.log.i("PCM[${pcmBytes.size}] cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us")
                player?.play(pcmBytes)
            }
            adpcmQT.release()
            player?.release()
        }
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}