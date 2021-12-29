@file:Suppress("MemberVisibilityCanBePrivate")

package com.leovp.lib_common_android.utils.shell

import android.annotation.TargetApi
import android.os.Build
import androidx.annotation.Keep
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Shell Utils
 */
@Suppress("WeakerAccess", "unused", "UNUSED_PARAMETER")
object ShellUtil {
    private const val SH_BIN = "sh"
    private const val SU_BIN = "su"
    private const val MOUNT_BIN = "mount"
    private const val SYSTEM_MOUNT_POINT = "/system"
    private const val CMD_PS = "ps"
    private const val CMD_MOUNT = "/system/bin/mount"
    private const val CMD_EXIT = "exit"
    private val LINE_SEP = System.getProperty("line.separator")!!

    /**
     * check whether has root permission
     *
     * @return root for true otherwise false
     */
    fun checkRootPermission(): Boolean {
        return execCmd("echo root", isRoot = true, isNeedResultMsg = false).result == 0
    }

    fun execCmd(command: String, isRoot: Boolean = false, isNeedResultMsg: Boolean = true): CommandResult {
        return execCmd(listOf(command), isRoot, isNeedResultMsg)
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun execCmd(commands: List<String>, isRoot: Boolean = false, isNeedResultMsg: Boolean = true): CommandResult {
        var result = -1
        if (commands.isEmpty()) {
            return CommandResult(result, "", "")
        }
        var successMsg: StringBuilder? = null
        var errorMsg: StringBuilder? = null
        val process: Process
        try {
            process = Runtime.getRuntime().exec(if (isRoot) SU_BIN else SH_BIN)
        } catch (e: Exception) {
            e.printStackTrace()
            return CommandResult(
                result,
                "",
                e.toString()
            )
        }

        DataOutputStream(process.outputStream).use {
            for (command in commands) {
                it.write(command.toByteArray())
                it.writeBytes(LINE_SEP)
                it.flush()
            }
            it.writeBytes(CMD_EXIT + LINE_SEP)
            it.flush()
        }

        result = process.waitFor()
        if (isNeedResultMsg) {
            successMsg = StringBuilder()
            errorMsg = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { successResult ->
                var line: String?
                if (successResult.readLine().also { line = it } != null) {
                    successMsg.append(line)
                    while (successResult.readLine().also { line = it } != null) {
                        successMsg.append(LINE_SEP).append(line)
                    }
                }
            }

            BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).use { errorResult ->
                var line: String?
                if (errorResult.readLine().also { line = it } != null) {
                    errorMsg.append(line)
                    while (errorResult.readLine().also { line = it } != null) {
                        errorMsg.append(LINE_SEP).append(line)
                    }
                }
            }
        }

        process.destroy()

        return CommandResult(
            result,
            successMsg.toString(),
            errorMsg.toString()
        )
    }

    fun forceStop(pkgName: String) {
        execCmd("am force-stop $pkgName", true)
    }

    // ========================================================================
    fun getProcessesList(isRoot: Boolean = false): List<LinuxProcess> {
        val processesListString = execCmd(CMD_PS, isRoot).successMsg
        val reader = BufferedReader(StringReader(processesListString), 256 shl 1024)
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
            e.printStackTrace()
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
     * Look for all processes by process name
     *
     * @param isRoot Run as root
     * @param name   Specific process named
     * @return Return found process list or return empty list.
     */
    fun findProcessByName(name: String, isRoot: Boolean = false): List<LinuxProcess> {
        return getProcessesList(isRoot)
            .filter { process -> name.equals(process.name, true) }
            .toCollection(arrayListOf())
    }

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
            execCmd(String.format("kill -9 %s", pid), isRoot)
        }
    }

    // unverified
    fun killSelf() {
        android.os.Process.killProcess(android.os.Process.myPid())
        killProcessByPid(android.os.Process.myPid(), false)
    }

    // unverified
    fun uninstallApk(pkgName: String) {
        execCmd(String.format("pm uninstall %s", pkgName), true)
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
            e.printStackTrace()
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
                Nexus 6(root): /dev/block/mmcblk0p41 on /system type ext4 (ro,seclabel,relatime,data=ordered)
                */
                val params = line!!.split(" ".toRegex()).toTypedArray()
                // cmd:
                // mount -o rw,remount /system
                // or
                // mount -o remount,rw -t yaffs2 /dev/block/mmcblk1p10 /system
                if (params.size > 2) {
                    val remountCommand = String.format(
                        "%s -o %s,remount -t %s %s %s", CMD_MOUNT,
                        if (write) "rw" else "ro", params[2], params[0], params[1]
                    )
                    execCmd(remountCommand, true)
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}

@Keep
data class CommandResult(
    val result: Int,
    val successMsg: String,
    val errorMsg: String
)