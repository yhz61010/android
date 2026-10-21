package com.leovp.screencapture.screenrecord.base

import android.media.MediaCodec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

class Mp4TrackWriterTest {
    private val files = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        files.forEach(File::delete)
    }

    @Test
    fun `addTrack failure releases muxer and deletes output`() {
        val muxer = mockk<Mp4Muxer>(relaxed = true)
        val output = outputFile()
        every { muxer.addTrack(any()) } throws IllegalStateException("bad format")
        val writer = Mp4TrackWriter(output) { muxer }

        writer.onOutputFormat(mockk(relaxed = true))

        verify(exactly = 1) { muxer.release() }
        assertFalse(output.exists())
    }

    @Test
    fun `stop failure releases muxer and deletes incomplete output`() {
        val muxer = mockk<Mp4Muxer>(relaxed = true)
        val output = outputFile()
        every { muxer.addTrack(any()) } returns 0
        every { muxer.stop() } throws IllegalStateException("missing moov")
        val writer = Mp4TrackWriter(output) { muxer }
        writer.onOutputFormat(mockk(relaxed = true))
        writer.write(byteArrayOf(1), MediaCodec.BUFFER_FLAG_KEY_FRAME, 1L)

        writer.close()

        verify(exactly = 1) { muxer.release() }
        assertFalse(output.exists())
    }

    @Test
    fun `fatal start failure remains isolated after cleanup`() {
        val muxer = mockk<Mp4Muxer>(relaxed = true)
        val output = outputFile()
        every { muxer.addTrack(any()) } returns 0
        every { muxer.start() } throws AssertionError("fatal")
        val writer = Mp4TrackWriter(output) { muxer }

        writer.onOutputFormat(mockk(relaxed = true))

        verify(exactly = 1) { muxer.release() }
        assertFalse(output.exists())
    }

    private fun outputFile(): File = File.createTempFile("mp4-writer-", ".mp4").also {
        files += it
    }
}
