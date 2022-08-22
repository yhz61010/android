package com.leovp.camerax.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.leovp.camerax.sdk.R
import com.leovp.camerax.fragments.base.BaseCameraXFragment.Companion.VIDEO_EXTENSION
import java.io.File

/** Fragment used for each individual page showing a photo inside of [GalleryFragment] */
class PhotoFragment internal constructor() : Fragment() {

    private var isVideo = true
    private var mediaFile: File? = null
    private var mc: MediaController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args = arguments ?: throw IllegalAccessException("Arguments can't be null.")
        mediaFile = args.getString(FILE_NAME_KEY)?.let {
            isVideo = it.endsWith(VIDEO_EXTENSION, true)
            File(it)
        }
        return if (isVideo) VideoView(context) else SubsamplingScaleImageView(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isVideo) {
            mc = MediaController(requireContext())
            (view as VideoView).apply {
                setVideoPath(mediaFile!!.absolutePath)
                setMediaController(mc)
                requestFocus()
            }
        } else {
            val iv: SubsamplingScaleImageView = view as SubsamplingScaleImageView
            mediaFile?.let { iv.setImage(ImageSource.uri(it.absolutePath)) }
                ?: iv.setImage(ImageSource.resource(R.drawable.ic_photo))
        }
    }

    override fun onResume() {
        super.onResume()
        (view as? VideoView)?.start()
        mc?.show(0)
    }

    companion object {
        private const val FILE_NAME_KEY = "file_name"

        fun create(mediaFile: File) = PhotoFragment().apply {
            arguments = Bundle().apply {
                putString(FILE_NAME_KEY, mediaFile.absolutePath)
            }
        }
    }
}
