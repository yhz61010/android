package com.leovp.androidbase

import com.leovp.androidbase.utils.CrashHandler
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 *
 * Regression tests for crash-handler chaining.
 */
class CrashHandlerTest {
    @Test fun `repeated init does not recursively call the same wrapper`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val calls = AtomicInteger(0)
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> calls.incrementAndGet() }

            CrashHandler.initCrashHandler { _, _ -> calls.addAndGet(10) }
            CrashHandler.initCrashHandler { _, _ -> calls.addAndGet(100) }

            Thread.getDefaultUncaughtExceptionHandler()
                ?.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertEquals(111, calls.get())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test fun `a throwing previous handler does not escape the installed default handler`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val previousCalled = AtomicBoolean(false)
        val customCalled = AtomicBoolean(false)
        try {
            // The already-installed handler (e.g. a misbehaving third-party reporter) throws.
            Thread.setDefaultUncaughtExceptionHandler { _, _ ->
                previousCalled.set(true)
                throw RuntimeException("previous handler boom")
            }
            CrashHandler.initCrashHandler { _, _ -> customCalled.set(true) }

            val installed = requireNotNull(Thread.getDefaultUncaughtExceptionHandler())
            // Must not propagate the previous handler's exception out of the default handler.
            installed.uncaughtException(Thread.currentThread(), RuntimeException("crash"))

            assertTrue(customCalled.get(), "custom handler must run")
            assertTrue(previousCalled.get(), "previous handler must still be chained")
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
