package com.leovp.camerax_sdk.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.leovp.camerax_sdk.R
import java.io.File

/** Fragment used for each individual page showing a photo inside of [GalleryFragment] */
class PhotoFragment internal constructor() : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?) = SubsamplingScaleImageView(context)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        val iv: SubsamplingScaleImageView = view as SubsamplingScaleImageView
        val imgFile: File? = args.getString(FILE_NAME_KEY)?.let { File(it) }
        if (imgFile == null) {
            iv.setImage(ImageSource.resource(R.drawable.ic_photo))
        } else {
            iv.setImage(ImageSource.uri(imgFile.absolutePath))
        }
    }

    companion object {
        private const val FILE_NAME_KEY = "file_name"

        fun create(image: File) = PhotoFragment().apply {
            arguments = Bundle().apply {
                putString(FILE_NAME_KEY, image.absolutePath)
            }
        }
    }
}