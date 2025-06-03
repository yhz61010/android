@file:Suppress("unused")

package com.leovp.audio.mediacodec

import android.media.AudioFormat
import android.media.MediaCodec
import com.leovp.log.LogContext
import java.nio.ByteBuffer
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2023/5/4 10:18
 */
abstract class BaseMediaCodecSynchronous(
    codecName: String,
    sampleRate: Int,
    channelCount: Int,
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    isEncoding: Boolean = false,
) :
    BaseMediaCodec(codecName, sampleRate, channelCount, audioFormat, isEncoding) {
    companion object {
        private const val TAG = "MediaCodecSync"
    }

    override fun start() {
        super.start()
        ioScope.launch {
            do {
                ensureActive()
            } while (process())
            onEndOfStream()
        }
    }

    private fun process(): Boolean {
        var isFinish = false
        try {
            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex: Int = codec.dequeueInputBuffer(0)
            if (inputIndex > -1) {
                val inputBuf = codec.getInputBuffer(inputIndex) ?: return true
                // Clear exist data.
                inputBuf.clear()
                // Fill inputBuffer with valid data.
                val size = onInputData(inputBuf)
                // LogContext.log.d(TAG, "    -> inputBuf size=${inputBuf.remaining()}")
                val pts = computePresentationTimeUs()
                if (pts < 0) {
                    isFinish = true
                    codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    codec.queueInputBuffer(inputIndex, 0, size, pts, 0)
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var buffer: ByteBuffer?
            val st = System.currentTimeMillis()
            // Start decoding and get output index
            var outputIndex: Int = codec.dequeueOutputBuffer(bufferInfo, 0)
            // LogContext.log.d(TAG, "outputIndex=$outputIndex")
            while (outputIndex > -1) {
                buffer = codec.getOutputBuffer(outputIndex)
                if (buffer == null) continue
                when {
                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 ->
                        onOutputData(buffer, bufferInfo, isConfig = true, isKeyFrame = false)

                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ->
                        onOutputData(buffer, bufferInfo, isConfig = false, isKeyFrame = true)

                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 -> {
                        LogContext.log.w(TAG, "onEndOfStream()")
                        onEndOfStream()
                    }

                    else -> onOutputData(buffer, bufferInfo, isConfig = false, isKeyFrame = false)
                }

                // Must clear decoded data before next loop. Otherwise, you will get the same data while looping.
                codec.releaseOutputBuffer(outputIndex, false)
                // Get data again.
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            LogContext.log.d(TAG, "Decode cost: ${System.currentTimeMillis() - st}ms")
        } catch (e: Exception) {
            LogContext.log.e(TAG, "You can ignore this message safely. decodeAndPlay error")
        }
        return !isFinish
    }
}
