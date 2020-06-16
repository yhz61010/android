package com.ho1ho.androidbase.utils.media

import java.io.*

/**
 * Author: Michael Leo
 * Date: 20-6-1 下午1:41
 */
@Suppress("unused")
object PcmToWavUtil {
    /**
     * Size of buffer used for transfer, by default
     */
    private const val TRANSFER_BUFFER_SIZE = 10 * 1024

    /**
     * @param pcmData      Raw PCM data
     * @param numChannels  Channel count. mono = 1, stereo = 2
     * @param sampleRate   Sample rate
     * @param bitPerSample Bits per sample. Example: 8bits, 16bits
     * @return Wave data
     */
    @Suppress("unused")
    fun pcmToWav(pcmData: ByteArray, numChannels: Int, sampleRate: Int, bitPerSample: Int): ByteArray {
        val wavData = ByteArray(pcmData.size + 44)
        val header =
            wavHeader(pcmData.size, numChannels, sampleRate, bitPerSample)
        System.arraycopy(header, 0, wavData, 0, header.size)
        System.arraycopy(pcmData, 0, wavData, header.size, pcmData.size)
        return wavData
    }
    // ====================================================

    /**
     * @param pcmLen       The length of PCM
     * @param numChannels  Channel count. mono = 1, stereo = 2
     * @param sampleRate   Sample rate
     * @param bitPerSample Bits per sample. Example: 8bits, 16bits
     * @return Wave header
     */
    @Suppress("unused")
    fun wavHeader(pcmLen: Int, numChannels: Int, sampleRate: Int, bitPerSample: Int): ByteArray {
        val header = ByteArray(44)
        // ChunkID, RIFF, 4 bytes respectively.
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        // ChunkSize, pcmLen + 36, 4 bytes respectively.
        val chunkSize = pcmLen + 36.toLong()
        header[4] = (chunkSize and 0xff).toByte()
        header[5] = (chunkSize shr 8 and 0xff).toByte()
        header[6] = (chunkSize shr 16 and 0xff).toByte()
        header[7] = (chunkSize shr 24 and 0xff).toByte()
        // Format, WAVE, 4 bytes respectively
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        // Subchunk1 ID, 'fmt ', 4 bytes
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        // Subchunk1 Size, 16, 4 bytes
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        // AudioFormat, pcm = 1, 2bytes
        header[20] = 1
        header[21] = 0
        // NumChannels, mono = 1, stereo = 2, 2 bytes
        header[22] = numChannels.toByte()
        header[23] = 0
        // SampleRate, 4 bytes
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        // ByteRate = SampleRate * NumChannels * BitsPerSample / 8, 4 bytes
        val byteRate = sampleRate * numChannels * bitPerSample / 8.toLong()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        // BlockAlign = NumChannels * BitsPerSample / 8, 2 bytes
        header[32] = (numChannels * bitPerSample / 8).toByte()
        header[33] = 0
        // BitsPerSample, 2 bytes
        header[34] = bitPerSample.toByte()
        header[35] = 0
        // Subhunk2ID, data, 4 bytes
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        // Subchunk2Size, 4 bytes
        header[40] = (pcmLen and 0xff).toByte()
        header[41] = (pcmLen shr 8 and 0xff).toByte()
        header[42] = (pcmLen shr 16 and 0xff).toByte()
        header[43] = (pcmLen shr 24 and 0xff).toByte()
        return header
    }

    /**
     * @param srcPcmFile    raw PCM data limit of file size for wave file: < 2^(2*4) - 36 bytes (~4GB)
     * @param dstWavFile    file to encode to in wav format
     * @param channelCount  number of channels: 1 for mono, 2 for stereo, etc.
     * @param sampleRate    sample rate of PCM audio
     * @param bitsPerSample bits per sample, i.e. 16 for PCM16
     * @throws IOException in event of an error between srcPcmFile/dstWavFile files
     *
     * @see [Wave Format](http://soundfile.sapp.org/doc/WaveFormat/)
     * @see [PCM Wav](https://www.jianshu.com/p/5766e2a7a12a)
     */
    @Suppress("unused")
    @Throws(IOException::class)
    fun pcmToWav(srcPcmFile: File, dstWavFile: File, channelCount: Int, sampleRate: Int, bitsPerSample: Int) {
        val inputSize = srcPcmFile.length().toInt()
        FileOutputStream(dstWavFile).use { encoded ->
            // WAVE RIFF header
            writeToOutput(encoded, "RIFF") // chunk id
            writeToOutput(encoded, 36 + inputSize) // chunk size
            writeToOutput(encoded, "WAVE") // format

            // SUB CHUNK 1 (FORMAT)
            writeToOutput(encoded, "fmt ") // subchunk 1 id
            writeToOutput(encoded, 16) // subchunk 1 size
            writeToOutput(encoded, 1.toShort()) // audio format (1 = PCM)
            writeToOutput(
                encoded,
                channelCount.toShort()
            ) // number of channelCount
            writeToOutput(encoded, sampleRate) // sample rate
            writeToOutput(
                encoded,
                sampleRate * channelCount * bitsPerSample / 8
            ) // byte rate
            writeToOutput(
                encoded,
                (channelCount * bitsPerSample / 8).toShort()
            ) // block align
            writeToOutput(
                encoded,
                bitsPerSample.toShort()
            ) // bits per sample

            // SUB CHUNK 2 (AUDIO DATA)
            writeToOutput(encoded, "data") // subchunk 2 id
            writeToOutput(encoded, inputSize) // subchunk 2 size
            copy(FileInputStream(srcPcmFile), encoded)
        }
    }

    /**
     * Writes string in big endian form to an output stream
     *
     * @param output stream
     * @param data   string
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun writeToOutput(output: OutputStream, data: String) {
        for (element in data) output.write(element.toInt())
    }

    @Throws(IOException::class)
    private fun writeToOutput(output: OutputStream, data: Int) {
        output.write(data and 0xff)
        output.write(data shr 8 and 0xff)
        output.write(data shr 16 and 0xff)
        output.write(data shr 24 and 0xff)
    }

    @Throws(IOException::class)
    private fun writeToOutput(output: OutputStream, data: Short) {
        output.write(data.toInt() and 0xFF)
        output.write(data.toInt() and 0xFF00 ushr 8)
    }

    @Throws(IOException::class)
    private fun copy(source: InputStream, output: OutputStream, bufferSize: Int = TRANSFER_BUFFER_SIZE) =
        source.copyTo(output, bufferSize)
}