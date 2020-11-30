package com.leovp.androidbase.exts.android

import android.util.Base64
import androidx.annotation.RequiresApi
import com.leovp.androidbase.exts.kotlin.toUTF8
import com.leovp.androidbase.utils.system.API

/** Convert String to base64 ByteArray(Default) */
val String.toBase64: String get() = Base64.encodeToString(this.toByteArray(), Base64.DEFAULT)

/** Convert base64 ByteArray to String(Default) */
val String.fromBase64: String get() = Base64.decode(this, Base64.DEFAULT).toUTF8

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
fun String.fromBase64(flag: Int = Base64.DEFAULT): String = Base64.decode(this, flag).toUTF8

/** Convert String to URL and Filename safe type base64 String */
val String.toUrlBase64: String @RequiresApi(API.O) get() = java.util.Base64.getUrlEncoder().encodeToString(this.toByteArray())

/** Convert URL and Filename safe type base64 String to String */
val String.fromUrlBase64: String @RequiresApi(API.O) get() = java.util.Base64.getUrlDecoder().decode(this).toUTF8

/** Convert String to Mime type base64 String */
val String.toMimeBase64: String @RequiresApi(API.O) get() = java.util.Base64.getMimeEncoder().encodeToString(this.toByteArray())

/** Convert Mime type base64 String to String */
val String.fromMimeBase64: String @RequiresApi(API.O) get() = java.util.Base64.getMimeDecoder().decode(this).toUTF8

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
fun ByteArray.toBase64(flag: Int = Base64.DEFAULT): ByteArray = Base64.encode(this, flag)

/** Convert ByteArray to base64 ByteArray */
val ByteArray.toBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getEncoder().encode(this)

/** Convert base64 ByteArray to ByteArray */
val ByteArray.fromBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getDecoder().decode(this)

/** Convert ByteArray to URL and Filename safe type base64 ByteArray */
val ByteArray.toUrlBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getUrlEncoder().encode(this)

/** Convert URL and Filename safe type base64 ByteArray to ByteArray */
val ByteArray.fromUrlBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getUrlDecoder().decode(this)

/** Convert ByteArray to Mime type base64 ByteArray */
val ByteArray.toMimeBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getMimeEncoder().encode(this)

/** Convert Mime type base64 ByteArray to ByteArray */
val ByteArray.fromMimeBase64: ByteArray @RequiresApi(API.O) get() = java.util.Base64.getMimeDecoder().decode(this)