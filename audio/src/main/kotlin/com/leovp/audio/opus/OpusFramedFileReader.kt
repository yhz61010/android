package com.leovp.audio.opus

import java.io.RandomAccessFile

internal data class OpusFilePayload(val data: ByteArray, val nextStartCodePosition: Long?)

/** Reads payloads separated by the legacy OPUS demo start code. */
internal class OpusFramedFileReader(private val file: RandomAccessFile, startCode: ByteArray) {
    private val startCode = startCode.copyOf()

    init {
        require(this.startCode.isNotEmpty()) { "Start code must not be empty" }
    }

    fun readPayload(startCodePosition: Long): OpusFilePayload {
        require(hasStartCodeAt(startCodePosition)) {
            "No start code at position $startCodePosition"
        }
        require(startCodePosition <= Long.MAX_VALUE - startCode.size) {
            "OPUS payload position overflow"
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
        val lastPossiblePosition = file.length() - startCode.size
        var currentPosition = startPosition
        val candidate = ByteArray(startCode.size)
        while (currentPosition <= lastPossiblePosition) {
            file.seek(currentPosition)
            file.readFully(candidate)
            if (candidate.contentEquals(startCode)) return currentPosition
            currentPosition++
        }
        return null
    }

    private fun hasStartCodeAt(position: Long): Boolean {
        if (position < 0 || position > file.length() - startCode.size) return false
        val candidate = ByteArray(startCode.size)
        file.seek(position)
        file.readFully(candidate)
        return candidate.contentEquals(startCode)
    }
}
