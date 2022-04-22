package com.leovp.camerax_sdk.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.leovp.camerax_sdk.R
import com.leovp.lib_image.getBitmap
import java.io.File

/** Fragment used for each individual page showing a photo inside of [GalleryFragment] */
class PhotoFragment internal constructor() : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?) = ImageView(context)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        val iv: ImageView = view as ImageView
        val imgFile: File? = args.getString(FILE_NAME_KEY)?.let { File(it) }
        if (imgFile == null) {
            iv.setImageResource(R.drawable.ic_photo)
        } else {
            iv.setImageBitmap(imgFile.getBitmap())
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