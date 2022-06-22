package com.leovp.camerax_sdk.listeners

import com.leovp.camerax_sdk.bean.CaptureImage

/**
 * Author: Michael Leo
 * Date: 2022/4/25 14:23
 */
interface CaptureImageListener {
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
    fun onSavedImageUri(savedImage: CaptureImage.ImageUri?, exc: Exception?) {}

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
    fun onSavedImageBytes(savedImage: CaptureImage.ImageBytes?, exc: Exception?) {}
}