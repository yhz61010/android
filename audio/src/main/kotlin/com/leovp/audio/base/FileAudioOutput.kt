package com.leovp.audio.base

import android.content.Context
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo

/** Minimal file-player boundary around the Android AudioTrack implementation. */
internal interface FileAudioOutput {
    val playbackHeadPosition: Int

    fun play()

    fun write(pcmBytes: ByteArray): Int

    fun stop()

    fun release()
}

internal class AndroidFileAudioOutput(
    context: Context,
    decoderInfo: AudioDecoderInfo,
    usage: Int,
    contentType: Int,
) : FileAudioOutput {
    private val delegate = AudioTrackPlayer(
        context,
        decoderInfo,
        usage = usage,
        contentType = contentType
    )

    override val playbackHeadPosition: Int get() = delegate.playbackHeadPosition

    override fun play() = delegate.play()

    override fun write(pcmBytes: ByteArray): Int = delegate.write(pcmBytes)

    override fun stop() = delegate.stop()

    override fun release() = delegate.release()
}
