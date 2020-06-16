package com.ho1ho.screenshot.base

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午2:10
 */
interface ScreenProcessor {
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