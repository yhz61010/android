@file:Suppress("unused")

package com.leovp.lib_common_android.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object API {
    /** Android 5.1 */
    const val L_MR1 = Build.VERSION_CODES.LOLLIPOP_MR1

    /** Android 6.0 */
    const val M = Build.VERSION_CODES.M

    /** Android 7.0 */
    const val N = Build.VERSION_CODES.N

    /** Android 7.1 */
    const val N_MR1 = Build.VERSION_CODES.N_MR1

    /** Android 8.0 */
    const val O = Build.VERSION_CODES.O

    /** Android 8.1 */
    const val O_MR1 = Build.VERSION_CODES.O_MR1

    /** Android 9 */
    const val P = Build.VERSION_CODES.P

    /** Android 10 */
    const val Q = Build.VERSION_CODES.Q

    /** Android 11 */
    const val R = Build.VERSION_CODES.R

    /** Android 12 */
    const val S = Build.VERSION_CODES.S

    /** Android 12L */
    const val S_V2 = Build.VERSION_CODES.S_V2

    /** Android 13 */
    const val TIRAMISU = Build.VERSION_CODES.TIRAMISU

    // ==============================

    /** Android >= 5.1 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    val ABOVE_L_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1

    /** Android >= 6.0 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
    val ABOVE_M = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /** Android >= 7.0 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    val ABOVE_N = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /** Android >= 7.1 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N_MR1)
    val ABOVE_N_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1

    /** Android >= 8.0 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val ABOVE_O = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /** Android >= 8.1 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O_MR1)
    val ABOVE_O_MR1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

    /** Android >= 9 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val ABOVE_P = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Android >= 10 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val ABOVE_Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Android >= 11 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val ABOVE_R = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Android >= 12 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val ABOVE_S = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Android >= 12L */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S_V2)
    val ABOVE_S_V2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2

    /** Android >= 13 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val ABOVE_TIRAMISU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
