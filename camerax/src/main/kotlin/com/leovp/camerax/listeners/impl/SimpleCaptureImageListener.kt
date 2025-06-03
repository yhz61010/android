@file:Suppress("unused")

package com.leovp.camerax.listeners.impl

import com.leovp.camerax.bean.CaptureImage
import com.leovp.camerax.listeners.CaptureImageListener

/**
 * Author: Michael Leo
 * Date: 2022/4/25 15:34
 */
open class SimpleCaptureImageListener : CaptureImageListener {
    /**
     * @param savedImage It guarantees that if the exc property in this function is not null,
     * the savedImage property must be null.
     * On the contrary, if the savedImage property in this function is not null,
     * the exc property must be null.
     *
     * @param exc It guarantees that if the exc property in this function is not null,
     * the savedImage property must be null.
     * On the contrary, if the savedImage property in this function is not null,
     * the exc property must be null.
     */
    override fun onSavedImageUri(savedImage: CaptureImage.ImageUri?, exc: Exception?) {}

    /**
     * @param savedImage It guarantees that if the exc property in this function is not null,
     * the savedImage property must be null.
     * On the contrary, if the savedImage property in this function is not null,
     * the exc property must be null.
     *
     * @param exc It guarantees that if the exc property in this function is not null,
     * the savedImage property must be null.
     * On the contrary, if the savedImage property in this function is not null,
     * the exc property must be null.
     */
    override fun onSavedImageBytes(savedImage: CaptureImage.ImageBytes?, exc: Exception?) {}
}
