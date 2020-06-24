package com.webrtc.audioprocessing

import com.ho1ho.androidbase.utils.CLog

/**
 * Author: Michael Leo
 * Date: 20-5-6
 */
class AudioProcessing(private val mApmVM: ApmVM) {
    private lateinit var mApm: Apm
    fun onDestroy() {
        try {
            mApmVM.start = false
            mApm.close()
        } catch (e: Exception) {
            CLog.e(TAG, "onDestroy error", e)
        }
    }

    // 1: Near end
    // 2: Far end
    fun process(flag: Int, processBuffer: ShortArray, readSize: Int) {
        var outAnalogLevel = 200
        try {
            if (readSize == processBuffer.size) {
                for (i in 0 until AEC_LOOP_COUNT) {
                    val processBufferOffSet = i * processBuffer.size / AEC_LOOP_COUNT
                    mApm.SetStreamDelay(mApmVM.aecBufferDelay)
                    if (mApmVM.agc) {
                        mApm.setAgcStreamAnalogLevel(outAnalogLevel)
                    }
                    if (flag == 1) {
                        // Near-End
                        mApm.processCaptureStream(processBuffer, processBufferOffSet)
                    } else {
                        // Far-End
                        mApm.processReverseStream(processBuffer, processBufferOffSet)
                    }
                    if (mApmVM.agc) {
                        outAnalogLevel = mApm.setAgcStreamAnalogLevel()
                    }
                    if (mApmVM.vad) {
                        if (!mApm.vadHasVoice()) {
                            continue
                        }
                    }
                }
            } else {
                CLog.d(TAG, "processReverseStream length invalid")
            }
        } catch (ex: Exception) {
            CLog.e(TAG, "processReverseStream error", ex)
        }
    }

    companion object {
        private const val TAG = "AP"
        private const val AEC_BUFFER_SIZE_MS = 10
        private const val CALLBACK_BUFFER_SIZE_MS = 10
        const val BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS
        private const val AEC_LOOP_COUNT =
            CALLBACK_BUFFER_SIZE_MS / AEC_BUFFER_SIZE_MS
    }

    init {
        CLog.i(TAG, mApmVM.toString())
        try {
            mApm = Apm(
                mApmVM.aecExtendFilter,
                mApmVM.speechIntelligibilityEnhance,
                mApmVM.delayAgnostic,
                mApmVM.beamForming,
                mApmVM.nextGenerationAEC,
                mApmVM.experimentalNS,
                mApmVM.experimentalAGC
            )
            mApm.enableHighPassFilter(mApmVM.highPassFilter)
            if (mApmVM.aecPC) {
                mApm.enableAecClockDriftCompensation(false)
                mApm.setAecSuppressionLevel(Apm.AEC_SuppressionLevel.values()[mApmVM.aecPCLevel])
                mApm.enableAec(true)
            } else if (mApmVM.aecMobile) {
                mApm.setAecmSuppressionLevel(Apm.AECM_RoutingMode.values()[mApmVM.aecMobileLevel])
                mApm.enableAecm(true)
            }
            mApm.setNsLevel(Apm.NS_Level.values()[mApmVM.nsLevel])
            mApm.enableNs(mApmVM.ns)
            mApm.enableVad(mApmVM.vad)
            if (mApmVM.agc) {
                mApm.setAGCAnalogLevelLimits(0, 255)
                mApm.setAgcMode(Apm.AGC_Mode.values()[mApmVM.agcMode])
                mApm.setAgcTargetLevelDbfs(mApmVM.agcTargetLevel)
                mApm.setAgcCompressionGainDb(mApmVM.agcCompressionGain)
                mApm.enableAgcLimiter(true)
                mApm.enableAgc(true)
            }
            mApmVM.start = true
        } catch (ex: Exception) {
            CLog.e(TAG, "initApm error", ex)
        }
    }
}