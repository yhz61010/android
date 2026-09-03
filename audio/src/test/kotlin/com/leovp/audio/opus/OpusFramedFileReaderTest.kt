package com.leovp.audio.opus

import java.io.File
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OpusFramedFileReaderTest {
    @Test
    fun `last payload is read through natural end of file`() {
        val startCode = "|leo|".encodeToByteArray()
        val config = byteArrayOf(1, 2)
        val firstFrame = byteArrayOf(3, 4, 5)
        val lastFrame = byteArrayOf(6, 7, 8, 9)
        withTemporaryFile(startCode + config + startCode + firstFrame + startCode + lastFrame) {
            val reader = OpusFramedFileReader(it, startCode)

            val configPayload = reader.readPayload(0)
            assertContentEquals(config, configPayload.data)

            val firstPayload = reader.readPayload(
                requireNotNull(configPayload.nextStartCodePosition)
            )
            assertContentEquals(firstFrame, firstPayload.data)

            val lastPayload = reader.readPayload(
                requireNotNull(firstPayload.nextStartCodePosition)
            )
            assertContentEquals(lastFrame, lastPayload.data)
            assertNull(lastPayload.nextStartCodePosition)
        }
    }

    @Test
    fun `missing start code is rejected`() {
        withTemporaryFile(byteArrayOf(1, 2, 3)) {
            val reader = OpusFramedFileReader(it, "|leo|".encodeToByteArray())

            val error = assertFailsWith<IllegalArgumentException> { reader.readPayload(0) }
            assertEquals("No start code at position 0", error.message)
        }
    }

    @Test
    fun `empty file is rejected`() {
        withTemporaryFile(byteArrayOf()) {
            val reader = OpusFramedFileReader(it, "|leo|".encodeToByteArray())

            assertFailsWith<IllegalArgumentException> { reader.readPayload(0) }
        }
    }

    @Test
    fun `trailing start code produces an empty final payload`() {
        val startCode = "|leo|".encodeToByteArray()
        withTemporaryFile(startCode + byteArrayOf(1, 2) + startCode) {
            val reader = OpusFramedFileReader(it, startCode)

            val first = reader.readPayload(0)
            val last = reader.readPayload(requireNotNull(first.nextStartCodePosition))

            assertContentEquals(byteArrayOf(), last.data)
            assertNull(last.nextStartCodePosition)
        }
    }

    @Test
    fun `partial start code at end remains payload data`() {
        val startCode = "|leo|".encodeToByteArray()
        val partialStartCode = "|le".encodeToByteArray()
        withTemporaryFile(startCode + byteArrayOf(1, 2) + partialStartCode) {
            val reader = OpusFramedFileReader(it, startCode)

            val payload = reader.readPayload(0)

            assertContentEquals(byteArrayOf(1, 2) + partialStartCode, payload.data)
            assertNull(payload.nextStartCodePosition)
        }
    }

    private fun withTemporaryFile(data: ByteArray, block: (RandomAccessFile) -> Unit) {
        val file = File.createTempFile("opus-reader", ".bin")
        try {
            RandomAccessFile(file, "rw").use {
                it.write(data)
                block(it)
            }
        } finally {
            file.delete()
        }
    }
}
