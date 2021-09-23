package com.leovp.androidbase.utils.system

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

@SuppressLint("ObsoleteSdkInt")
object API {
    /** Android 4.2.x */
    const val J_MR1 = Build.VERSION_CODES.JELLY_BEAN_MR1

    /** Android >= 4.2 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    val ABOVE_J_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1

    /** Android 4.3.x */
    const val J_MR2 = Build.VERSION_CODES.JELLY_BEAN_MR2

    /** Android >= 4.3 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    val ABOVE_J_MR2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1

    /** Android 4.4 ~ 4.4.4 */
    const val K = Build.VERSION_CODES.KITKAT

    /** Android >= 4.4 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.KITKAT)
    val ABOVE_K = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    /** Android 5.0 */
    const val L = Build.VERSION_CODES.LOLLIPOP

    /** Android >= 5.0 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
    val ABOVE_L = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    /** Android 5.1 */
    const val L_MR1 = Build.VERSION_CODES.LOLLIPOP_MR1

    /** Android >= 5.1 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    val ABOVE_L_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1

    /** Android 6.0 */
    const val M = Build.VERSION_CODES.M

    /** Android >= 6.0 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
    val ABOVE_M = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /** Android 7.0 */
    const val N = Build.VERSION_CODES.N

    /** Android >= 7.0 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    val ABOVE_N = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /** Android 7.1 */
    const val N_MR1 = Build.VERSION_CODES.N_MR1

    /** Android >= 7.1 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N_MR1)
    val ABOVE_N_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1

    /** Android 8.0 */
    const val O = Build.VERSION_CODES.O

    /** Android >= 8.0 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val ABOVE_O = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /** Android 8.1 */
    const val O_MR1 = Build.VERSION_CODES.O_MR1

    /** Android >= 8.1 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O_MR1)
    val ABOVE_O_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

    /** Android 9 */
    const val P = Build.VERSION_CODES.P

    /** Android >= 9 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val ABOVE_P = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Android 10 */
    const val Q = Build.VERSION_CODES.Q

    /** Android >= 10 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val ABOVE_Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Android 11 */
    const val R = Build.VERSION_CODES.R

    /** Android >= 11 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val ABOVE_R = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    val isOsVersionHigherThenGingerbread: Boolean
        get() = !(Build.VERSION.RELEASE.startsWith("1.") || Build.VERSION.RELEASE.startsWith("2.0")
                || Build.VERSION.RELEASE.startsWith("2.1") || Build.VERSION.RELEASE.startsWith("2.2")
                || Build.VERSION.RELEASE.startsWith("2.3"))
}