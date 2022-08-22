package com.leovp.aidl.client.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Author: Michael Leo
 * Date: 21-3-24 下午6:11
 */
@Parcelize
class LocalLog(val type: String, val message: String) : Parcelable
