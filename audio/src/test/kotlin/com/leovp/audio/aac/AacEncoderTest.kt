package com.leovp.audio.aac

import com.leovp.audio.mediacodec.iter.IAudioMediaCodec.Companion.AAC_PROFILE_LC
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import org.junit.Test

class AacEncoderTest {
    @Test
    fun `builds csd0 for aac lc 16 khz mono`() {
        val csd0 = AacEncoder.getAudioEncodingCsd0(AAC_PROFILE_LC, 16_000, 1)

        assertContentEquals(byteArrayOf(0x14, 0x08), csd0)
    }

    @Test
    fun `builds csd0 for aac lc 44 point 1 khz stereo`() {
        val csd0 = AacEncoder.getAudioEncodingCsd0(AAC_PROFILE_LC, 44_100, 2)

        assertContentEquals(byteArrayOf(0x12, 0x10), csd0)
    }

    @Test
    fun `returns null for unsupported sample rate`() {
        assertNull(AacEncoder.getAudioEncodingCsd0(AAC_PROFILE_LC, 12_345, 1))
    }
}
