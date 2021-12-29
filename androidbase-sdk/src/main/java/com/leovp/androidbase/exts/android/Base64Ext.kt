package com.leovp.androidbase.exts.android

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.annotation.RequiresApi
import com.leovp.lib_common_android.utils.API
import com.leovp.lib_image.getBitmap
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
 * Convert base64 ByteArray to String
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
fun String.fromBase64(flag: Int = Base64.DEFAULT): String = Base64.decode(this, flag).toString(Charset.forName("US-ASCII"))

fun String.fromBase64ToByteArray(flag: Int = Base64.DEFAULT): ByteArray = Base64.decode(this, flag)

/** Convert String to URL and Filename safe type base64 String */
val String.toUrlBase64: String @RequiresApi(API.O) get() = java.util.Base64.getUrlEncoder().encodeToString(this.toByteArray())

/** Convert URL and Filename safe type base64 String to String */
val String.fromUrlBase64: String @RequiresApi(API.O) get() = java.util.Base64.getUrlDecoder().decode(this).toString()

/** Convert String to Mime type base64 String */
val String.toMimeBase64: String @RequiresApi(API.O) get() = java.util.Base64.getMimeEncoder().encodeToString(this.toByteArray())

/** Convert Mime type base64 String to String */
val String.fromMimeBase64: String @RequiresApi(API.O) get() = java.util.Base64.getMimeDecoder().decode(this).toString()

/**
 * Convert base64 ByteArray to ByteArray
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
fun ByteArray.toBase64(flag: Int = Base64.DEFAULT): String = Base64.encodeToString(this, flag)

/** Convert ByteArray to base64 ByteArray */
val ByteArray.toBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getEncoder().encode(this)

/** Convert base64 ByteArray to ByteArray */
val ByteArray.fromBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getDecoder().decode(this)

/** Convert ByteArray to URL and Filename safe type base64 ByteArray */
val ByteArray.toUrlBase64ByteArray: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getUrlEncoder().encode(this)
val ByteArray.toUrlBase64: String @RequiresApi(API.O) get() = java.util.Base64.getUrlEncoder().encodeToString(this)

/** Convert URL and Filename safe type base64 ByteArray to ByteArray */
val ByteArray.fromUrlBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getUrlDecoder().decode(this)

/** Convert ByteArray to Mime type base64 ByteArray */
val ByteArray.toMimeBase64ByteArray: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getMimeEncoder().encode(this)
val ByteArray.toMimeBase64: String @RequiresApi(API.O) get() = java.util.Base64.getMimeEncoder().encodeToString(this)

/** Convert Mime type base64 ByteArray to ByteArray */
val ByteArray.fromMimeBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getMimeDecoder().decode(this)

fun Drawable.toBase64(imgType: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): String? {
    val bmp = this.getBitmap()
    return if (bmp == null) null
    else {
        val compressedBmpOS = ByteArrayOutputStream()
        bmp.compress(imgType, 100, compressedBmpOS)
        compressedBmpOS.toByteArray().toBase64()
    }
}

fun String.fromBase64ToDrawable(resources: Resources, flag: Int = Base64.DEFAULT): Drawable? {
    return runCatching {
        val drawableBytes = this.fromBase64ToByteArray(flag)
        BitmapDrawable(resources, BitmapFactory.decodeByteArray(drawableBytes, 0, drawableBytes.size))
    }.getOrNull()
}