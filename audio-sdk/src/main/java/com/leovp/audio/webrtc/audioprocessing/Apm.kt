package com.leovp.audio.webrtc.audioprocessing

import com.leovp.androidbase.utils.log.LogContext

/**
 * Author: Michael Leo
 * Date: 20-5-6
 */
class Apm(
    aecExtendFilter: Boolean,
    speechIntelligibilityEnhance: Boolean,
    delayAgnostic: Boolean,
    beamforming: Boolean,
    nextGenerationAec: Boolean,
    experimentalNs: Boolean,
    experimentalAgc: Boolean
) {
    private var init: Boolean

    enum class AEC_SuppressionLevel {
        LowSuppression, ModerateSuppression, HighSuppression
    }

    // Recommended settings for particular audio routes. In general, the louder
    // the echo is expected to be, the higher this value should be set. The
    // preferred setting may vary from device to device.
    enum class AECM_RoutingMode {
        QuietEarpieceOrHeadset, Earpiece, LoudEarpiece, Speakerphone, LoudSpeakerphone
    }

    enum class AGC_Mode {
        // Adaptive mode intended for use if an analog volume control is available
        // on the capture device. It will require the user to provide coupling
        // between the OS mixer controls and AGC through the |stream_analog_level()|
        // functions.
        //
        // It consists of an analog gain prescription for the audio device and a
        // digital compression stage.
        AdaptiveAnalog,  // Adaptive mode intended for situations in which an analog volume control

        // is unavailable. It operates in a similar fashion to the adaptive analog
        // mode, but with scaling instead applied in the digital domain. As with
        // the analog mode, it additionally uses a digital compression stage.
        AdaptiveDigital,  // Fixed mode which enables only the digital compression stage also used by

        // the two adaptive modes.
        //
        // It is distinguished from the adaptive modes by considering only a
        // short time-window of the input signal. It applies a fixed gain through
        // most of the input level range, and compresses (gradually reduces gain
        // with increasing level) the input signal at higher levels. This mode is
        // preferred on embedded devices where the capture signal level is
        // predictable, so that a known gain can be applied.
        FixedDigital
    }

    // Determines the aggressiveness of the suppression. Increasing the level
    // will reduce the noise level at the expense of a higher speech distortion.
    enum class NS_Level {
        Low, Moderate, High, VeryHigh
    }

    // Specifies the likelihood that a frame will be declared to contain voice.
    // A higher value makes it more likely that speech will not be clipped, at
    // the expense of more noise being detected as voice.
    enum class VAD_Likelihood {
        VeryLowLikelihood, LowLikelihood, ModerateLikelihood, HighLikelihood
    }

    companion object {
        private const val TAG = "APM"

        init {
            System.loadLibrary("webrtc_apms")
        }
    }

    fun close() {
        if (init) {
            nativeFreeApmInstance()
            init = false
        }
    }

    fun enableHighPassFilter(enable: Boolean): Int {
        return high_pass_filter_enable(enable)
    }

    //AEC PC
    fun enableAecClockDriftCompensation(enable: Boolean): Int {
        return aec_clock_drift_compensation_enable(enable)
    }

    fun setAecSuppressionLevel(level: AEC_SuppressionLevel): Int { //[0, 1, 2]
        return aec_set_suppression_level(level.ordinal)
    }

    fun enableAec(enable: Boolean): Int {
        return aec_enable(enable)
    }

    // AEC Mobile
    fun setAecmSuppressionLevel(level: AECM_RoutingMode): Int { //[0, 1, 2]
        return aecm_set_suppression_level(level.ordinal)
    }

    fun enableAecm(enable: Boolean): Int {
        return aecm_enable(enable)
    }

    // NS
    fun enableNs(enable: Boolean): Int {
        return ns_enable(enable)
    }

    fun setNsLevel(level: NS_Level): Int { // [0, 1, 2, 3]
        return ns_set_level(level.ordinal)
    }

    // AGC
    fun setAGCAnalogLevelLimits(minimum: Int, maximum: Int): Int { // limit to [0, 65535]
        return agc_set_analog_level_limits(minimum, maximum)
    }

    fun setAgcMode(mode: AGC_Mode): Int { // [0, 1, 2]
        return agc_set_mode(mode.ordinal)
    }

    fun setAgcTargetLevelDbfs(level: Int): Int {
        return agc_set_target_level_dbfs(level)
    }

    fun setAgcCompressionGainDb(gain: Int): Int {
        return agc_set_compression_gain_db(gain)
    }

    fun enableAgcLimiter(enable: Boolean): Int {
        return agc_enable_limiter(enable)
    }

    fun setAgcStreamAnalogLevel(level: Int): Int {
        return agc_set_stream_analog_level(level)
    }

    fun setAgcStreamAnalogLevel(): Int {
        return agc_stream_analog_level()
    }

    fun enableAgc(enable: Boolean): Int {
        return agc_enable(enable)
    }

    // VAD
    fun enableVad(enable: Boolean): Int {
        return vad_enable(enable)
    }

    fun setVadLikeHood(likelihood: VAD_Likelihood): Int {
        return vad_set_likelihood(likelihood.ordinal)
    }

    fun vadHasVoice(): Boolean {
        return vad_stream_has_voice()
    }

    fun processCaptureStream(nearEnd: ShortArray, offset: Int): Int { // 16K, 16bits, mono， 10ms
        return ProcessStream(nearEnd, offset)
    }

    // processReverseStream: It is only necessary to provide this if echo processing is enabled, as the
    // reverse stream forms the echo reference signal. It is recommended, but not
    // necessary, to provide if gain control is enabled.
    // may modify |farEnd| if intelligibility is enabled.
    fun processReverseStream(farEnd: ShortArray, offset: Int): Int { // 16K, 16bits, mono， 10ms
        return ProcessReverseStream(farEnd, offset)
    }

    fun SetStreamDelay(delay_ms: Int): Int {
        return set_stream_delay_ms(delay_ms)
    }

    protected fun finalize() {
        close()
    }

    private val objData // Do not modify it.
            : Long = 0

    ////////////////////////////////////////////////////////////////////////////////////////////////
    fun initializeReSampler(inFreq: Int, outFreq: Int, num_channels: Long): Boolean {
        return SamplingInit(inFreq, outFreq, num_channels)
    }

    fun resetRreSampler(inFreq: Int, outFreq: Int, num_channels: Long): Int {
        return SamplingReset(inFreq, outFreq, num_channels)
    }

    fun resetReSamplerIfNeeded(inFreq: Int, outFreq: Int, num_channels: Long): Int {
        return SamplingResetIfNeeded(inFreq, outFreq, num_channels)
    }

    fun bytesPushToReSample(
        samplesIn: ShortArray,
        lengthIn: Long,
        samplesOut: ShortArray,
        maxLen: Long,
        outLen: Long
    ): Int {
        return SamplingPush(samplesIn, lengthIn, samplesOut, maxLen, outLen)
    }

    fun destroyReSampler(): Boolean {
        return SamplingDestroy()
    }

    // =======================================================================
    // ===== Native methods
    private external fun nativeCreateApmInstance(
        aecExtendFilter: Boolean,
        speechIntelligibilityEnhance: Boolean,
        delayAgnostic: Boolean,
        beamforming: Boolean,
        nextGenerationAec: Boolean,
        experimentalNs: Boolean,
        experimentalAgc: Boolean
    ): Boolean

    private external fun nativeFreeApmInstance()
    private external fun high_pass_filter_enable(enable: Boolean): Int
    private external fun aec_enable(enable: Boolean): Int
    private external fun aec_set_suppression_level(level: Int): Int //[0, 1, 2]
    private external fun aec_clock_drift_compensation_enable(enable: Boolean): Int
    private external fun aecm_enable(enable: Boolean): Int
    private external fun aecm_set_suppression_level(level: Int): Int //[0, 1, 2, 3, 4]
    private external fun ns_enable(enable: Boolean): Int
    private external fun ns_set_level(level: Int): Int // [0, 1, 2, 3]
    private external fun agc_enable(enable: Boolean): Int

    // Sets the target peak |level| (or envelope) of the AGC in dBFs (decibels
    // from digital full-scale). The convention is to use positive values. For
    // instance, passing in a value of 3 corresponds to -3 dBFs, or a target
    // level 3 dB below full-scale. Limited to [0, 31].
    private external fun agc_set_target_level_dbfs(level: Int): Int //[0,31]

    // Sets the maximum |gain| the digital compression stage may apply, in dB. A
    // higher number corresponds to greater compression, while a value of 0 will
    // leave the signal uncompressed. Limited to [0, 90].
    private external fun agc_set_compression_gain_db(gain: Int): Int //[0,90]

    // When enabled, the compression stage will hard limit the signal to the
    // target level. Otherwise, the signal will be compressed but not limited
    // above the target level.
    private external fun agc_enable_limiter(enable: Boolean): Int

    // Sets the |minimum| and |maximum| analog levels of the audio capture device.
    // Must be set if and only if an analog mode is used. Limited to [0, 65535].
    private external fun agc_set_analog_level_limits(minimum: Int, maximum: Int): Int // limit to [0, 65535]
    private external fun agc_set_mode(mode: Int): Int // [0, 1, 2]

    // When an analog mode is set, this must be called prior to |ProcessStream()|
    // to pass the current analog level from the audio HAL. Must be within the
    // range provided to |set_analog_level_limits()|.
    private external fun agc_set_stream_analog_level(level: Int): Int

    // When an analog mode is set, this should be called after |ProcessStream()|
    // to obtain the recommended new analog level for the audio HAL. It is the
    // users responsibility to apply this level.
    private external fun agc_stream_analog_level(): Int
    private external fun vad_enable(enable: Boolean): Int
    private external fun vad_set_likelihood(likelihood: Int): Int
    private external fun vad_stream_has_voice(): Boolean
    private external fun ProcessStream(nearEnd: ShortArray, offset: Int): Int // Near-End 10ms
    private external fun ProcessReverseStream(farEnd: ShortArray, offset: Int): Int // Far-End 10ms
    private external fun set_stream_delay_ms(delay: Int): Int

    // =======================================================================
    // ===== ReSampler Native Methods
    private external fun SamplingInit(inFreq: Int, outFreq: Int, num_channels: Long): Boolean
    private external fun SamplingReset(inFreq: Int, outFreq: Int, num_channels: Long): Int
    private external fun SamplingResetIfNeeded(inFreq: Int, outFreq: Int, num_channels: Long): Int
    private external fun SamplingPush(
        samplesIn: ShortArray,
        lengthIn: Long,
        samplesOut: ShortArray,
        maxLen: Long,
        outLen: Long
    ): Int

    private external fun SamplingDestroy(): Boolean

    // aecExtendFilter, delayAgnostic, nextGenerationAec
    // The above configurations only apply to EchoCancellation and not EchoControlMobile.
    init {
        if (!nativeCreateApmInstance(
                aecExtendFilter,
                speechIntelligibilityEnhance,
                delayAgnostic,
                beamforming,
                nextGenerationAec,
                experimentalNs,
                experimentalAgc
            )
        ) {
            LogContext.log.e(TAG, "Create APM failed")
            throw Exception("Create APM failed")
        }
        init = true
    }
}