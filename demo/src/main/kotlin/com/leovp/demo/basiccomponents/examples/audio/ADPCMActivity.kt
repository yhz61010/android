package com.leovp.demo.basiccomponents.examples.audio

import android.media.AudioFormat
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import com.leovp.android.exts.createFile
import com.leovp.android.exts.toast
import com.leovp.audio.AudioPlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityADPCMBinding
import com.leovp.ffmpeg.audio.adpcm.AdpcmImaQtDecoder
import com.leovp.ffmpeg.audio.adpcm.AdpcmImaQtEncoder
import com.leovp.ffmpeg.audio.base.EncodeAudioCallback
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class ADPCMActivity : BaseDemonstrationActivity<ActivityADPCMBinding>(R.layout.activity_a_d_p_c_m) {
    override fun getTagName(): String = ITAG

    companion object {
        private const val OUTPUT_IMA_FILE_NAME = "raw_adpcm_ima_qt.raw"
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_CHANNELS = 2
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityADPCMBinding =
        ActivityADPCMBinding.inflate(layoutInflater)

    private var player: AudioPlayer? = null

    fun onEncodeToADPCMClick(@Suppress("unused") view: View) {
        val inputStream = resources.openRawResource(R.raw.raw_pcm_44100_2ch_s16le)
        val pcmData = inputStream.readBytes()
        inputStream.close()

        val outFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
        thread {
            runCatching {
                BufferedOutputStream(FileOutputStream(outFile)).use { outputStream ->
                    AdpcmImaQtEncoder(AUDIO_SAMPLE_RATE, AUDIO_CHANNELS, 64000).use { encoder ->
                        encoder.encodedCallback = object : EncodeAudioCallback {
                            override fun onEncodedUpdate(encodedAudio: ByteArray) {
                                outputStream.write(encodedAudio)
                            }
                        }
                        val inputFrameBytes = encoder.inputFrameBytes()
                        val alignedPcmData = pcmData.padPcmWithSilence(inputFrameBytes)
                        if (alignedPcmData !== pcmData) {
                            LogContext.log.w(
                                ITAG,
                                "Pad PCM input from ${pcmData.size} to " +
                                    "${alignedPcmData.size} bytes."
                            )
                        }
                        encoder.encode(alignedPcmData)
                    }
                }
            }.onSuccess {
                toast("Encode done!")
            }.onFailure {
                LogContext.log.e(ITAG, "Unable to encode ADPCM audio.", it)
            }
        }
    }

    fun onPlayADPCMClick(@Suppress("unused") view: View) {
        val decoderInfo =
            AudioDecoderInfo(
                AUDIO_SAMPLE_RATE,

                @Suppress("SENSELESS_COMPARISON")
                if (AUDIO_CHANNELS ==
                    2
                ) {
                    AudioFormat.CHANNEL_OUT_STEREO
                } else {
                    AudioFormat.CHANNEL_OUT_MONO
                },
                AudioFormat.ENCODING_PCM_16BIT
            )
        player = AudioPlayer(this, decoderInfo, AudioType.PCM)

        thread {
            runCatching {
                AdpcmImaQtDecoder(decoderInfo.sampleRate, decoderInfo.channelCount).use { decoder ->
                    // val inputStream = resources.openRawResource(R.raw.out_adpcm_44100_2ch_64kbps)
                    val inFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
                    val musicBytes = FileInputStream(inFile).use { it.readBytes() }
                    val chunkSize = decoder.chunkSize()
                    require(musicBytes.size % chunkSize == 0) {
                        "ADPCM file contains an incomplete trailing chunk."
                    }
                    for (i in musicBytes.indices step chunkSize) {
                        val chunk = musicBytes.copyOfRange(i, i + chunkSize)
                        val st = SystemClock.elapsedRealtimeNanos()
                        val pcmBytes = decoder.decode(chunk)
                        LogContext.log.i(
                            ITAG,
                            "PCM[${pcmBytes.size}] " +
                                "cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us"
                        )
                        player?.play(pcmBytes)
                    }
                }
            }.onFailure {
                LogContext.log.e(ITAG, "Unable to decode ADPCM audio.", it)
            }.also {
                player?.release()
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}

internal fun ByteArray.padPcmWithSilence(frameBytes: Int): ByteArray {
    require(frameBytes > 0) { "Frame size must be positive." }
    val remainder = size % frameBytes
    if (remainder == 0) return this
    val paddingBytes = frameBytes - remainder
    require(size <= Int.MAX_VALUE - paddingBytes) { "Padded PCM input is too large." }
    return copyOf(size + paddingBytes)
}
