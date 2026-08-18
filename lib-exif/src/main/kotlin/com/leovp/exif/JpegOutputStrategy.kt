package com.leovp.exif

import androidx.annotation.Keep

/** Controls whether JPEG orientation is normalized in pixels or represented only by EXIF. */
@Keep
enum class JpegOutputStrategy {
    /** Decode, rotate/mirror, and re-encode so pixels are physically upright. */
    PIXEL_NORMALIZED,

    /** Preserve compressed JPEG pixels and represent rotation/mirroring through EXIF metadata. */
    EXIF_ONLY,
}
