package com.leovp.screencapture.screenrecord.base

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.leovp.log.LogContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Writes one encoded video track into an MP4 file from the samples a recorder is already
 * producing, leaving that recorder's own output untouched.
 *
 * An MP4 is not a raw elementary stream with a header in front of it. The start codes have to
 * become length prefixes, the parameter sets have to move out of the stream and into the sample
 * entry, and an index has to be built and written as `moov` when the file is closed.
 * [MediaMuxer] does all of that, which is why this exists at all.
 *
 * Two consequences follow from that last part and are worth stating plainly:
 *
 * - **A file that is never [close]d is unusable.** Without `moov` there is no index, so a process
 *   killed mid-recording leaves nothing playable. A raw stream survives the same kill.
 * - **The timestamps matter now.** A raw stream stores none, so a recorder whose timeline does
 *   not match real time still produced a file that looked right. Here the timeline is written
 *   down, and a wrong one plays back at the wrong speed.
 *
 * Not thread safe. Every call must come from the single thread that delivers the codec callbacks.
 */
internal class Mp4TrackWriter(private val outputFile: File) {

    private var muxer: MediaMuxer? = null
    private var trackIndex = NO_TRACK
    private var samplesWritten = 0L
    private var samplesDroppedBeforeTrack = 0L

    /** Latches on the first failure, so one bad sample does not produce one log line per frame. */
    private var broken = false

    /**
     * Opens the file and adds the track, using the format the codec reports.
     *
     * The format is taken from the codec rather than assembled from the codec-config buffer on
     * purpose: it already carries `csd-0`/`csd-1` split the way each codec needs them. HEVC packs
     * VPS, SPS and PPS into a single `csd-0` while AVC wants SPS in `csd-0` and PPS in `csd-1`,
     * and getting that wrong yields a file that muxes without complaint and plays nowhere.
     */
    fun onOutputFormat(format: MediaFormat) {
        if (broken || muxer != null) return
        try {
            val created = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            trackIndex = created.addTrack(format)
            created.start()
            muxer = created
            LogContext.log.i(TAG, "MP4 track opened. dstFile=${outputFile.absolutePath}")
        } catch (error: Throwable) {
            // Thrown here by an unsupported track format - notably an HEVC track before API 24,
            // which the builder is meant to have already turned into H.264.
            fail("Could not open ${outputFile.absolutePath}", error)
        }
    }

    /**
     * Adds one encoded sample. Codec-config and end-of-stream buffers are skipped rather than
     * written: the parameter sets are already in the sample entry via the track format, and the
     * end-of-stream marker carries no payload.
     */
    fun write(encodedBytes: ByteArray, flags: Int, presentationTimeUs: Long) {
        if (broken) return
        if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        if (encodedBytes.isEmpty()) return
        val target = muxer
        if (target == null) {
            // The codec has not reported its format yet. Counted rather than logged per frame;
            // close() reports the total, because losing the opening frames is worth knowing about.
            samplesDroppedBeforeTrack++
            return
        }
        val info = MediaCodec.BufferInfo().apply {
            set(0, encodedBytes.size, presentationTimeUs, flags and SAMPLE_FLAG_MASK)
        }
        try {
            target.writeSampleData(trackIndex, ByteBuffer.wrap(encodedBytes), info)
            samplesWritten++
        } catch (error: Throwable) {
            fail("Sample ${samplesWritten + 1} rejected", error)
        }
    }

    /**
     * Finishes the file. Must run for the MP4 to be playable, and must not run while [write] can
     * still be called.
     *
     * A file with no samples is deleted instead of left behind: [MediaMuxer.stop] rejects an empty
     * track, so what would remain is a zero-length `.mp4` that only looks like a recording.
     */
    fun close() {
        val target = muxer
        muxer = null
        if (samplesDroppedBeforeTrack > 0) {
            LogContext.log.w(
                TAG,
                "$samplesDroppedBeforeTrack sample(s) arrived before the codec reported its " +
                    "format and are missing from ${outputFile.name}"
            )
        }
        if (target == null) {
            // Either the track never opened or fail() already tore it down. Nothing to finish.
            return
        }
        try {
            if (samplesWritten > 0) {
                target.stop()
                LogContext.log.i(
                    TAG,
                    "MP4 closed with $samplesWritten sample(s). dstFile=${outputFile.absolutePath}"
                )
            } else {
                LogContext.log.w(TAG, "MP4 had no samples; discarding ${outputFile.name}")
            }
        } catch (error: Throwable) {
            LogContext.log.e(TAG, "MP4 stop failed; ${outputFile.name} is incomplete", error)
        } finally {
            runCatching { target.release() }
                .onFailure { LogContext.log.e(TAG, "MP4 release failed", it) }
            if (samplesWritten == 0L) discardOutputFile()
        }
    }

    /**
     * Gives up on this file for good and removes it. Partial MP4 output is worse than none: it
     * has no `moov`, so it is not playable, but it still looks like a finished recording.
     */
    private fun fail(message: String, error: Throwable) {
        broken = true
        LogContext.log.e(TAG, "MP4 writing stopped. $message", error)
        val target = muxer
        muxer = null
        if (target != null) {
            runCatching { target.release() }
                .onFailure { LogContext.log.e(TAG, "MP4 release failed after $message", it) }
        }
        discardOutputFile()
    }

    private fun discardOutputFile() {
        runCatching { if (outputFile.exists()) outputFile.delete() }
            .onFailure { LogContext.log.e(TAG, "Could not delete ${outputFile.name}", it) }
    }

    companion object {
        private const val TAG = "Mp4Writer"
        private const val NO_TRACK = -1

        /**
         * Everything but the two flags that describe a buffer's role rather than its content.
         * Passing those through would tell the muxer a sample is a parameter set or a stream
         * terminator.
         */
        private const val SAMPLE_FLAG_MASK =
            (MediaCodec.BUFFER_FLAG_CODEC_CONFIG or MediaCodec.BUFFER_FLAG_END_OF_STREAM).inv()
    }
}
