package com.leovp.screenshot.base

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午2:10
 */
interface ScreenProcessor {

    companion object {
        @Suppress("unused")
        const val NAL_SLICE = 1

        //        const val NAL_SLICE_DPA = 2
        //        const val NAL_SLICE_DPB = 3
        //        const val NAL_SLICE_DPC = 4
        @Suppress("unused")
        const val NAL_SLICE_IDR = 5

        //        const val NAL_SEI = 6
        @Suppress("unused")
        const val NAL_SPS = 7

        @Suppress("unused")
        const val NAL_PPS = 8
        //        const val NAL_AUD = 9
        //        const val NAL_FILLER = 12
    }

    fun onInit()
    fun onStart()

    /**
     * After `onStop()`, you can do `onStart()` again.
     * However, once do `onRelease()` you can not start again. Please call `onInit()` then `onStart()`
     */
    fun onStop()

    /**
     * Once do `onRelease()` you can not start again. Please call `onInit()` then `onStart()`
     */
    fun onRelease()
}