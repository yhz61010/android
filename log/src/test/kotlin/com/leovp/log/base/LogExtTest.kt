package com.leovp.log.base

import com.leovp.log.LogContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogExtTest {
    @AfterEach
    fun tearDown() {
        LogContext.setLogImpl(TestLog(LogLevel.VERB, false))
    }

    @Test
    fun `disabled level does not evaluate debug configuration or message`() {
        var configured = false
        var evaluated = false
        LogContext.setLogImpl(TestLog(LogLevel.ERROR, true))

        d {
            configured = true
            tag = "test"
            block = {
                evaluated = true
                "debug"
            }
        }

        assertFalse(configured)
        assertFalse(evaluated)
    }

    @Test
    fun `enabled level evaluates message`() {
        var evaluated = false
        val logger = TestLog(LogLevel.ERROR, true)
        LogContext.setLogImpl(logger)

        e("test") {
            evaluated = true
            "error"
        }

        assertTrue(evaluated)
        assertTrue(logger.errorPrinted)
    }

    private class TestLog(logLevel: LogLevel, enableLog: Boolean) :
        AbsLog("TEST", logLevel = logLevel, enableLog = enableLog) {
        var errorPrinted = false

        override fun printVerbLog(tag: String, message: String, outputType: LogOutType) = Unit
        override fun printDebugLog(tag: String, message: String, outputType: LogOutType) = Unit
        override fun printInfoLog(tag: String, message: String, outputType: LogOutType) = Unit
        override fun printWarnLog(tag: String, message: String, outputType: LogOutType) = Unit
        override fun printErrorLog(tag: String, message: String, outputType: LogOutType) {
            errorPrinted = true
        }

        override fun printFatalLog(tag: String, message: String, outputType: LogOutType) = Unit
    }
}
