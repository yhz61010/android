package com.ho1ho.leoandroidbaseutil.basic_components.examples.media_player.base

import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 20-7-28 下午4:54
 */
object DecodeH264RawFile {
    private const val TAG = "DecodeH264File"

    private val inputStream: FileInputStream? = null
    private var rf: RandomAccessFile? = null

    //当前读到的帧位置
    private val curIndex = 0
    private val builder = StringBuilder()

    //    private val SLICE: Array<String>
//    private val byteList: List<Byte?> = ArrayList<Any?>()
    private val isStartCode4 = false
//    fun init() {
//        initInputStream()
//    }

//    private fun initInputStream() {
//        val file = File(MyApplication.H264_PLAY_PATH)
//        if (!file.exists()) {
//            return
//        }
//        try {
//            rf = RandomAccessFile(file, "r")
//        } catch (e: FileNotFoundException) {
//            e.printStackTrace()
//        }
//    }

    /**
     * 读取每一帧数据
     *
     * @param buffer
     * @return
     */
    fun readSampleData(buffer: ByteBuffer): Int {
        val nal = nALU
        buffer.put(nal)
        return nal!!.size
    }

    private val nALU: ByteArray?
        private get() {
            try {
                var curpos = 0
                val bb = ByteArray(100000)
                rf!!.read(bb, 0, 4)
                if (findStartCode4(bb, 0)) {
                    curpos = 4
                } else {
                    rf!!.seek(0)
                    rf!!.read(bb, 0, 3)
                    if (findStartCode3(bb, 0)) {
                        curpos = 3
                    }
                }
                var findNALStartCode = false
                var nextNalStartPos = 0
                var reWind = 0
                while (!findNALStartCode) {
                    val hex = rf!!.read()
                    if (curpos >= bb.size) {
                        break
                    }
                    bb[curpos++] = hex.toByte()
                    if (hex == -1) {
                        nextNalStartPos = curpos
                    }
                    if (findStartCode4(bb, curpos - 4)) {
                        findNALStartCode = true
                        reWind = 4
                        nextNalStartPos = curpos - reWind
                    } else if (findStartCode3(bb, curpos - 3)) {
                        findNALStartCode = true
                        reWind = 3
                        nextNalStartPos = curpos - reWind
                    }
                }
                val nal = ByteArray(nextNalStartPos)
                System.arraycopy(bb, 0, nal, 0, nextNalStartPos)
                val pos = rf!!.filePointer
                val setPos = pos - reWind
                rf!!.seek(setPos)
                return nal
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

    //find match "00 00 00 01"
    private fun findStartCode4(bb: ByteArray, offSet: Int): Boolean {
        if (offSet < 0) {
            return false
        }
        return bb[offSet].toInt() == 0 && bb[offSet + 1].toInt() == 0 && bb[offSet + 2].toInt() == 0 && bb[offSet + 3].toInt() == 1
    }

    //find match "00 00 01"
    private fun findStartCode3(bb: ByteArray, offSet: Int): Boolean {
        if (offSet <= 0) {
            return false
        }
        return bb[offSet].toInt() == 0 && bb[offSet + 1].toInt() == 0 && bb[offSet + 2].toInt() == 1
    }

    fun close() {
    }

//    init {
//        init()
//    }
}