package com.leovp.audio.adpcm

/**
 * ADPCM encoder/decoder
 *
 * https://github.com/nabto/android-audio-stream-demo/blob/master/app/src/main/java/com/nabto/androidaudiodemo/ADPCM.java
 */
@Deprecated("Use [AdpcmImaQtEncoder] and [AdpcmImaQtDecoder] in adpcm-ima-qt-codec-sdk.")
class ADPCMCodec {
    private var leftStepIndexEnc = 0
    private var rightStepIndexEnc = 0
    private var leftPredictedEnc = 0
    private var rightPredictedEnc = 0
    private var leftStepIndexDec = 0
    private var rightStepIndexDec = 0
    private var leftPredictedDec = 0
    private var rightPredictedDec = 0

    /**
     * Creates a ADPCM encoder/decoder.
     */
    init {
        resetEncoder()
        resetDecoder()
    }

    /**
     * Reset the ADPCM predictor.
     * Call when encoding a new stream.
     */
    fun resetEncoder() {
        rightStepIndexEnc = 0
        leftStepIndexEnc = rightStepIndexEnc
        rightPredictedEnc = 0
        leftPredictedEnc = rightPredictedEnc
    }

    /**
     * Reset the ADPCM predictor.
     * Call when decoding a new stream.
     */
    fun resetDecoder() {
        rightStepIndexDec = 0
        leftStepIndexDec = rightStepIndexDec
        rightPredictedDec = 0
        leftPredictedDec = rightPredictedDec
    }

    /**
     * Encode 16-bit stereo little-endian PCM audio data to 8-bit ADPCM audio data.
     *
     * @param input 16-bit stereo little-endian PCM audio data
     * @return 8-bit ADPCM audio data
     */
    fun encode(input: ShortArray): ByteArray {
        val count = input.size / 2
        val output = ByteArray(count)
        var inputIndex = 0
        var outputIndex = 0
        while (outputIndex < count) {
            val leftSample = input[inputIndex++].toInt()
            val rightSample = input[inputIndex++].toInt()
            val leftStep = stepTable[leftStepIndexEnc].toInt()
            val rightStep = stepTable[rightStepIndexEnc].toInt()
            var leftCode = ((leftSample - leftPredictedEnc) * 4 + leftStep * 8) / leftStep
            var rightCode = ((rightSample - rightPredictedEnc) * 4 + rightStep * 8) / rightStep
            if (leftCode > 15) leftCode = 15
            if (rightCode > 15) rightCode = 15
            if (leftCode < 0) leftCode = 0
            if (rightCode < 0) rightCode = 0
            leftPredictedEnc += (leftCode * leftStep ushr 2) - (15 * leftStep ushr 3)
            rightPredictedEnc += (rightCode * rightStep ushr 2) - (15 * rightStep ushr 3)
            if (leftPredictedEnc > 32767) leftPredictedEnc = 32767
            if (rightPredictedEnc > 32767) rightPredictedEnc = 32767
            if (leftPredictedEnc < -32768) leftPredictedEnc = -32768
            if (rightPredictedEnc < -32768) rightPredictedEnc = -32768
            leftStepIndexEnc += stepIndexTable[leftCode]
            rightStepIndexEnc += stepIndexTable[rightCode]
            if (leftStepIndexEnc > 88) leftStepIndexEnc = 88
            if (rightStepIndexEnc > 88) rightStepIndexEnc = 88
            if (leftStepIndexEnc < 0) leftStepIndexEnc = 0
            if (rightStepIndexEnc < 0) rightStepIndexEnc = 0

            //output[outputIndex++] = (byte) ((leftCode << 4) | rightCode);
            output[outputIndex++] = (rightCode shl 4 or rightCode).toByte()
        }
        return output
    }

    /**
     * Decode 8-bit ADPCM audio data to 16-bit stereo little-endian PCM audio data.
     *
     * @param input 8-bit ADPCM audio data
     * @return 16-bit stereo little-endian PCM audio data
     */
    fun decode(input: ByteArray): ShortArray {
        val count = input.size * 2
        val output = ShortArray(count)
        var inputIndex = 0
        var outputIndex = 0
        while (outputIndex < count) {
            var leftCode: Int = input[inputIndex++].toInt() and 0xFF
            val rightCode = leftCode and 0xF
            leftCode = leftCode ushr 4
            val leftStep = stepTable[leftStepIndexDec].toInt()
            val rightStep = stepTable[rightStepIndexDec].toInt()
            leftPredictedDec += (leftCode * leftStep ushr 2) - (15 * leftStep ushr 3)
            rightPredictedDec += (rightCode * rightStep ushr 2) - (15 * rightStep ushr 3)
            if (leftPredictedDec > 32767) leftPredictedDec = 32767
            if (rightPredictedDec > 32767) rightPredictedDec = 32767
            if (leftPredictedDec < -32768) leftPredictedDec = -32768
            if (rightPredictedDec < -32768) rightPredictedDec = -32768
            output[outputIndex++] = leftPredictedDec.toShort()
            output[outputIndex++] = rightPredictedDec.toShort()
            leftStepIndexDec += stepIndexTable[leftCode]
            rightStepIndexDec += stepIndexTable[rightCode]
            if (leftStepIndexDec > 88) leftStepIndexDec = 88
            if (rightStepIndexDec > 88) rightStepIndexDec = 88
            if (leftStepIndexDec < 0) leftStepIndexDec = 0
            if (rightStepIndexDec < 0) rightStepIndexDec = 0
        }
        return output
    }

    companion object {
        private val stepIndexTable = byteArrayOf(
            8, 6, 4, 2, -1, -1, -1, -1,
            -1, -1, -1, -1, 2, 4, 6, 8

            // https://wiki.multimedia.cx/index.php/Apple_QuickTime_IMA_ADPCM

            // https://wiki.multimedia.cx/index.php/IMA_ADPCM
//            -1, -1, -1, -1, 2, 4, 6, 8,
//            -1, -1, -1, -1, 2, 4, 6, 8
        )
        private val stepTable = shortArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
            19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
            50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
            130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
            876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
            2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
            5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
        )
    }
}