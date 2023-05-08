package com.leovp.demo.basiccomponents.examples.opus

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.leovp.android.exts.createFile
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.bean.OpusCsd
import com.leovp.audio.mediacodec.utils.AudioCodecUtil
import com.leovp.audio.opus.OpusDecoder
import com.leovp.bytes.readBytes
import com.leovp.bytes.toHexString
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.audio.AudioActivity
import com.leovp.demo.databinding.ActivityOpusBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * https://chenliang.org/2020/03/14/ogg-container-format/
 * https://codezjx.com/posts/opus-mediacodec-decode/
 */
class OpusActivity : BaseDemonstrationActivity<ActivityOpusBinding>() {
    private val startCode = "|leo|"
    private val startCodeSize = startCode.length

    override fun getTagName(): String = ITAG

    private val opusFile by lazy { this.createFile("audio.opus") }
    private var audioTrackPlayer: AudioTrackPlayer? = null
    private var decoder: OpusDecoder? = null

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private lateinit var rf: RandomAccessFile

    private fun getCsd(): OpusCsd? {
        val csdEndPos = findStartCode(5)
        LogContext.log.w(tag, "csd end pos=$csdEndPos")

        val csdBytes = ByteArray((csdEndPos - startCodeSize).toInt())
        try {
            rf.seek(startCodeSize.toLong())
            rf.readFully(csdBytes)
        } catch (e: EOFException) {
            LogContext.log.e(tag, "EOFException", e)
        } catch (ioe: IOException) {
            LogContext.log.e(tag, "EOFException", ioe)
        }

        LogContext.log.e(tag, "csd[${csdBytes.size}]=${csdBytes.toHexString()}")
        return AudioCodecUtil.parseOpusConfigFrame(csdBytes, ByteOrder.LITTLE_ENDIAN)
    }

    private fun isStartCode(bb: ByteArray, offset: Int = 0): Boolean {
        if (offset < 0) {
            return false
        }
        return bb.readBytes(startCodeSize, offset).decodeToString() == startCode
    }

    private fun findStartCode(startPos: Long): Long {
        var curPos = startPos
        val startCodeBytes = ByteArray(startCodeSize)
        while (true) {
            try {
                rf.seek(curPos)
                rf.readFully(startCodeBytes)
                // LogContext.log.i(tag, "startCodeBytes=${startCodeBytes.decodeToString()}")
            } catch (e: EOFException) {
                LogContext.log.e(tag, "EOFException", e)
                break
            } catch (ioe: IOException) {
                LogContext.log.e(tag, "EOFException", ioe)
                break
            }
            if (isStartCode(startCodeBytes)) {
                return curPos
            } else {
                curPos++
            }
        }
        return -1
    }

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityOpusBinding {
        return ActivityOpusBinding.inflate(layoutInflater)
    }

    override fun onResume() {
        super.onResume()

        binding.btnPlay.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val audioDecoderInfo = AudioActivity.audioDecoderInfo
                audioTrackPlayer = AudioTrackPlayer(this@OpusActivity, audioDecoderInfo)
                audioTrackPlayer?.play()

                rf = RandomAccessFile(opusFile, "r")
                LogContext.log.w(tag, "File length=${rf.length()}")
                val opusCsd = getCsd()!!
                LogContext.log.w(tag, "csd0=${opusCsd.csd0.toHexString()}")
                LogContext.log.w(tag, "csd1=${opusCsd.csd1.toHexString()}")
                LogContext.log.w(tag, "csd2=${opusCsd.csd2.toHexString()}")
                decoder = OpusDecoder(
                    audioDecoderInfo.sampleRate, audioDecoderInfo.channelCount,
                    opusCsd.csd0, opusCsd.csd1, opusCsd.csd2,
                    object : IDecodeCallback {
                        override fun onDecoded(pcmData: ByteArray) {
                            audioTrackPlayer?.write(pcmData)
                        }
                    }
                ).apply { start() }
                ioScope.launch {
                    var startCodeBeginPos = findStartCode(startCodeSize.toLong())
                    while (true) {
                        ensureActive()
                        var startCodeEndPos: Long = 0
                        try {
                            startCodeEndPos = findStartCode(startCodeBeginPos + startCodeSize)
                            val audioFrameData = ByteArray((startCodeEndPos - startCodeBeginPos - startCodeSize).toInt())
                            rf.seek(startCodeBeginPos + startCodeSize)
                            rf.readFully(audioFrameData)
                            LogContext.log.e(tag, "audioFrameData[${audioFrameData.size}]=${audioFrameData.toHexString()}")
                            decoder?.decode(audioFrameData)
                        } catch (e: EOFException) {
                            LogContext.log.e(tag, "EOFException", e)
                            break
                        } catch (ioe: IOException) {
                            LogContext.log.e(tag, "EOFException", ioe)
                            break
                        } catch (nase: NegativeArraySizeException) {
                            LogContext.log.e(tag, "NegativeArraySizeException", nase)
                            break
                        }
                        startCodeBeginPos = startCodeEndPos
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        audioTrackPlayer?.release()
        decoder?.release()
        ioScope.cancel()
    }
}
