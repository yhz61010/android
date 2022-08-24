package com.leovp.android.utils.shell

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 19-11-29 下午4:24
 */
@Keep
data class LinuxProcess(val pid: Int) {
    var user: String? = null
    var ppid = 0
    var vsize: String? = null
    var rss: String? = null
    var wchan: String? = null
    var pc: String? = null
    var status: String? = null
    var name: String? = null
}
