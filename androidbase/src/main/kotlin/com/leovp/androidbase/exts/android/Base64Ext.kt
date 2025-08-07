@file:Suppress("unused")

package com.leovp.androidbase.exts.android

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import com.leovp.android.utils.API
import com.leovp.image.getBitmap
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Convert String to base64 String
 *
 * @param flag
 * Base64.DEFAULT    -> Default values for encoder/decoder flags.
 * Base64.CRLF       -> Encoder flag bit to indicate lines should be terminated with a CRLF pair instead of just an LF.  Has no effect if {@code NO_WRAP} is specified as well.
 * Base64.NO_PADDING -> Encoder flag bit to omit the padding '=' characters at the end of the output (if any).
 * Base64.NO_WRAP    -> Encoder flag bit to omit all line terminators (i.e., the output will be on one long line).
 * Base64.URL_SAFE   -> Encoder/decoder flag bit to indicate using the "URL and filename safe" variant of Base64
 * (see RFC 3548 section 4) where `-` and `_` are used in place of `+` and '/'.
 */
@JvmOverloads
fun String.toBase64(flag: Int = Base64.DEFAULT): String = Base64.encodeToString(this.toByteArray(), flag)

/**
 * Decode base64 String to String
 *
 * @param flag
 * Base64.DEFAULT    -> Default values for encoder/decoder flags.
 * Base64.CRLF       -> Encoder flag bit to indicate lines should be terminated with a CRLF pair instead of just an LF.  Has no effect if {@code NO_WRAP} is specified as well.
 * Base64.NO_PADDING -> Encoder flag bit to omit the padding '=' characters at the end of the output (if any).
 * Base64.NO_WRAP    -> Encoder flag bit to omit all line terminators (i.e., the output will be on one long line).
 * Base64.URL_SAFE   -> Encoder/decoder flag bit to indicate using the "URL and filename safe" variant of Base64
 * (see RFC 3548 section 4) where `-` and `_` are used in place of `+` and '/'.
 */
@JvmOverloads
fun String.fromBase64(flag: Int = Base64.DEFAULT): String =
    Base64.decode(this, flag).toString(Charset.forName("US-ASCII"))

/**
 * Decode base64 String to ByteArray
 *
 * @param flag
 * Base64.DEFAULT    -> Default values for encoder/decoder flags.
 * Base64.CRLF       -> Encoder flag bit to indicate lines should be terminated with a CRLF pair instead of just an LF.  Has no effect if {@code NO_WRAP} is specified as well.
 * Base64.NO_PADDING -> Encoder flag bit to omit the padding '=' characters at the end of the output (if any).
 * Base64.NO_WRAP    -> Encoder flag bit to omit all line terminators (i.e., the output will be on one long line).
 * Base64.URL_SAFE   -> Encoder/decoder flag bit to indicate using the "URL and filename safe" variant of Base64
 * (see RFC 3548 section 4) where `-` and `_` are used in place of `+` and '/'.
 */
fun String.fromBase64ToByteArray(flag: Int = Base64.DEFAULT): ByteArray = Base64.decode(this, flag)

/** Convert String to URL and Filename safe type base64 String */
val String.toUrlBase64: String
    @RequiresApi(API.O)
    get() = java.util.Base64.getUrlEncoder().encodeToString(this.toByteArray())

/** Convert URL and Filename safe type base64 String to String */
val String.fromUrlBase64: String
    @RequiresApi(API.O)
    get() = java.util.Base64.getUrlDecoder().decode(this).toString()

/** Convert String to Mime type base64 String */
val String.toMimeBase64: String
    @RequiresApi(API.O)
    get() = java.util.Base64.getMimeEncoder().encodeToString(this.toByteArray())

/** Convert Mime type base64 String to String */
val String.fromMimeBase64: String
    @RequiresApi(API.O)
    get() = java.util.Base64.getMimeDecoder().decode(this).toString()

/**
 * Decode base64 ByteArray to ByteArray
 *
 * @param flag
 * Base64.DEFAULT    -> Default values for encoder/decoder flags.
 * Base64.CRLF       -> Encoder flag bit to indicate lines should be terminated with a CRLF pair instead of just an LF.  Has no effect if {@code NO_WRAP} is specified as well.
 * Base64.NO_PADDING -> Encoder flag bit to omit the padding '=' characters at the end of the output (if any).
 * Base64.NO_WRAP    -> Encoder flag bit to omit all line terminators (i.e., the output will be on one long line).
 * Base64.URL_SAFE   -> Encoder/decoder flag bit to indicate using the "URL and filename safe" variant of Base64
 * (see RFC 3548 section 4) where `-` and `_` are used in place of `+` and '/'.
 */
@JvmOverloads
fun ByteArray.fromBase64(flag: Int = Base64.DEFAULT): ByteArray = Base64.decode(this, flag)

/**
 * Convert ByteArray to base64 ByteArray
 *
 * @param flag
 * Base64.DEFAULT    -> Default values for encoder/decoder flags.
 * Base64.CRLF       -> Encoder flag bit to indicate lines should be terminated with a CRLF pair instead of just an LF.  Has no effect if {@code NO_WRAP} is specified as well.
 * Base64.NO_PADDING -> Encoder flag bit to omit the padding '=' characters at the end of the output (if any).
 * Base64.NO_WRAP    -> Encoder flag bit to omit all line terminators (i.e., the output will be on one long line).
 * Base64.URL_SAFE   -> Encoder/decoder flag bit to indicate using the "URL and filename safe" variant of Base64
 * (see RFC 3548 section 4) where `-` and `_` are used in place of `+` and '/'.
 */
@JvmOverloads
fun ByteArray.toBase64ByteArray(flag: Int = Base64.DEFAULT): ByteArray = Base64.encode(this, flag)

/**
 * Convert ByteArray to base64 String
 *
 * @param flag
 * Base64.DEFAULT    -> Default values for encoder/decoder flags.
 * Base64.CRLF       -> Encoder flag bit to indicate lines should be terminated with a CRLF pair instead of just an LF.  Has no effect if {@code NO_WRAP} is specified as well.
 * Base64.NO_PADDING -> Encoder flag bit to omit the padding '=' characters at the end of the output (if any).
 * Base64.NO_WRAP    -> Encoder flag bit to omit all line terminators (i.e., the output will be on one long line).
 * Base64.URL_SAFE   -> Encoder/decoder flag bit to indicate using the "URL and filename safe" variant of Base64
 * (see RFC 3548 section 4) where `-` and `_` are used in place of `+` and '/'.
 */
@JvmOverloads
fun ByteArray.toBase64(flag: Int = Base64.DEFAULT): String = Base64.encodeToString(this, flag)

/** Convert ByteArray to base64 ByteArray */
val ByteArray.toBase64: ByteArray
    @RequiresApi(API.O)
    get() = java.util.Base64.getEncoder().encode(this)

/** Decode base64 ByteArray to ByteArray */
val ByteArray.fromBase64: ByteArray
    @RequiresApi(API.O)
    get() = java.util.Base64.getDecoder().decode(this)

/** Convert ByteArray to URL and Filename safe type base64 ByteArray */
val ByteArray.toUrlBase64ByteArray: ByteArray
    @RequiresApi(API.O)
    get() = java.util.Base64.getUrlEncoder().encode(this)

/** Convert ByteArray to URL and Filename safe type base64 String */
val ByteArray.toUrlBase64: String
    @RequiresApi(API.O)
    get() = java.util.Base64.getUrlEncoder().encodeToString(this)

/** Decode URL and Filename safe type base64 ByteArray to ByteArray */
val ByteArray.fromUrlBase64: ByteArray
    @RequiresApi(API.O)
    get() = java.util.Base64.getUrlDecoder().decode(this)

/** Convert ByteArray to Mime type base64 ByteArray */
val ByteArray.toMimeBase64ByteArray: ByteArray
    @RequiresApi(API.O)
    get() = java.util.Base64.getMimeEncoder().encode(this)

/** Convert ByteArray to Mime type base64 String */
val ByteArray.toMimeBase64: String
    @RequiresApi(API.O)
    get() = java.util.Base64.getMimeEncoder().encodeToString(this)

/** Decode Mime type base64 ByteArray to ByteArray */
val ByteArray.fromMimeBase64: ByteArray
    @RequiresApi(API.O)
    get() = java.util.Base64.getMimeDecoder().decode(this)

/**
 * Convert Drawable to base64 String.
 */
fun Drawable.toBase64(imgType: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): String? {
    val bmp = this.getBitmap()
    return if (bmp == null) {
        null
    } else {
        val compressedBmpOS = ByteArrayOutputStream()
        bmp.compress(imgType, 100, compressedBmpOS)
        compressedBmpOS.toByteArray().toBase64()
    }
}

/**
 * Decode drawable base64 String to Drawable.
 */
fun String.fromBase64ToDrawable(resources: Resources, flag: Int = Base64.DEFAULT): Drawable? = runCatching {
    val drawableBytes = this.fromBase64ToByteArray(flag)
//    BitmapDrawable(resources, BitmapFactory.decodeByteArray(drawableBytes, 0, drawableBytes.size))
    BitmapFactory.decodeByteArray(drawableBytes, 0, drawableBytes.size).toDrawable(resources)
}.getOrNull()
