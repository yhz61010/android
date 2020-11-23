package com.leovp.androidbase.exts

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("ConstantLocale")
internal val sdf1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

@SuppressLint("ConstantLocale")
internal val sdf2 = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

@SuppressLint("ConstantLocale")
internal val sdf3 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@SuppressLint("ConstantLocale")
internal val sdf4 = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@SuppressLint("ConstantLocale")
internal val sdf5 = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

@SuppressLint("ConstantLocale")
internal val sdf6 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

/**
 * Author: Michael Leo
 * Date: 20-8-17 下午4:06
 */

fun Date.getToday(format: String) = SimpleDateFormat(format, Locale.getDefault()).format(this)

/**
 * Pattern: yyyy-MM-dd HH:mm:ss
 */
fun Date.formatToDateTimeDefaults(): String = sdf1.format(this)

/**
 * Pattern: yyyyMMddHHmmss
 */
fun Date.formatToTruncatedDateTime(): String = sdf2.format(this)

/**
 * Pattern: yyyy-MM-dd
 */
fun Date.formatToServerDateDefaults(): String = sdf3.format(this)

/**
 * Pattern: HH:mm:ss
 */
fun Date.formatToTimeDefaults(): String {
    return sdf4.format(this)
}

/**
 * Pattern: dd/MM/yyyy HH:mm:ss
 */
fun Date.formatToViewDateTimeDefaults(): String {
    return sdf5.format(this)
}

/**
 * Pattern: dd/MM/yyyy
 */
fun Date.formatToViewDateDefaults(): String {
    return sdf6.format(this)
}

/**
 * Add field date to current date
 */
fun Date.add(field: Int, amount: Int): Date {
    Calendar.getInstance().apply {
        time = this@add
        add(field, amount)
        return time
    }
}

fun Date.addYears(years: Int): Date {
    return add(Calendar.YEAR, years)
}

fun Date.addMonths(months: Int): Date {
    return add(Calendar.MONTH, months)
}

fun Date.addDays(days: Int): Date {
    return add(Calendar.DAY_OF_MONTH, days)
}

fun Date.addHours(hours: Int): Date {
    return add(Calendar.HOUR_OF_DAY, hours)
}

fun Date.addMinutes(minutes: Int): Date {
    return add(Calendar.MINUTE, minutes)
}

fun Date.addSeconds(seconds: Int): Date {
    return add(Calendar.SECOND, seconds)
}