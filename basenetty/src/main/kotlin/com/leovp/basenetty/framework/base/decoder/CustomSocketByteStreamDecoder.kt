package com.leovp.basenetty.framework.base.decoder

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

/**
 * Custom byte stream decoder with a 4-byte little-endian length prefix.
 *
 * Frame format: [4-byte LE length][payload bytes]
 *
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
class CustomSocketByteStreamDecoder : ByteToMessageDecoder() {
    companion object {
        /** Maximum allowed frame payload size (10 MB). */
        const val MAX_FRAME_SIZE = 10 * 1024 * 1024
    }

    override fun decode(ctx: ChannelHandlerContext, inBuf: ByteBuf, out: MutableList<Any>) {
        if (inBuf.readableBytes() < Int.SIZE_BYTES) return

        inBuf.markReaderIndex()
        val dataLen = inBuf.readIntLE()

        if (dataLen < 0 || dataLen > MAX_FRAME_SIZE) {
            inBuf.resetReaderIndex()
            ctx.close()
            return
        }

        if (inBuf.readableBytes() < dataLen) {
            inBuf.resetReaderIndex()
            return
        }
        out.add(inBuf.readBytes(dataLen))
    }
}
