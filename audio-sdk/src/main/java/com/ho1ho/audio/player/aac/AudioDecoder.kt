package com.ho1ho.audio.player.aac

import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.ho1ho.androidbase.utils.JsonUtil.toHexadecimalString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.audio.recorder.BuildConfig
import com.ho1ho.audio.recorder.aac.AudioEncoder.Companion.computePresentationTimeUs
import com.ho1ho.audio.webrtc.audioprocessing.AudioProcessing
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午2:12
 */
class AudioDecoder @JvmOverloads constructor(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val channelConfig: Int,
    private val audioFormat: Int,
    private val csd0: ByteArray,
    private val trackBufferSize: Int =
        AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
) {
    private var mAacOs: BufferedOutputStream? = null
    private var presentationTimeUs = AtomicLong(0)
    var audioTrack: AudioTrack? = null
        private set
    var audioDecoder: MediaCodec? = null
        private set

    //    private var mAudioManager: AudioManager? = null
    private var mAudioPlayThread: HandlerThread? = null
    private var mAudioPlayHandler: Handler? = null

    //    private var apmViewModel: ApmVM? = null
//    private var audioProcessing: AudioProcessing? = null
    private val readSize: Int
    private fun initAudioPlayThread() {
        mAudioPlayThread = HandlerThread("thread-audio-play")
        mAudioPlayThread!!.start()
        mAudioPlayHandler = Handler(mAudioPlayThread!!.looper)
    }

    init {
        val bytesPerFrame = channelCount * ((if (audioFormat == 2) 16 else 8) / 8)
        val framesPerBuffer = sampleRate / AudioProcessing.BUFFERS_PER_SECOND
        readSize = bytesPerFrame * framesPerBuffer / 2
        initAudioPlayThread()
        initAudioDecoder(sampleRate, channelCount, csd0)
        // AudioManager.STREAM_VOICE_CALL
        // AudioManager.STREAM_MUSIC
        LLog.i(
            TAG,
            """AacDecoder 
                sampleRate=$sampleRate
                channelCount=$channelCount
                channelConfig=$channelConfig
                audioFormat=$audioFormat
                trackBufferSize=$trackBufferSize
                realBufferSize=$readSize
                csd0=${toHexadecimalString(csd0)}
            """.trimIndent()
        )
        initAudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, audioFormat, trackBufferSize)
//        try {
//            apmViewModel = ApmVM()
//            apmViewModel.speechIntelligibilityEnhance = false
//
//            // AEC
//            apmViewModel.aecPC = false
//            // apmViewModel.setAecPCLevel(2);
//            apmViewModel.delayAgnostic = false
//            apmViewModel.aecExtendFilter = false
//            apmViewModel.nextGenerationAEC = false
//            apmViewModel.aecMobile = true
//            apmViewModel.aecMobileLevel = 3
//            apmViewModel.aecBufferDelay = 50
//
//            // AGC
//            apmViewModel.agc = true
//            apmViewModel.agcMode = 1
//            // For instance, passing in a value of 3 corresponds to -3 dBFs, or a target level 3 dB below full-scale.
//            apmViewModel.agcTargetLevel = 20 // [0, 31]
//            apmViewModel.agcCompressionGain = 10 // [0, 90]
//
//            // NS
//            apmViewModel.ns = false
//            apmViewModel.nsLevel = 2
//
//            // VAD
////            apmViewModel.setVad(true);
////
//            audioProcessing = AudioProcessing(apmViewModel)
//        } catch (e: Exception) {
//            CLog.e(TAG, e, "Init ApmViewModel error")
//        }
    }

    /**
     * Write audio data to disk<br></br>
     * <br></br>
     * DEBUG ONLY
     *
     * @param audioData The audio data to be written.
     */
    fun writeDataToDisk(audioData: ByteArray?) {
        try {
            if (mAacOs != null) {
                mAacOs!!.write(audioData)
            } else {
                LLog.d(TAG, "mAacOs is null")
            }
        } catch (e: Exception) {
            LLog.e(TAG, "You can ignore this message safely. writeDataToDisk error")
        }
    }

    /**
     * DEBUG ONLY
     */
    fun closeOutputStream() {
        if (mAacOs == null) {
            return
        }
        try {
            LLog.d(TAG, "END-OF-AUDIO stop stream.")
            mAacOs!!.flush()
            mAacOs!!.close()
        } catch (e: Exception) {
            LLog.d(TAG, "closeOutputStream error", e)
        }
    }

    /**
     * DEBUG ONLY
     */
    fun initOutputStream() {
        LLog.w(TAG, "START-AUDIO init stream.")
        val outputFolder: String = "/sdcard" + File.separator + "leo-audio"
        val folder = File(outputFolder)
        if (!folder.exists()) {
            val succ = folder.mkdirs()
            if (!succ) {
                LLog.e(TAG, "Can not create output file=$outputFolder")
            }
        }
        val aacFile =
            File(outputFolder, "received-original-" + SystemClock.elapsedRealtime() + ".aac")
        val aacFilename = aacFile.absolutePath
        try {
            mAacOs = BufferedOutputStream(FileOutputStream(aacFilename), 64 * 1024)
        } catch (e: Exception) {
            LLog.e(TAG, "initOutputStream error.", e)
        }
    }

    @Synchronized
    private fun initAudioTrack(
        streamType: Int,
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSize: Int
    ) {
//        mAudioManager =
//            CustomApplication.getInstance().getSystemService(Context.AUDIO_SERVICE)
//        //        mCurrentStreamType = streamType;
        LLog.w(TAG, "stream=$streamType")
        if (audioTrack != null) {
            LLog.e(TAG, "mAudioTrack is not null")
            return
        }
        try {
//            mAudioManager = (AudioManager) CustomApplication.getInstance().getSystemService(Context.AUDIO_SERVICE);
            audioTrack = AudioTrack(
                streamType,  // AudioManager.STREAM_VOICE_CALL // AudioManager.STREAM_MUSIC
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            if (AudioManager.STREAM_VOICE_CALL == streamType) {
                setOutputSourceInAudioManagerForEarphone()
            } else {
                setOutputSourceInAudioManagerForSpeaker()
            }
            if (AudioTrack.STATE_INITIALIZED == audioTrack!!.state) {
                audioTrack!!.play()
            }
        } catch (e: Exception) {
            LLog.e(TAG, "initAudioTrack error", e)
        }
    }

    // https://juejin.im/post/5c36bbad6fb9a049d975676b
    // https://stackoverflow.com/questions/56106877/how-to-decode-aac-formatmp4-audio-file-to-pcm-format-in-android
    // https://www.jianshu.com/p/b30d6a4f745b
    // https://blog.csdn.net/lavender1626/article/details/80431902
    // http://sohailaziz05.blogspot.com/2014/06/mediacodec-decoding-aac-android.html
    private fun initAudioDecoder(sampleRate: Int, channelCount: Int, csd0: ByteArray) {
        try {
//            String folder = Objects.requireNonNull(CustomApplication.instance.getExternalFilesDir(null)).getAbsolutePath() + File.separator + "leo-audio";
//            File mFilePath = new File(folder, "original.aac");

//            mMediaExtractor = new MediaExtractor();
//            mMediaExtractor.setDataSource(mFilePath.getAbsolutePath());

//            MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(0);
//            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
//            if (mime.startsWith("audio")) {
//                mMediaExtractor.selectTrack(0);

//                mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
//                mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//                mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, AacEncoder.DEFAULT_SAMPLE_RATES);
//                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, AacEncoder.DEFAULT_BIT_RATES);
//                mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, CHANNEL_IN);
//                mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
//                mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            )
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE_AAC_LC)
            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1)

            // ByteBuffer key
            // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
            // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
            // Convert them to binary value: 0b10, 0b100, 0b1
            // According to AAC required, convert theirs values to binary bits:
            // 00010 0100 0001 000
            // The corresponding hex value：
            // 0001 0010 0000 1000
            // So the csd_0 value is 0x12,0x08
            // https://developer.android.com/reference/android/media/MediaCodec
            // AAC CSD: Decoder-specific information from ESDS
            val csd_0 = ByteBuffer.wrap(csd0)
            // Set ADTS decoder information.
            mediaFormat.setByteBuffer("csd-0", csd_0)
            audioDecoder!!.configure(mediaFormat, null, null, 0)
        } catch (e: IOException) {
            LLog.e(TAG, "initAudioDecoder error.", e)
        }
        if (audioDecoder == null) {
            LLog.e(TAG, "mAudioDecoder is null")
            return
        }
        // Start MediaCodec. Waiting to receive data.
        audioDecoder!!.start()
    }

    fun decodeAndPlay(audioData: ByteArray) {
        val st = SystemClock.elapsedRealtime()
        try {
            val bufferInfo = MediaCodec.BufferInfo()
            val inputBuffer: ByteBuffer?
            var outputBuffer: ByteBuffer?

            // See the dequeueInputBuffer method in document to confirm the timeoutUs parameter.
            val inputIndex = audioDecoder!!.dequeueInputBuffer(-1)
            if (BuildConfig.DEBUG) {
                LLog.d(TAG, "inputIndex=$inputIndex")
            }
            if (inputIndex < 0) {
                return
            }
            inputBuffer = audioDecoder!!.getInputBuffer(inputIndex)
            if (inputBuffer != null) {
                // Clear exist data.
                inputBuffer.clear()
                // Put pcm audio data to encoder.
                inputBuffer.put(audioData)
            }
            audioDecoder!!.queueInputBuffer(
                inputIndex,
                0,
                audioData.size,
                computePresentationTimeUs(
                    presentationTimeUs.getAndIncrement(),
                    sampleRate
                ) /*mMediaExtractor.getSampleTime()*/,
                0
            )

            // Start decoding and get output index
            var outputIndex = audioDecoder!!.dequeueOutputBuffer(bufferInfo, 0)
            if (BuildConfig.DEBUG) {
                LLog.d(TAG, "outputIndex=$outputIndex")
            }
            var chunkPCM: ByteArray
            // Get decoded data in bytes
            while (outputIndex >= 0) {
                outputBuffer = audioDecoder!!.getOutputBuffer(outputIndex)
                chunkPCM = ByteArray(bufferInfo.size)
                if (outputBuffer != null) {
                    outputBuffer[chunkPCM]
                } else {
                    LLog.e(TAG, "outputBuffer is null")
                }
                // Must clear decoded data before next loop. Otherwise, you will get the same data while looping.
                if (chunkPCM.size > 0) {
                    val finalChunkPCM = chunkPCM
                    mAudioPlayHandler!!.post {
                        if (BuildConfig.DEBUG) {
                            LLog.d(TAG, "PCM data[${finalChunkPCM.size}]")
                        }
                        if (audioTrack == null || AudioTrack.STATE_UNINITIALIZED == audioTrack!!.state) {
                            return@post
                        }
                        if (AudioTrack.PLAYSTATE_PLAYING == audioTrack!!.playState) {
                            val apmPcmData = ShortArray(finalChunkPCM.size / 2)
                            for (i in apmPcmData.indices) {
                                apmPcmData[i] =
                                    (finalChunkPCM[i * 2].toInt() and 0xFF or (finalChunkPCM[i * 2 + 1].toInt() shl 8)).toShort()
                            }
                            var idx = 0
                            var totalRead = 0
                            var realReadSize: Int
                            var apmSt: Long
                            var apmEd: Long
                            var playPartData: ShortArray
                            var i = 0
                            while (i < apmPcmData.size) {
                                realReadSize =
                                    if (idx * readSize + readSize < apmPcmData.size) readSize else apmPcmData.size - idx * readSize
                                totalRead += realReadSize
                                playPartData = ShortArray(realReadSize)
                                System.arraycopy(apmPcmData, idx * readSize, playPartData, 0, realReadSize)
                                idx++
                                apmSt = SystemClock.elapsedRealtimeNanos()
//                                if (BuildConfig.ENABLE_WEBRTC) {
//                                    // Far-End
//                                    audioProcessing!!.process(2, playPartData, playPartData.size)
//                                }
                                apmEd = SystemClock.elapsedRealtimeNanos()
                                if (BuildConfig.DEBUG) {
                                    LLog.d(TAG, "Play APM[${playPartData.size * 2}] cost=${(apmEd - apmSt) / 1000}us")
                                }

                                // Play decoded audio data in PCM
                                audioTrack!!.write(playPartData, 0, playPartData.size)
                                i += readSize
                            }
                            if (totalRead < apmPcmData.size) {
                                playPartData = ShortArray(apmPcmData.size - totalRead)
                                System.arraycopy(
                                    apmPcmData,
                                    idx * readSize,
                                    playPartData,
                                    0,
                                    playPartData.size
                                )

//                                apmSt = SystemClock.elapsedRealtimeNanos();
//                                if (BuildConfig.ENABLE_WEBRTC) {
//                                audioProcessing.processReverseStream(playPartData, playPartData.length);
//                                }
//                                apmEd = SystemClock.elapsedRealtimeNanos();
                                audioTrack!!.write(playPartData, 0, playPartData.size)
                            }
                        } /* else {
                            synchronized (AudioDecoder.class) {
//                                    CLog.e(TAG, "AT=%s", mAudioTrack);
                                if (mAudioTrack != null) {
                                    CLog.e(TAG, "ts=%d ps=%d", mAudioTrack.getState(), mAudioTrack.getPlayState());
//                                        mAudioTrack.play();
//                                mAudioTrack.write(finalChunkPCM, 0, finalChunkPCM.length);
                                }
//                                initAudioTrack(mSampleRate, mChannelConfig, mAudioFormat, mTrackBufferSize);
                            }
                        }*/
                    }
                }
                audioDecoder!!.releaseOutputBuffer(outputIndex, false)
                // Get data again.
                outputIndex = audioDecoder!!.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
//            e.printStackTrace();
            LLog.e(TAG, "You can ignore this message safely. decodeAndPlay error")
        } finally {
            val ed = SystemClock.elapsedRealtime()
            LLog.d(TAG, "Cost=${ed - st}")
        }
    }

    val audioTimeUs: Long
        get() {
            var latencyUs: Long? = null
            return try {
                val numFramesPlayed = audioTrack!!.playbackHeadPosition
                val getLatencyMethod: Method? = AudioTrack::class.java.getMethod("getLatency", null)
                if (getLatencyMethod != null) {
                    latencyUs = getLatencyMethod.invoke(audioTrack, null as Array<Any?>?) as Int * 1000L / 2
                    latencyUs = Math.max(latencyUs, 0)
                }
                numFramesPlayed * 1000000L / sampleRate - latencyUs!!
            } catch (e: Exception) {
                0
            }
        }

    private fun purgeAudioPlayThread() {
        LLog.i(TAG, "purgeAudioPlayThread()")
        if (mAudioPlayHandler != null) {
            LLog.i(TAG, "mAudioPlayHandler removeCallbacks")
            mAudioPlayHandler!!.removeCallbacksAndMessages(null)
        }
        if (mAudioPlayThread != null) {
            LLog.i(TAG, "mAudioPlayThread.quitSafely()")
            mAudioPlayThread!!.quitSafely()
        }
    }

    @Synchronized
    fun flush(): Long {
        LLog.i(TAG, "flush()")
        val st = SystemClock.elapsedRealtime()
        if (audioTrack != null /* && STATE_UNINITIALIZED != mAudioTrack.getState()*/) {
            audioTrack!!.pause()
            audioTrack!!.flush()
            audioTrack!!.release()
            audioTrack = null
        }
        LLog.w(TAG, "flush")
        initAudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, audioFormat, trackBufferSize)
        if (audioDecoder != null) {
            audioDecoder!!.flush()
            //                mAudioDecoder.stop();
//                mAudioDecoder.start();
        }
        presentationTimeUs = AtomicLong(0)

//            if (Constants.ENABLE_AUDIO_DEBUG) {
//                closeOutputStream();
//            }
        val cost = SystemClock.elapsedRealtime() - st
        LLog.d(TAG, "flush cost=$cost")
        return cost
    }

    @Synchronized
    @Throws(Exception::class)
    fun stop() {
        try {
            if (audioTrack != null && AudioTrack.STATE_UNINITIALIZED != audioTrack!!.state) {
                audioTrack!!.pause()
                audioTrack!!.flush()
            }
            if (audioDecoder != null) {
                audioDecoder!!.flush()
                audioDecoder!!.stop()
            }
        } finally {
            presentationTimeUs = AtomicLong(0)
            if (BuildConfig.DEBUG) {
                closeOutputStream()
            }
        }
    }

    @Synchronized
    @Throws(Exception::class)
    fun release() {
        stop()
        if (audioTrack != null) {
            audioTrack!!.release()
            audioTrack = null
        }
        if (audioDecoder != null) {
            audioDecoder!!.release()
            audioDecoder = null
        }
//        if (audioProcessing != null) {
//            audioProcessing!!.onDestroy()
//            audioProcessing = null
//        }
        purgeAudioPlayThread()
    }

    val playState: Int
        get() = try {
            if (audioTrack != null) {
                audioTrack!!.playState
            } else AudioTrack.PLAYSTATE_STOPPED
        } catch (e: Exception) {
            AudioTrack.PLAYSTATE_STOPPED
        }

    val audioTrackState: Int
        get() = try {
            if (audioTrack != null) {
                audioTrack!!.state
            } else AudioTrack.STATE_UNINITIALIZED
        } catch (e: Exception) {
            AudioTrack.STATE_UNINITIALIZED
        }

    fun playAudioInSpeaker() {
        LLog.w(TAG, "3 playAudioInSpeaker")

        // AudioManager.STREAM_VOICE_CALL // AudioManager.STREAM_MUSIC
        LLog.w(TAG, "4 set audioOutputSource=STREAM_MUSIC")
        flush()
        setOutputSourceInAudioManagerForSpeaker()
    }

    private fun setOutputSourceInAudioManagerForSpeaker() {
//        if (mAudioManager != null) {
//            mAudioManager!!.mode = AudioManager.MODE_NORMAL
//            mAudioManager!!.isSpeakerphoneOn = true
//        }
    }

    fun playAudioInEarphone() {
        LLog.w(TAG, "3 playAudioInEarphone")

        // AudioManager.STREAM_VOICE_CALL // AudioManager.STREAM_MUSIC
//        CustomApplication.getInstance().audioOutputSource = AudioManager.STREAM_VOICE_CALL
        LLog.w(TAG, "6 set audioOutputSource=STREAM_VOICE_CALL")
        flush()
        setOutputSourceInAudioManagerForEarphone()
    }

    private fun setOutputSourceInAudioManagerForEarphone() {
//        if (mAudioManager != null) {
//            mAudioManager!!.mode = AudioManager.MODE_IN_COMMUNICATION
//            mAudioManager!!.isSpeakerphoneOn = false
//        }
    }

    companion object {
        private const val TAG = "AuDE"
        private const val PROFILE_AAC_LC = MediaCodecInfo.CodecProfileLevel.AACObjectLC

        // We ask for a native buffer size of BUFFER_SIZE_FACTOR * (minimum required
        // buffer size). The extra space is allocated to guard against glitches under
        // high load.
        private const val BUFFER_SIZE_FACTOR = 1
    }
}