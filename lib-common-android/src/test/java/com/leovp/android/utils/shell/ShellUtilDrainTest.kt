package com.leovp.android.utils.shell

import java.io.ByteArrayInputStream
import java.time.Duration
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test

/**
 * Author: Michael Leo
 *
 * Regression test for remediation H1: [ShellUtil.drainStreams] must read both streams concurrently
 * so a large payload on one stream can never block the other reader (the deadlock that used to
 * happen when [Process.waitFor] was called before the streams were drained).
 */
class ShellUtilDrainTest {

    @Test
    fun `drainStreams reads large stdout and stderr without deadlock`() {
        // ~160KB per stream, far beyond the typical 64KB OS pipe buffer.
        val lineCount = 20_000
        val outText = (1..lineCount).joinToString("\n") { "out-$it" }
        val errText = (1..lineCount).joinToString("\n") { "err-$it" }

        val (out, err) = assertTimeoutPreemptively<Pair<String, String>>(Duration.ofSeconds(10)) {
            ShellUtil.drainStreams(
                ByteArrayInputStream(outText.toByteArray()),
                ByteArrayInputStream(errText.toByteArray())
            )
        }

        out shouldContain "out-1"
        out shouldContain "out-$lineCount"
        err shouldContain "err-1"
        err shouldContain "err-$lineCount"
    }
}
