package com.leovp.androidbase.exts.kotlin

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar.*

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

@SuppressLint("ConstantLocale")
internal val sdf7 = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

@SuppressLint("ConstantLocale")
internal val sdf8 = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault())

@SuppressLint("ConstantLocale")
internal val sdf9 = SimpleDateFormat("HH:mm", Locale.getDefault())

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
 * Pattern: yyyy-MM-dd HH:mm
 */
fun Date.formatToShortDateTimeDefaults(): String = sdf7.format(this)

/**
 * Pattern: yyyyMMddHHmmss
 */
fun Date.formatToTruncatedDateTime(): String = sdf2.format(this)

/**
 * Pattern: yyyyMMddHHmm
 */
fun Date.formatToTruncatedShortDateTime(): String = sdf8.format(this)

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
 * Pattern: HH:mm
 */
fun Date.formatToShortTimeDefaults(): String {
    return sdf9.format(this)
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

/**
 * Calendar Extension
 *
 * z-时区
 * G 年代[公元/公元前]
 * a-时段[上午/下午]
 * y-年/M-月/d-日期/E-星期
 * k-时[24小时制(1-24)]/K-时[12小时制(0~11)]    Tips.k中24为当天0时/K中0为当天上午12时
 * h-时[12小时制(1-11)]/H-时[24小时制(0-23)]
 * m-分/s-秒/S-毫秒
 * D-一年中的第几天
 * F-一月中第几个星期几
 * w-一年中第几个星期   Tips.1.星期天为一周的开始,不随FirstDayOfWeek变化，根据FirstDayOfWeek变化的周次使用Calendar.get(Calendar.WEEK_OF_YEAR)  2.无论w或者Calendar.get(Calendar.WEEK_OF_YEAR)，一年的第53周默认都算为次年第一周
 * W-一月中第几个星期   Tips.星期天为一周的开始,不随FirstDayOfWeek变化，根据FirstDayOfWeek变化的周次使用Calendar.get(Calendar.WEEK_OF_MONTH)
 */

/**
 * Coordinated Universal Time
 */
val Calendar.utc: String
    get() {
        add(MILLISECOND, -(get(ZONE_OFFSET) + get(DST_OFFSET)))
        return SimpleDateFormat("yyyy'-'MM'-'dd'T'kk':'mm':'ss'Z'", Locale.getDefault()).format(time)
    }

/** Get formatted date */
@JvmOverloads
fun Calendar.date(pattern: String = "yyyy-MM-dd HH:mm:ss", locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat(pattern, locale).format(time)

/** Get year */
var Calendar.year
    get() = get(YEAR)
    set(value) = set(YEAR, value)

/** Get month(January(1) to December(12)) */
var Calendar.month
    get() = get(MONTH) + 1
    set(value) = set(MONTH, value - 1)

/** Get day */
var Calendar.day
    get() = get(DAY_OF_MONTH)
    set(value) = set(DAY_OF_MONTH, value)

/** Get hour */
var Calendar.hour
    get() = get(HOUR_OF_DAY)
    set(value) = set(HOUR_OF_DAY, value)

/** Get minute */
var Calendar.minute
    get() = get(MINUTE)
    set(value) = set(MINUTE, value)

/** Get second */
var Calendar.second
    get() = get(SECOND)
    set(value) = set(SECOND, value)

/** Get millisecond */
var Calendar.millisecond
    get() = get(MILLISECOND)
    set(value) = set(MILLISECOND, value)

/**
 * Get week number
 * [Calendar.SUNDAY]    -> Sunday    -> 1
 * [Calendar.MONDAY]    -> Monday    -> 2
 * [Calendar.TUESDAY]   -> Tuesday   -> 3
 * [Calendar.WEDNESDAY] -> Wednesday -> 4
 * [Calendar.THURSDAY]  -> Thursday  -> 5
 * [Calendar.FRIDAY]    -> Friday    -> 6
 * [Calendar.SATURDAY]  -> Saturday  -> 7
 **/
val Calendar.week
    get() = get(DAY_OF_WEEK)

/** Get week name */
@JvmOverloads
fun Calendar.week(locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("E", locale).format(time)

/**
 * Set the Calendar to the date that first day of the week
 * @param firstDayOfWeek Week number of the first day of the week
 */
@JvmOverloads
fun Calendar.firstOfWeek(firstDayOfWeek: Int = this.firstDayOfWeek): Calendar {
    val now = get(DAY_OF_WEEK)
    add(DATE, firstDayOfWeek - now - (if (now < firstDayOfWeek) 7 else 0))
    return this
}

/**
 * Set the Calendar to the date that last day of the week
 * @param firstDayOfWeek Week number of the first day of the week
 */
@JvmOverloads
fun Calendar.lastOfWeek(firstDayOfWeek: Int = this.firstDayOfWeek): Calendar {
    val now = get(DAY_OF_WEEK)
    add(DATE, firstDayOfWeek - now + (if (now < firstDayOfWeek) -1 else +6))
    return this
}

/** Get week of month */
val Calendar.weekOfMonth
    get() = this.get(WEEK_OF_MONTH)

/** Get week of year */
val Calendar.weekOfYear
    get() = if (get(WEEK_OF_YEAR) == 1 && month == 12) 53 else get(WEEK_OF_YEAR)