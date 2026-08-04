@file:Suppress("MemberVisibilityCanBePrivate")

package com.leovp.android.utils.shell

import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.VisibleForTesting
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Shell Utils
 */
@Suppress("WeakerAccess", "unused", "UNUSED_PARAMETER")
object ShellUtil {
    private const val TAG = "ShellUtil"

    private const val SH_BIN = "sh"
    private const val SU_BIN = "su"
    private const val MOUNT_BIN = "mount"
    private const val SYSTEM_MOUNT_POINT = "/system"
    private const val CMD_PS = "ps"
    private const val CMD_MOUNT = "/system/bin/mount"
    private const val CMD_EXIT = "exit"
    private val LINE_SEP = System.lineSeparator()

    private val PACKAGE_NAME_REGEX =
        Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    /**
     * Validates an Android package name before it is interpolated into a shell command, closing the
     * command-injection vector in [forceStop] / [uninstallApk] (remediation C2). Rejects anything
     * containing shell metacharacters or otherwise not matching the package-name grammar.
     */
    @VisibleForTesting
    internal fun requireValidPackage(pkgName: String): String {
        require(PACKAGE_NAME_REGEX.matches(pkgName)) { "Illegal package name: $pkgName" }
        return pkgName
    }

    /**
     * Check root permission.
     *
     * @return root for true otherwise false
     */
    fun checkRootPermission(): Boolean = execCmd(
        "echo root",
        isRoot = true,
        isNeedResultMsg = false
    ).result == 0

    /**
     * Executes a raw shell command. The command string is interpreted by a real shell (`sh`/`su`),
     * so shell metacharacters are live. The caller MUST guarantee the command is trusted / already
     * validated — never interpolate unsanitized external input (see [requireValidPackage]). This is
     * a low-level primitive by design.
     */
    fun execCmd(
        command: String,
        isRoot: Boolean = false,
        isNeedResultMsg: Boolean = true
    ): CommandResult = execCmd(listOf(command), isRoot, isNeedResultMsg)

    fun execCmd(
        commands: List<String>,
        isRoot: Boolean = false,
        isNeedResultMsg: Boolean = true
    ): CommandResult {
        var result = -1
        if (commands.isEmpty()) {
            return CommandResult(result, "", "Command is empty.")
        }
        // Check root environment if available.
        val pb = ProcessBuilder(if (isRoot) SU_BIN else SH_BIN)
        val process = runCatching { pb.start() }.getOrElse { e ->
            return CommandResult(result, "", e.toString())
        }

        // StandardCharsets.UTF_8
        OutputStreamWriter(process.outputStream, "UTF-8").use { osw ->
            for (command in commands) {
                osw.write(command)
                osw.appendLine()
            }
            osw.write(CMD_EXIT)
            osw.appendLine()
            osw.flush()
        }

        // Drain stdout and stderr concurrently BEFORE reaping the exit code. A child that fills its
        // output pipe blocks on write; calling waitFor() first (or reading the two streams
        // sequentially) would let neither side progress and the call would deadlock (remediation H1).
        val (successMsg, errorMsg) = drainStreams(process.inputStream, process.errorStream)
        result = process.waitFor()
        // process.destroy()

        return CommandResult(
            result,
            if (isNeedResultMsg) successMsg else "",
            if (isNeedResultMsg) errorMsg else ""
        )
    }

    /**
     * Reads [stdout] and [stderr] to EOF on two separate threads so a child process that fills one
     * pipe cannot block the other reader (or [Process.waitFor]). Returns the fully-drained
     * `(stdout, stderr)` pair once both streams are exhausted (remediation H1).
     */
    @VisibleForTesting
    internal fun drainStreams(stdout: InputStream, stderr: InputStream): Pair<String, String> {
        val outSink = StringBuilder()
        val errSink = StringBuilder()
        val outThread = readStreamAsync(stdout, outSink)
        val errThread = readStreamAsync(stderr, errSink)
        // join() establishes happens-before, so reading the sinks here is safe.
        outThread.join()
        errThread.join()
        return outSink.toString() to errSink.toString()
    }

    private fun readStreamAsync(stream: InputStream, sink: StringBuilder): Thread =
        Thread {
            try {
                stream.bufferedReader(StandardCharsets.UTF_8).use { br ->
                    br.useLines { seq -> sink.append(seq.toList().joinToString(LINE_SEP)) }
                }
            } catch (e: IOException) {
                Log.e(TAG, "readStream failed: ${e.message}")
            }
        }.apply { start() }

    fun forceStop(pkgName: String) {
        execCmd("am force-stop ${requireValidPackage(pkgName)}", true)
    }

    // ========================================================================
    fun getProcessesList(isRoot: Boolean = false): List<LinuxProcess> {
        val processesListString = execCmd(CMD_PS, isRoot).successMsg
        val reader = BufferedReader(StringReader(processesListString), 256 shl 10)
        val processes: MutableList<LinuxProcess> = ArrayList()
        try {
            var info: LinuxProcess
            var line: String
            while (reader.readLine().also { line = it } != null) {
                val tokens = line.split("\\s+".toRegex()).toTypedArray()
                if (tokens.size > 8) {
                    info = LinuxProcess(Integer.valueOf(tokens[1]))
                    info.user = tokens[0]
                    info.ppid = Integer.valueOf(tokens[2])
                    info.vsize = tokens[3]
                    info.rss = tokens[4]
                    info.wchan = tokens[5]
                    info.pc = tokens[6]
                    info.status = tokens[7]
                    info.name = tokens[8]
                    processes.add(info)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "getProcessesList exception. Message: ${e.message}")
        }
        return processes
    }

    fun findProcessByPid(pid: Int, isRoot: Boolean = false): LinuxProcess? {
        val processes = getProcessesList(isRoot)
        for (process in processes) {
            if (process.pid == pid) {
                return process
            }
        }
        return null
    }

    /**
     * Look for all processes by process name.
     *
     * @param isRoot Run as root
     * @param name   Specific process named
     * @return Return found process list or return empty list.
     */
    fun findProcessByName(name: String, isRoot: Boolean = false): List<LinuxProcess> =
        getProcessesList(isRoot)
            .filter { process -> name.equals(process.name, true) }
            .toCollection(arrayListOf())

    fun killProcessByName(processName: String, isRoot: Boolean = false) {
        // As of API level 24: java.lang.Iterable#forEach
        //        findProcessByName(
        //            processName,
        //            isRoot
        //        ).forEach { (pid) -> killProcessByPid(pid, isRoot) }
        val processList = findProcessByName(
            processName,
            isRoot
        )
        for (process in processList) {
            killProcessByPid(process.pid, isRoot)
        }
    }

    fun killProcessByPid(pid: Int, isRoot: Boolean = false) {
        val process = findProcessByPid(pid, isRoot)
        if (process != null) {
            execCmd("kill -9 $pid", isRoot)
        }
    }

    // unverified
    fun killSelf() {
        android.os.Process.killProcess(android.os.Process.myPid())
        killProcessByPid(android.os.Process.myPid(), false)
    }

    // unverified
    fun uninstallApk(pkgName: String) {
        execCmd("pm uninstall ${requireValidPackage(pkgName)}", true)
    }

    // unverified
    fun isProcessRunning(processName: String, isRoot: Boolean = false): Boolean {
        val processesListString = execCmd(CMD_PS, isRoot).successMsg
        val reader = BufferedReader(StringReader(processesListString), 256 shl 10)
        try {
            var line: String
            while (reader.readLine().also { line = it } != null) {
                if (line.contains(processName)) {
                    return true
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "execCmd exception. Message: ${e.message}")
            return false
        }
        return false
    }

    // unverified
    fun remountFileSystem(write: Boolean) {
        try {
            val cmdResult = execCmd(CMD_MOUNT, true).successMsg
            val reader = BufferedReader(StringReader(cmdResult), 256 shl 10)
            var line: String?
            // Find /system line
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains(" /system")) break
            }

            // Check file system info
            if (line != null) {
                /*
                Samsung 9350: /dev/block/dm-0 on /system type ext4 (ro,seclabel,relatime,norecovery)
                Nexus 6(root): /dev/block/mmcblk0p41 on /system type ext4 (
                    ro,
                    seclabel,
                    relatime,
                    data=ordered
                )
                 */
                val params = line.split(" ".toRegex()).toTypedArray()
                // cmd:
                // mount -o rw,remount /system
                // or
                // mount -o remount,rw -t yaffs2 /dev/block/mmcblk1p10 /system
                if (params.size > 2) {
                    val remountCommand = String.format(
                        Locale.ENGLISH,
                        "%s -o %s,remount -t %s %s %s",
                        CMD_MOUNT,
                        if (write) "rw" else "ro",
                        params[2],
                        params[0],
                        params[1]
                    )
                    execCmd(remountCommand, true)
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "remountFileSystem exception. Message: ${e.message}")
        }
    }
}

@Keep
data class CommandResult(val result: Int, val successMsg: String, val errorMsg: String)
