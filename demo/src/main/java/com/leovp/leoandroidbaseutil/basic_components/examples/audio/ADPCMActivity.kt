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
import com.leovp.audio.adpcm.ADPCMCodec
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.ffmpeg_javacpp.audio.adpcm.AdpcmImaQTDecoder
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import kotlin.concurrent.thread

class ADPCMActivity : BaseDemonstrationActivity() {
    companion object {
        private const val OUTPUT_IMA_FILE_NAME = "adpcm_ima_22050_2ch_s16le.ima"
    }

    private val adpcm = ADPCMCodec()
    private val adpcmQT = AdpcmImaQTDecoder(44100, 2)

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
                val pcmBytes: ByteArray = adpcmQT.decode(chunk)?.first ?: fail("Decode ADPCM error!")
//                os.write(pcmBytes)
                player.play(pcmBytes)
            }
//            os.flush()
//            os.close()
            if (LogContext.enableLog) LogContext.log.i("PCM wrote.")
        }
    }
}