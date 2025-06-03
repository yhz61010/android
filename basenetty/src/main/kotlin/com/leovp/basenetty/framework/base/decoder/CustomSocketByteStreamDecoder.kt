package com.leovp.basenetty.framework.base.decoder

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

/**
 * Author: Michael Leo
 * Date: 20-5-13 下午4:39
 */
class CustomSocketByteStreamDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, inBuf: ByteBuf, out: MutableList<Any>) {
//        val readableSize = inBuf.readableBytes()
//        if (readableSize < 6) {
//            return
//        }
//        val beginIndex = inBuf.readerIndex()
//        val length = inBuf.readIntLE()
//        if (readableSize < length + 4) {
//            inBuf.readerIndex(beginIndex)
//            return
//        }
//        inBuf.readerIndex(beginIndex + length + 4)
//        val otherByteBufRef = inBuf.slice(beginIndex, length + 4)
//        otherByteBufRef.retain()
//        out.add(otherByteBufRef)

        val bufLen = inBuf.readableBytes()
        if (bufLen < 6) {
            return
        }
        inBuf.markReaderIndex()
        val dataLen = inBuf.readIntLE()
        if (inBuf.readableBytes() < dataLen) {
            inBuf.resetReaderIndex()
            return
        }
        out.add(inBuf.readBytes(dataLen))
    }
}
