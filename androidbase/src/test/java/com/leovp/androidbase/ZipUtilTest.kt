package com.leovp.androidbase

import android.util.Log
import com.leovp.androidbase.utils.cipher.ZipUtil
import java.io.File
import kotlin.test.Test
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.TestInstance
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Author: Michael Leo
 * Date: 2025/4/1 13:47
 */

@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZipUtilTest {

    private lateinit var baseDir: File
    private lateinit var testDir: File
    private lateinit var testZipFile: File
    private lateinit var testZipFile2: File
    private lateinit var testZipFile3: File
    private lateinit var extractDir: File

    companion object {
        private const val TEST_DIR = "testDir"
    }

    @BeforeAll
    fun setUp() {
        baseDir = File("build", "tmp").apply { mkdirs() }
        // Create test directory
        testDir = File(baseDir, TEST_DIR).apply { mkdirs() }

        // Create some test files
        File(testDir, "file1.txt").writeText("This is file 1")
        File(testDir, "file2.txt").writeText("This is file 2")

        println("testDir=${testDir.absolutePath}")

        // Create a subdirectory
        val subDir = File(testDir, "subDir").apply { mkdirs() }
        File(subDir, "file3.txt").writeText("This is file 3")

        // Define the ZIP output path
        testZipFile = File(baseDir, "test.zip")
        testZipFile2 = File(baseDir, "test2.zip")
        testZipFile3 = File(baseDir, "test3.zip")

        // Define extraction directory
        extractDir = File(baseDir, "extracted").apply { mkdirs() }
    }

    @AfterAll
    fun tearDown() {
        // Cleanup all test files and directories
        testDir.deleteRecursively()
        testZipFile.delete()
        testZipFile2.delete()
        testZipFile3.delete()
        extractDir.deleteRecursively()
    }

    @Test
    @DisplayName("Test Zipping and Unzipping Files")
    fun zipFolderAndUnzipTest() {
        // Compress test directory
        ZipUtil.zip(testDir.absolutePath, testZipFile.absolutePath)

        // Ensure ZIP file is created
        assertEquals(true, testZipFile.exists())
        assert(testZipFile.length() > 0)

        assertEquals(true, ZipUtil.isZipFile(testZipFile))

        // Extract ZIP file
        ZipUtil.unzip(testZipFile.absolutePath, extractDir.absolutePath)

        // Verify extracted files
        val extractedFile1 = File(extractDir, "$TEST_DIR/file1.txt")
        val extractedFile2 = File(extractDir, "$TEST_DIR/file2.txt")
        val extractedFile3 = File(extractDir, "$TEST_DIR/subDir/file3.txt")

        assertEquals(false, ZipUtil.isZipFile(extractedFile1))

        println("extractedFile1=${extractedFile1.absolutePath}")
        assertEquals(true, extractedFile1.exists())
        assertEquals("This is file 1", extractedFile1.readText())

        assertEquals(true, extractedFile2.exists())
        assertEquals("This is file 2", extractedFile2.readText())

        assertEquals(true, extractedFile3.exists())
        assertEquals("This is file 3", extractedFile3.readText())
    }

    @Test
    @DisplayName("Test File Zipping and Unzipping Files")
    fun zipFileAndUnzipTest() {
        // Create some test files
        val txtFile1 = File(testDir, "text_file_1.txt").apply { writeText("This is text file 1") }
        val txtFile2 = File(testDir, "text_file_2.txt").apply { writeText("This is text file 2") }

        // Compress test directory
        ZipUtil.zip(listOf(txtFile1, txtFile2), testZipFile2.absolutePath)

        // Ensure ZIP file is created
        assertEquals(true, testZipFile2.exists())
        assert(testZipFile2.length() > 0)

        assertEquals(true, ZipUtil.isZipFile(testZipFile2))

        // Extract ZIP file
        ZipUtil.unzip(testZipFile2.absolutePath, extractDir.absolutePath)

        // Verify extracted files
        val extractedFile1 = File(extractDir, "text_file_1.txt")
        val extractedFile2 = File(extractDir, "text_file_2.txt")

        assertEquals(false, ZipUtil.isZipFile(extractedFile1))

        assertEquals(true, extractedFile1.exists())
        assertEquals("This is text file 1", extractedFile1.readText())

        assertEquals(true, extractedFile2.exists())
        assertEquals("This is text file 2", extractedFile2.readText())
    }

    @Test
    @DisplayName("Test File Full Path Zipping and Unzipping Files")
    fun zipFilePathAndUnzipTest() {
        // Create some test files
        val txtFile1 = File(testDir, "text_filepath_1.txt").apply { writeText("This is text path file 1") }
        val txtFile2 = File(testDir, "text_filepath_2.txt").apply { writeText("This is text path file 2") }

        // Compress test directory
        ZipUtil.zip(listOf(txtFile1, txtFile2), testZipFile3.absolutePath)

        // Ensure ZIP file is created
        assertEquals(true, testZipFile3.exists())
        assert(testZipFile3.length() > 0)

        assertEquals(true, ZipUtil.isZipFile(testZipFile3))

        // Extract ZIP file
        ZipUtil.unzip(testZipFile3.absolutePath, extractDir.absolutePath)

        // Verify extracted files
        val extractedFile1 = File(extractDir, "text_filepath_1.txt")
        val extractedFile2 = File(extractDir, "text_filepath_2.txt")

        assertEquals(false, ZipUtil.isZipFile(extractedFile1))

        assertEquals(true, extractedFile1.exists())
        assertEquals("This is text path file 1", extractedFile1.readText())

        assertEquals(true, extractedFile2.exists())
        assertEquals("This is text path file 2", extractedFile2.readText())
    }

    @Test
    @DisplayName("Test Listing Files Inside ZIP")
    fun listFilesInZipTest() {
        // Create ZIP
        ZipUtil.zip(testDir.absolutePath, testZipFile.absolutePath)

        // List files inside ZIP
        val fileList = ZipUtil.listFilesInZip(testZipFile.absolutePath)

        // Verify file structure inside ZIP
        assertThat(fileList, hasItems("$TEST_DIR/file1.txt", "$TEST_DIR/file2.txt", "$TEST_DIR/subDir/file3.txt"))
    }

    @Test
    @DisplayName("Test Reading Specific File from ZIP")
    fun readTxtFileFromZipTest() {
        // Create ZIP
        ZipUtil.zip(testDir.absolutePath, testZipFile.absolutePath)

        // Read a specific file from the ZIP
        val content = ZipUtil.readTxtFileFromZip(testZipFile.absolutePath, "$TEST_DIR/file1.txt")

        // Verify content is correct
        assertEquals("This is file 1", content)
    }
}
