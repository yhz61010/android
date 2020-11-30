package com.leovp.androidbase.utils.system

import android.annotation.SuppressLint
import android.os.Build

@SuppressLint("ObsoleteSdkInt")
object API {
    const val J_MR1 = Build.VERSION_CODES.JELLY_BEAN_MR1
    val ABOVE_J_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
    const val J_MR2 = Build.VERSION_CODES.JELLY_BEAN_MR2
    val ABOVE_J_MR2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
    const val K = Build.VERSION_CODES.KITKAT
    val ABOVE_K = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    const val L = Build.VERSION_CODES.LOLLIPOP
    val ABOVE_L = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    const val L_MR1 = Build.VERSION_CODES.LOLLIPOP_MR1
    val ABOVE_L_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
    const val M = Build.VERSION_CODES.M
    val ABOVE_M = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    const val N = Build.VERSION_CODES.N
    val ABOVE_N = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    const val N_MR1 = Build.VERSION_CODES.N_MR1
    val ABOVE_N_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
    const val O = Build.VERSION_CODES.O
    val ABOVE_O = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    const val P = Build.VERSION_CODES.P
    val ABOVE_P = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    const val Q = Build.VERSION_CODES.Q
    val ABOVE_Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val isOsVersionHigherThenGingerbread: Boolean
        get() = !(Build.VERSION.RELEASE.startsWith("1.") || Build.VERSION.RELEASE.startsWith("2.0")
                || Build.VERSION.RELEASE.startsWith("2.1") || Build.VERSION.RELEASE.startsWith("2.2")
                || Build.VERSION.RELEASE.startsWith("2.3"))
}