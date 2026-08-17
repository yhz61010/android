package com.leovp.camerax.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.leovp.camerax.R
import com.leovp.camerax.fragments.base.BaseCameraXFragment.Companion.VIDEO_EXTENSION
import com.leovp.log.LogContext
import java.io.File

/** Fragment used for each individual page showing a photo inside of [GalleryFragment] */
class PhotoFragment internal constructor() : Fragment() {

    private var isVideo = true
    private var mediaFile: File? = null
    private var mc: MediaController? = null
    private var videoView: VideoView? = null

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
        return if (isVideo) {
            FrameLayout(requireContext()).apply {
                setBackgroundColor(Color.BLACK)
                addView(
                    VideoView(context).also { videoView = it },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    ),
                )
            }
        } else {
            SubsamplingScaleImageView(context)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isVideo) {
            val videoFile = mediaFile
            if (videoFile == null) {
                LogContext.log.e(TAG, "Missing media file for video preview")
                return
            }
            mc = MediaController(requireContext())
            videoView?.apply {
                setVideoPath(videoFile.absolutePath)
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
        videoView?.start()
        mc?.show(0)
    }

    override fun onDestroyView() {
        videoView?.stopPlayback()
        videoView = null
        mc = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "PhotoFragment"
        private const val FILE_NAME_KEY = "file_name"

        fun create(mediaFile: File) = PhotoFragment().apply {
            arguments = Bundle().apply {
                putString(FILE_NAME_KEY, mediaFile.absolutePath)
            }
        }
    }
}
