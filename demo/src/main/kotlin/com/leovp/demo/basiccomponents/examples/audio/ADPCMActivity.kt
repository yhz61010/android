package com.leovp.demo.basiccomponents.examples.audio

import android.media.AudioFormat
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ADPCMActivity : BaseDemonstrationActivity<ActivityADPCMBinding>(R.layout.activity_a_d_p_c_m) {
    override fun getTagName(): String = ITAG

    companion object {
        private const val OUTPUT_IMA_FILE_NAME = "raw_adpcm_ima_qt.raw"
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_CHANNELS = 2
        private const val BIT_RATE = 64000
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityADPCMBinding =
        ActivityADPCMBinding.inflate(layoutInflater)

    private var playbackJob: Job? = null

    fun onEncodeToADPCMClick(@Suppress("unused") view: View) {
        val inputStream = resources.openRawResource(R.raw.raw_pcm_44100_2ch_s16le)
        val pcmData = inputStream.readBytes()
        inputStream.close()

        val outFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
        thread {
            runCatching {
                BufferedOutputStream(FileOutputStream(outFile)).use { outputStream ->
                    AdpcmImaQtEncoder(AUDIO_SAMPLE_RATE, AUDIO_CHANNELS, BIT_RATE).use { encoder ->
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
                if (AUDIO_CHANNELS == 2) {
                    AudioFormat.CHANNEL_OUT_STEREO
                } else {
                    AudioFormat.CHANNEL_OUT_MONO
                },
                AudioFormat.ENCODING_PCM_16BIT
            )

        // cancel() does not wait, so capture the old job and join it inside the new one.
        val previousJob = playbackJob?.also { it.cancel() }
        playbackJob = lifecycleScope.launch(Dispatchers.IO) {
            // Join the previous job before building a new player: the old job's finally still has
            // a NonCancellable releaseAndJoin() to run, and without this wait rapid clicks stack
            // several live AudioTracks. Joining here, not on the click thread, keeps the UI
            // thread free. The join itself is NonCancellable so that a third click, which cancels
            // this job while it waits, cannot let the chain skip a link: this job still outlives
            // the previous one, and the next job waits on this one. ensureActive() then honours
            // the cancellation before anything is built.
            withContext(NonCancellable) { previousJob?.join() }
            ensureActive()
            // Inside the job body, never outside it: a job cancelled before its first dispatch
            // never reaches this finally, so a player built on the click thread would leak.
            val currentPlayer = AudioPlayer(this@ADPCMActivity, decoderInfo, AudioType.PCM)
            try {
                try {
                    AdpcmImaQtDecoder(decoderInfo.sampleRate, decoderInfo.channelCount)
                        .use { decoder ->
                            // val inputStream =
                            //     resources.openRawResource(R.raw.out_adpcm_44100_2ch_64kbps)
                            val inFile = createFile(OUTPUT_IMA_FILE_NAME).absolutePath
                            val musicBytes = FileInputStream(inFile).use { it.readBytes() }
                            val chunkSize = decoder.chunkSize()
                            require(musicBytes.size % chunkSize == 0) {
                                "ADPCM file contains an incomplete trailing chunk."
                            }
                            for (i in musicBytes.indices step chunkSize) {
                                ensureActive()
                                val chunk = musicBytes.copyOfRange(i, i + chunkSize)
                                val st = SystemClock.elapsedRealtimeNanos()
                                val pcmBytes = decoder.decode(chunk)
                                LogContext.log.i(
                                    ITAG,
                                    "PCM[${pcmBytes.size}] " +
                                        "cost=${(SystemClock.elapsedRealtimeNanos() - st) / 1000}us"
                                )
                                currentPlayer.play(pcmBytes)
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogContext.log.e(ITAG, "Unable to decode ADPCM audio.", e)
                }
            } finally {
                withContext(NonCancellable) { currentPlayer.releaseAndJoin() }
            }
        }
    }

    override fun onDestroy() {
        // Cancel only. The playback job owns its player and releases it in its own finally, so
        // releasing here would race the loop that may still be calling play().
        playbackJob?.cancel()
        playbackJob = null
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
