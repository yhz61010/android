package com.leovp.leoandroidbaseutil.basic_components.examples.audio

import android.media.AudioFormat
import android.os.Bundle
import android.view.View
import com.leovp.androidbase.exts.android.toast
import com.leovp.androidbase.exts.kotlin.fail
import com.leovp.androidbase.exts.kotlin.toByteArrayLE
import com.leovp.androidbase.exts.kotlin.toShortArrayLE
import com.leovp.androidbase.utils.file.FileUtil
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.audio.AudioPlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.av_get_bytes_per_sample
import org.bytedeco.javacpp.BytePointer
import kotlin.concurrent.thread

class ADPCMActivity : BaseDemonstrationActivity() {
    companion object {
        private const val OUTPUT_IMA_FILE_NAME = "adpcm_ima_22050_2ch_s16le.ima"
    }

    private val adpcm = com.leovp.ffmpeg.ADPCMCodec()
    private val adpcmQT = com.leovp.ffmpeg.AdpcmImaQTDecoder(2, 44100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_a_d_p_c_m)
    }

    fun onEncodeToADPCMClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val inputStream = resources.openRawResource(R.raw.raw_22050_2ch_s16le)
        val pcmData = inputStream.readBytes()
        val adpcmArray = adpcm.encode(pcmData.toShortArrayLE())
        FileUtil.createFile(this, OUTPUT_IMA_FILE_NAME).writeBytes(adpcmArray)
        toast("Encode done!")
    }

    fun onPlayADPCMClick(@Suppress("UNUSED_PARAMETER") view: View) {
//        val inputStream = resources.openRawResource(R.raw.adpcm_22050_2ch_s16le_128kbps)
//        val musicBytes = inputStream.readBytes()
        val musicBytes = FileUtil.createFile(this, OUTPUT_IMA_FILE_NAME).readBytes()
        val shortPcmArray = adpcm.decode(musicBytes)
        val player = AudioPlayer(
            this,
            AudioDecoderInfo(22050, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
            AudioType.PCM
        )
        thread { player.play(shortPcmArray.toByteArrayLE()) }
    }

    fun onPlayRawAdpcmClick(@Suppress("UNUSED_PARAMETER") view: View) {
        val player = AudioPlayer(this, AudioDecoderInfo(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioType.PCM)
//        val adpcmIMAQtPcmFile = FileUtil.createFile(this, "adpcm_ima_qt_44100_2channels_s16le.pcm")
        thread {
//            val os = BufferedOutputStream(FileOutputStream(adpcmIMAQtPcmFile))
            val inputStream = resources.openRawResource(R.raw.adpcm_ima_qt_44100_2channels_s16le)
            val musicBytes = inputStream.readBytes()
            val chunkSize: Int = adpcmQT.chunkSize()
            for (i in musicBytes.indices step chunkSize) {
                val chunk = musicBytes.copyOfRange(i, i + chunkSize)
                val avFrame: AVFrame = adpcmQT.decode(chunk) ?: fail("ADPCM decode error")

//                val bp: BytePointer = avFrame.data(0)
//                val pcmChunkBytes = ByteArray(avFrame.linesize().get())
//                bp.get(pcmChunkBytes)

                val bpLeft: BytePointer = avFrame.extended_data(0)
                val leftChunkBytes = ByteArray(avFrame.linesize(0))
                bpLeft.get(leftChunkBytes)

//                val bpRight: BytePointer = avFrame.extended_data(1)
//                val rightChunkBytes = ByteArray(avFrame.linesize(1))
//                bpRight.get(rightChunkBytes)
//
                val stereoPcmBytes = leftChunkBytes // + rightChunkBytes

//                os.write(pcmChunkBytes)

                if (LogContext.enableLog) LogContext.log.i(
                    "$i: bytes per sample=${av_get_bytes_per_sample(avFrame.format())} chunk[${stereoPcmBytes.size}] ch:${avFrame.channels()} sampleRate:${avFrame.sample_rate()} np_samples:${avFrame.nb_samples()} " +
                            " linesize[0]=${avFrame.linesize(0)} fmt[${avFrame.format()}]:${
                                com.leovp.ffmpeg.AdpcmImaQTDecoder.getSampleFormatName(
                                    avFrame.format()
                                )
                            }"
                )

                player.play(stereoPcmBytes)
            }
//            os.flush()
//            os.close()
            if (LogContext.enableLog) LogContext.log.i("PCM wrote.")
        }
    }
}