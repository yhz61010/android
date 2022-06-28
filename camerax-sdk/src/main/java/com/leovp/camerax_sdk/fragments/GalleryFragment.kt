package com.leovp.camerax_sdk.fragments

import android.graphics.PointF
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.leovp.camerax_sdk.R
import com.leovp.camerax_sdk.databinding.FragmentGalleryBinding
import com.leovp.lib_common_android.exts.padWithDisplayCutout
import com.leovp.lib_common_android.exts.share
import com.leovp.lib_common_android.exts.showImmersive
import com.leovp.log_sdk.LogContext
import java.io.File
import java.util.*

val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    /** Android ViewBinding */
    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null

    private val fragmentGalleryBinding get() = _fragmentGalleryBinding!!

    /** AndroidX navigation arguments */
    private val args: GalleryFragmentArgs by navArgs()

    private lateinit var mediaList: MutableList<File>

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        private var previousPosition = 0
        override fun onPageSelected(position: Int) {
            LogContext.log.d(TAG, "Current position=$position previous=$previousPosition")
            if (previousPosition != position) {
                val photoFragment =
                        childFragmentManager.fragments[previousPosition] as? PhotoFragment
                val iv = photoFragment?.view as? SubsamplingScaleImageView
                iv?.setScaleAndCenter(0f, PointF(0f, 0f))
            }
            previousPosition = position
        }
    }

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = mediaList.size

        override fun createFragment(position: Int) = PhotoFragment.create(mediaList[position])

        // When you call [Adapter#notifyDataSetChanged] method, you should [containsItem] method
        // to check whether the item exists in List.
        // You'd better to override the [getItemId] method at the same time.
        override fun getItemId(position: Int): Long = mediaList[position].hashCode().toLong()

        // When you call [Adapter#notifyDataSetChanged] method, you should [containsItem] method
        // to check whether the item exists in List.
        // You'd better to override the [getItemId] method at the same time.
        override fun containsItem(itemId: Long): Boolean {
            return mediaList.firstOrNull { it.hashCode().toLong() == itemId } != null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // https://stackoverflow.com/a/71891158/1685062
            // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
            @Suppress("DEPRECATION")
            retainInstance = true
        }

        // Get root directory of media from navigation arguments
        val rootDirectory = File(args.rootDirectory)

        // Walk through all files in the root directory
        // We reverse the order of the list to present the last photos first
        mediaList = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.uppercase(Locale.ROOT))
        }?.sortedDescending()?.toMutableList() ?: mutableListOf()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding = FragmentGalleryBinding.inflate(inflater, container, false)
        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Checking media files list
        if (mediaList.isEmpty()) {
            fragmentGalleryBinding.deleteButton.isEnabled = false
            fragmentGalleryBinding.shareButton.isEnabled = false
        }

        // Populate the ViewPager2 and implement a cache of two media items
        fragmentGalleryBinding.photoViewPager.apply {
            offscreenPageLimit = 2
            adapter = MediaPagerAdapter(this@GalleryFragment)
            currentItem = 0
            registerOnPageChangeCallback(pageChangeCallback)
        }

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            fragmentGalleryBinding.cutoutSafeArea.padWithDisplayCutout()
        }

        // Handle back button press
        fragmentGalleryBinding.backButton.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax)
                .navigateUp()
        }

        // Handle share button press
        fragmentGalleryBinding.shareButton.setOnClickListener {
            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)
                ?.let { mediaFile ->
                    val uri =
                            FileProvider.getUriForFile(view.context,
                                view.context.packageName + ".provider",
                                mediaFile)
                    share(uri, getString(R.string.share_hint))
                }
        }

        // Handle delete button press
        fragmentGalleryBinding.deleteButton.setOnClickListener {
            val photoViewPager = fragmentGalleryBinding.photoViewPager
            val currentItemPos = photoViewPager.currentItem
            mediaList.getOrNull(currentItemPos)
                ?.let { mediaFile ->
                    AlertDialog.Builder(view.context)
                        .setTitle(getString(R.string.delete_title))
                        .setMessage(getString(R.string.delete_dialog))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            // Delete current photo
                            mediaFile.delete()

                            // Send relevant broadcast to notify other apps of deletion
                            MediaScannerConnection.scanFile(view.context,
                                arrayOf(mediaFile.absolutePath), null, null)

                            // Notify our view pager
                            mediaList.removeAt(currentItemPos)
                            photoViewPager.adapter?.notifyItemRemoved(currentItemPos)

                            // If all photos have been deleted, return to camera
                            if (mediaList.isEmpty()) {
                                Navigation.findNavController(requireActivity(),
                                    R.id.fragment_container_camerax).navigateUp()
                            }

                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create().showImmersive()
                }
        }
    }

    override fun onDestroyView() {
        fragmentGalleryBinding.photoViewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        _fragmentGalleryBinding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "GalleryFragment"
    }
}
