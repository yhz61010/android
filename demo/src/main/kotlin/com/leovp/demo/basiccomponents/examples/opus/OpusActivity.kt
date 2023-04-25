package com.leovp.demo.basiccomponents.examples.opus

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import com.leovp.audio.AudioPlayer
import com.leovp.audio.base.AudioType
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityOpusBinding
import com.leovp.log.base.ITAG
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * https://chenliang.org/2020/03/14/ogg-container-format/
 * https://codezjx.com/posts/opus-mediacodec-decode/
 */
class OpusActivity : BaseDemonstrationActivity<ActivityOpusBinding>() {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityOpusBinding {
        return ActivityOpusBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.btnPlay.setOnClickListener {
            MainScope().launch(Dispatchers.IO) {
                val opusFile = File("/sdcard/opus.opus")
                val opusBytes = opusFile.readBytes()

                val sampleRate = 48000
                val channelCount = 2
                val bitRate = 64_000

                val decoderInfo = AudioDecoderInfo(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                val player = AudioPlayer(this@OpusActivity, decoderInfo, AudioType.PCM)

                val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channelCount)
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                val csd0bytes = opusBytes.copyOfRange(0, 19)
                // val csd0bytes = byteArrayOf(
                //     // OpusHead
                //     0x4f, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64,
                //     // Version
                //     0x01,
                //     // Channel Count
                //     0x02,
                //     // Pre skip
                //     0x00, 0x00,
                //     // Input Sample Rate (Hz), 48000
                //     0x80.toByte(), 0xbb.toByte(), 0x00, 0x00,
                //     // Output Gain (Q7.8 in dB)
                //     0x00, 0x00,
                //     // Mapping Family
                //     0x00)
                val csd1bytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                val csd2bytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                val csd0 = ByteBuffer.wrap(csd0bytes)
                mediaFormat.setByteBuffer("csd-0", csd0)
                val csd1 = ByteBuffer.wrap(csd1bytes)
                mediaFormat.setByteBuffer("csd-1", csd1)
                val csd2 = ByteBuffer.wrap(csd2bytes)
                mediaFormat.setByteBuffer("csd-2", csd2)

                val mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
                mediaCodec.configure(mediaFormat, null, null, 0)
                mediaCodec.start()

                var eof = false
                val bufferInfo = MediaCodec.BufferInfo()
                while (!eof) {
                    val inputBufferId = mediaCodec.dequeueInputBuffer(10_000)
                    if (inputBufferId >= 0) {
                        val inputBuffer: ByteBuffer = mediaCodec.getInputBuffer(inputBufferId)!!
                        inputBuffer.clear()
                        val data: ByteArray = opusBytes.copyOfRange(19, opusBytes.size)
                        inputBuffer.put(data, 0, data.size)
                        mediaCodec.queueInputBuffer(inputBufferId, 0, data.size, 0, 0)
                    }
                    val outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                    eof = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (outputBufferId >= 0) {
                        val outputBuffer: ByteBuffer = mediaCodec.getOutputBuffer(outputBufferId)!!
                        val pcm = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcm)
                        outputBuffer.clear()
                        player.play(pcm)
                        mediaCodec.releaseOutputBuffer(outputBufferId, false)
                    }
                } // end of while
                mediaCodec.stop()
                mediaCodec.release()
            }
        }
    }
}
