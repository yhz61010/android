package com.leovp.androidbase

import com.leovp.androidbase.utils.CrashHandler
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
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
}
