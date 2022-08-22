@file:Suppress("unused")

package com.leovp.androidbase.utils.cipher

import java.security.Security

/**
 * Author: Michael Leo
 * Date: 2021/9/22 16:13
 */
object CipherUtil {
    val allSupportedCipher: Set<String> = Security.getAlgorithms("Cipher")
}
