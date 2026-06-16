package com.leovp.basenetty

import com.leovp.basenetty.framework.base.decoder.CustomSocketByteStreamDecoder
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test

class CustomSocketByteStreamDecoderTest {

    private fun createChannel() = EmbeddedChannel(CustomSocketByteStreamDecoder())

    private fun writeFrame(channel: EmbeddedChannel, payload: ByteArray): Boolean {
        val buf = Unpooled.buffer(Int.SIZE_BYTES + payload.size)
        buf.writeIntLE(payload.size)
        buf.writeBytes(payload)
        return channel.writeInbound(buf)
    }

    @Test
    fun `empty buffer produces no output`() {
        val ch = createChannel()
        ch.writeInbound(Unpooled.EMPTY_BUFFER)
        ch.readInbound<Any>().shouldBeNull()
    }

    @Test
    fun `buffer smaller than header produces no output`() {
        val ch = createChannel()
        ch.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))
        ch.readInbound<Any>().shouldBeNull()
    }

    @Test
    fun `zero byte payload decodes correctly`() {
        val ch = createChannel()
        writeFrame(ch, byteArrayOf())
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        result.readableBytes().shouldBeEqualTo(0)
        result.release()
    }

    @Test
    fun `one byte payload decodes correctly`() {
        val ch = createChannel()
        writeFrame(ch, byteArrayOf(0x42))
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        result.readableBytes().shouldBeEqualTo(1)
        result.readByte().shouldBeEqualTo(0x42)
        result.release()
    }

    @Test
    fun `normal multi-byte frame decodes correctly`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val ch = createChannel()
        writeFrame(ch, payload)
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        val decoded = ByteArray(result.readableBytes())
        result.readBytes(decoded)
        decoded.shouldBeEqualTo(payload)
        result.release()
    }

    @Test
    fun `half packet reassembly works`() {
        val ch = createChannel()
        val payload = byteArrayOf(10, 20, 30, 40)
        val fullBuf = Unpooled.buffer(Int.SIZE_BYTES + payload.size)
        fullBuf.writeIntLE(payload.size)
        fullBuf.writeBytes(payload)
        val allBytes = ByteArray(fullBuf.readableBytes())
        fullBuf.readBytes(allBytes)
        fullBuf.release()

        // Write first half
        ch.writeInbound(Unpooled.wrappedBuffer(allBytes, 0, 4))
        ch.readInbound<Any>().shouldBeNull()

        // Write second half
        ch.writeInbound(Unpooled.wrappedBuffer(allBytes, 4, allBytes.size - 4))
        val result = ch.readInbound<io.netty.buffer.ByteBuf>()
        result.shouldNotBeNull()
        val decoded = ByteArray(result.readableBytes())
        result.readBytes(decoded)
        decoded.shouldBeEqualTo(payload)
        result.release()
    }

    @Test
    fun `negative length closes channel`() {
        val ch = createChannel()
        val buf = Unpooled.buffer(Int.SIZE_BYTES)
        buf.writeIntLE(-1)
        ch.writeInbound(buf)
        ch.isOpen.shouldBeFalse()
    }

    @Test
    fun `oversized frame closes channel`() {
        val ch = createChannel()
        val buf = Unpooled.buffer(Int.SIZE_BYTES)
        buf.writeIntLE(11 * 1024 * 1024)
        ch.writeInbound(buf)
        ch.isOpen.shouldBeFalse()
    }
}
