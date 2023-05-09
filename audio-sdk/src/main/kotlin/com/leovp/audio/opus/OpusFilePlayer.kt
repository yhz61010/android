package com.leovp.audio.opus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioTrack
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.base.iters.IDecodeCallback
import com.leovp.audio.mediacodec.bean.OpusCsd
import com.leovp.audio.mediacodec.utils.AudioCodecUtil
import com.leovp.bytes.readBytes
import com.leovp.bytes.toHexString
import com.leovp.log.LogContext
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2023/5/5 16:03
 */
class OpusFilePlayer(
    ctx: Context,
    private val audioDecoderInfo: AudioDecoderInfo,
    // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    usage: Int = AudioAttributes.USAGE_MEDIA,
    // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
) {
    companion object {
        private const val TAG = "OpusFilePlayer"
        const val startCode = "|leo|"
    }

    private val queue = ArrayBlockingQueue<ByteArray>(64)

    private val startCodeSize = startCode.length
    private val ioScope = CoroutineScope(Dispatchers.IO + CoroutineName("opus-file-player"))

    private val audioTrackPlayer: AudioTrackPlayer = AudioTrackPlayer(ctx, audioDecoderInfo, usage = usage, contentType = contentType)
    private var decoder: OpusDecoder? = null

    // private var cb: (() -> Unit)? = null

    private lateinit var rf: RandomAccessFile

    fun playOpus(opusFile: File, endCallback: () -> Unit) {
        // cb = endCallback
        audioTrackPlayer.play()
        rf = RandomAccessFile(opusFile, "r")
        LogContext.log.w(TAG, "File length=${rf.length()}")
        val opusCsd = getCsd()!!
        LogContext.log.w(TAG, "csd0=${opusCsd.csd0.toHexString()}")
        LogContext.log.w(TAG, "csd1=${opusCsd.csd1.toHexString()}")
        LogContext.log.w(TAG, "csd2=${opusCsd.csd2.toHexString()}")
        decoder = OpusDecoder(
            audioDecoderInfo.sampleRate, audioDecoderInfo.channelCount,
            opusCsd.csd0, opusCsd.csd1, opusCsd.csd2,
            object : IDecodeCallback {
                override fun onDecoded(pcmData: ByteArray) {
                    queue.put(pcmData)
                    // LogContext.log.i(TAG, "onDecoded -> queue[${queue.size}] pcm[${pcmData.size}]")
                }
            }
        ).apply { start() }

        var isDecodeDone = false
        ioScope.launch {
            var startCodeBeginPos = findStartCode(startCodeSize.toLong())
            var frame: Long = 0
            val frameSize: Int = audioDecoderInfo.sampleRate / 1000 * 20 // 20ms
            val baseDelay = 16L
            var calcDelay = baseDelay
            isDecodeDone = false
            while (true) {
                ensureActive()
                var startCodeEndPos: Long
                try {
                    startCodeEndPos = findStartCode(startCodeBeginPos + startCodeSize)
                    if (startCodeEndPos < 0) {
                        LogContext.log.w(TAG, "Can't find start code.")
                        break
                    }
                    // LogContext.log.w(tag, "startCodeBeginPos=$startCodeBeginPos  startCodeEndPos=$startCodeEndPos")
                    val audioFrameData = ByteArray((startCodeEndPos - startCodeBeginPos - startCodeSize).toInt())
                    rf.seek(startCodeBeginPos + startCodeSize)
                    rf.readFully(audioFrameData)
                    // LogContext.log.d(tag, "audioFrameData[${audioFrameData.size}]=${audioFrameData.toHexString()}")
                    decoder?.decode(audioFrameData)
                    val delayMs = if (calcDelay > 20) 20 else calcDelay
                    if (delayMs < 20) {
                        calcDelay = baseDelay + (++frame / frameSize)
                    }
                    // LogContext.log.e(TAG, "-----> Delay=$delayMs")
                    delay(delayMs)
                } catch (e: EOFException) {
                    LogContext.log.e(TAG, "EOFException", e)
                    break
                } catch (ioe: IOException) {
                    LogContext.log.e(TAG, "IOException", ioe)
                    break
                } catch (nase: NegativeArraySizeException) {
                    LogContext.log.e(TAG, "NegativeArraySizeException", nase)
                    break
                }
                startCodeBeginPos = startCodeEndPos
            } // end while
            isDecodeDone = true
        }

        ioScope.launch {
            while (true) {
                ensureActive()
                val pcmData: ByteArray = queue.take()
                // LogContext.log.w(TAG, "play -> queue[${queue.size}] pcm=${pcmData.size}")
                audioTrackPlayer.write(pcmData)
            }
        }

        ioScope.launch {
            while (true) {
                if (isDecodeDone && queue.isEmpty()) {
                    break
                }
                delay(100)
            }
            endCallback.invoke()
        }
    }

    fun stop() {
        if (audioTrackPlayer.state == AudioTrack.STATE_UNINITIALIZED) {
            return
        }
        audioTrackPlayer.release()
        decoder?.release()
        ioScope.cancel()
        queue.clear()
    }

    private fun getCsd(): OpusCsd? {
        val csdEndPos = findStartCode(5)
        // LogContext.log.w(tag, "csd end pos=$csdEndPos")

        val csdBytes = ByteArray((csdEndPos - startCodeSize).toInt())
        try {
            rf.seek(startCodeSize.toLong())
            rf.readFully(csdBytes)
        } catch (e: EOFException) {
            LogContext.log.e(TAG, "EOFException", e)
        } catch (ioe: IOException) {
            LogContext.log.e(TAG, "EOFException", ioe)
        }

        LogContext.log.w(TAG, "csd[${csdBytes.size}]=${csdBytes.toHexString()}")
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
                LogContext.log.w(TAG, "Read file. EOF")
                break
            } catch (ioe: IOException) {
                LogContext.log.e(TAG, "IOException", ioe)
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
}
