package com.ho1ho.androidbase.annotations

/**
 * Author: Michael Leo
 * Date: 20-6-8 下午8:35
 */
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION
)
@Suppress("unused")
@Retention(AnnotationRetention.SOURCE)
annotation class NotImplemented(val str: String)