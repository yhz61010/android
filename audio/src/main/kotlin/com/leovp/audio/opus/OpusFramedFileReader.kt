package com.leovp.audio.opus

import java.io.RandomAccessFile

internal class OpusFilePayload(val data: ByteArray, val nextStartCodePosition: Long?)

/** Reads payloads separated by the legacy OPUS demo start code. */
internal class OpusFramedFileReader(private val file: RandomAccessFile, startCode: ByteArray) {
    companion object {
        private const val SCAN_BUFFER_SIZE = 8 * 1024
    }

    private val startCode = startCode.copyOf()
    private val prefixTable = buildPrefixTable(this.startCode)

    init {
        require(this.startCode.isNotEmpty()) { "Start code must not be empty" }
    }

    fun readPayload(startCodePosition: Long): OpusFilePayload {
        require(hasStartCodeAt(startCodePosition)) {
            "No start code at position $startCodePosition"
        }
        val payloadStart = startCodePosition + startCode.size
        val nextStartCodePosition = findStartCode(payloadStart)
        val payloadEnd = nextStartCodePosition ?: file.length()
        val payloadSize = payloadEnd - payloadStart
        require(payloadSize in 0..Int.MAX_VALUE.toLong()) {
            "Invalid OPUS payload size: $payloadSize"
        }

        return OpusFilePayload(
            data = ByteArray(payloadSize.toInt()).also {
                file.seek(payloadStart)
                file.readFully(it)
            },
            nextStartCodePosition = nextStartCodePosition
        )
    }

    private fun findStartCode(startPosition: Long): Long? {
        if (startPosition > file.length() - startCode.size) return null
        file.seek(startPosition)
        val scanBuffer = ByteArray(SCAN_BUFFER_SIZE)
        var absolutePosition = startPosition
        var matchedBytes = 0
        while (true) {
            val readSize = file.read(scanBuffer)
            if (readSize < 0) return null
            for (index in 0 until readSize) {
                val currentByte = scanBuffer[index]
                while (matchedBytes > 0 && currentByte != startCode[matchedBytes]) {
                    matchedBytes = prefixTable[matchedBytes - 1]
                }
                if (currentByte == startCode[matchedBytes]) matchedBytes++
                if (matchedBytes == startCode.size) {
                    return absolutePosition + index - startCode.size + 1
                }
            }
            absolutePosition += readSize
        }
    }

    private fun hasStartCodeAt(position: Long): Boolean {
        if (position < 0 || position > file.length() - startCode.size) return false
        val candidate = ByteArray(startCode.size)
        file.seek(position)
        file.readFully(candidate)
        return candidate.contentEquals(startCode)
    }

    private fun buildPrefixTable(pattern: ByteArray): IntArray {
        val prefix = IntArray(pattern.size)
        var matchedBytes = 0
        for (index in 1 until pattern.size) {
            while (matchedBytes > 0 && pattern[index] != pattern[matchedBytes]) {
                matchedBytes = prefix[matchedBytes - 1]
            }
            if (pattern[index] == pattern[matchedBytes]) matchedBytes++
            prefix[index] = matchedBytes
        }
        return prefix
    }
}
