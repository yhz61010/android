package com.leovp.android.utils

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Author: Michael Leo
 *
 * Unit tests for the path-traversal hardening of [FileDocumentUtil] (remediation C1).
 *
 * These exercise only the pure `java.io.File` sanitization helpers, so they run on the plain JVM
 * without Robolectric.
 */
class FileDocumentUtilTest {

    @Test
    fun `sanitizedFileName strips path-traversal segments down to the base name`() {
        // A malicious content DISPLAY_NAME must never keep its `..` segments.
        FileDocumentUtil.sanitizedFileName("../../evil.db") shouldBeEqualTo "evil.db"
        FileDocumentUtil.sanitizedFileName("/abs/path/secret.xml") shouldBeEqualTo "secret.xml"
    }

    @Test
    fun `sanitizedFileName keeps a normal file name unchanged`() {
        FileDocumentUtil.sanitizedFileName("report.txt") shouldBeEqualTo "report.txt"
    }

    @Test
    fun `sanitizedFileName rejects dot-dot and blank names`() {
        assertThrows<IllegalArgumentException> { FileDocumentUtil.sanitizedFileName("..") }
        assertThrows<IllegalArgumentException> { FileDocumentUtil.sanitizedFileName("   ") }
    }

    @Test
    fun `resolveWithinBase keeps a traversal display name inside the base dir`(@TempDir base: File) {
        val resolved = FileDocumentUtil.resolveWithinBase(base, "../../evil.db")

        resolved.name shouldBeEqualTo "evil.db"
        resolved.canonicalPath.startsWith(base.canonicalPath + File.separator).shouldBeTrue()
    }

    @Test
    fun `resolveWithinBase resolves a normal name under the base dir`(@TempDir base: File) {
        FileDocumentUtil.resolveWithinBase(base, "ok.txt").canonicalPath shouldBeEqualTo
            File(base, "ok.txt").canonicalPath
    }

    @Test
    fun `getPathFromExtSD returns null for a malformed docId with no relative path`() {
        // Previously this accessed pathData[1] directly and threw ArrayIndexOutOfBoundsException.
        FileDocumentUtil.getPathFromExtSD(arrayOf("primary")).shouldBeNull()
    }

    @Test
    fun `stripRawDownloadPrefix strips document-raw and raw prefixes`() {
        FileDocumentUtil.stripRawDownloadPrefix("/document/raw:/storage/emulated/0/x.pdf") shouldBeEqualTo
            "/storage/emulated/0/x.pdf"
        FileDocumentUtil.stripRawDownloadPrefix("raw:/a/b") shouldBeEqualTo "/a/b"
        FileDocumentUtil.stripRawDownloadPrefix("/plain/path") shouldBeEqualTo "/plain/path"
    }

    @Test
    fun `stripRawDownloadPrefix returns null for a null path`() {
        // Opaque Uris expose a null path; the helper must not throw (remediation H3).
        FileDocumentUtil.stripRawDownloadPrefix(null).shouldBeNull()
    }
}
