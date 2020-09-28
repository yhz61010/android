package com.leovp.androidbase.utils.system

import android.annotation.TargetApi
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Author: Michael Leo
 * Date: 20-7-21 下午4:03
 */
object ClipboardUtil {
    fun getClipboardText(activity: Activity, f: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getTextFromClipboardForAndroidQ(activity, f)
        } else {
            f.invoke(getTextFromClipboard(activity))
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun getTextFromClipboardForAndroidQ(activity: Activity, f: (String) -> Unit) {
        val runnable = Runnable {
            try {
                val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (!clipboardManager.hasPrimaryClip()) {
                    f.invoke("")
                    return@Runnable
                }
                val clipData = clipboardManager.primaryClip
                if (null == clipData || clipData.itemCount < 1) {
                    f.invoke("")
                    return@Runnable
                }
                f.invoke(runCatching { clipData.getItemAt(0).text.toString() }.getOrDefault(""))
                return@Runnable
            } catch (e: Exception) {
                f.invoke("")
                return@Runnable
            }
        }
        activity.window?.decorView?.postDelayed(runnable, 1000) ?: f.invoke("")
    }

    fun setTextToClipboard(ctx: Context, text: String) {
        val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
    }

    private fun getTextFromClipboard(activity: Activity): String {
        val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboardManager.hasPrimaryClip()) {
            return ""
        }
        val clipData = clipboardManager.primaryClip
        if (null == clipData || clipData.itemCount < 1) {
            return ""
        }
        return runCatching { clipData.getItemAt(0).text.toString() }.getOrDefault("")
    }

    fun clear(ctx: Context) {
        val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        } else {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, ""))
        }
    }
}