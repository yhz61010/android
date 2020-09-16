package com.ho1ho.audio.webrtc.audioprocessing

/**
 * Author: Michael Leo
 * Date: 20-6-18 下午2:15
 */
class ApmVM {
    var start = false

    // Speech Quality
    var highPassFilter = false
    var speechIntelligibilityEnhance = true
    var beamForming = false
    var speaker = true

    // AEC
    var aecBufferDelay = 150
    var aecPC = false
    var aecExtendFilter = false
    var delayAgnostic = true
    var nextGenerationAEC = false
    var aecPCLevel = 2 // [0, 2]
    var aecMobile = true
    var aecMobileLevel = 3 // [0, 4]
    var aecNone = false

    // NS
    var ns = true
    var experimentalNS = false
    var nsLevel = 2 // [0, 3]

    // AGC
    var agc = true
    var experimentalAGC = false
    var agcTargetLevel = 2 // [0, 31]
    var agcCompressionGain = 9 // [0, 90]
    var agcMode = 1 // [0, 2]

    // VAD
    var vad = true
    var rcvCount = 0
    var sndCount = 0
}