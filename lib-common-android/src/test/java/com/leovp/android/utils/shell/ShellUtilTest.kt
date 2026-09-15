package com.leovp.android.utils.shell

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Author: Michael Leo
 *
 * Unit tests for the package-name validation that closes the shell command-injection vector in
 * [ShellUtil.forceStop] / [ShellUtil.uninstallApk] (remediation C2).
 */
class ShellUtilTest {

    @Test
    fun `requireValidPackage accepts a well-formed package name`() {
        ShellUtil.requireValidPackage("com.leovp.demo") shouldBeEqualTo "com.leovp.demo"
    }

    @Test
    fun `requireValidPackage rejects shell metacharacters`() {
        assertThrows<IllegalArgumentException> {
            ShellUtil.requireValidPackage("com.foo; rm -rf /sdcard")
        }
    }

    @Test
    fun `requireValidPackage rejects a single-segment or blank name`() {
        assertThrows<IllegalArgumentException> { ShellUtil.requireValidPackage("notapackage") }
        assertThrows<IllegalArgumentException> { ShellUtil.requireValidPackage("") }
    }

    @Test
    fun `uninstallApk rejects an injecting package name before touching the shell`() {
        // Must throw at validation, never reaching execCmd / the su shell.
        assertThrows<IllegalArgumentException> {
            ShellUtil.uninstallApk("a; rm -rf /sdcard")
        }
    }

    @Test
    fun `process parser skips toybox header and parses process rows`() {
        val output = """
            USER PID PPID VSZ RSS WCHAN ADDR S NAME
            root 1 0 10849520 9540 0 0 S init
            shell 123 1 1000 200 futex 0 S sh
        """.trimIndent()

        val processes = ShellUtil.parseProcessesList(output)

        processes.size shouldBeEqualTo 2
        processes[0].pid shouldBeEqualTo 1
        processes[0].name shouldBeEqualTo "init"
        processes[1].pid shouldBeEqualTo 123
        processes[1].ppid shouldBeEqualTo 1
    }
}
