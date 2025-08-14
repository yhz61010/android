package com.leovp.camerax.fragments

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.leovp.camerax.R
import kotlinx.coroutines.launch

/**
 * The sole purpose of this fragment is to request permissions and, once granted, display the
 * camera fragment to the user.
 */
class PermissionsFragment : Fragment() {

    companion object {
        //        val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)
        val PERMISSIONS_REQUIRED = PermissionLists.getCameraPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (XXPermissions.isGrantedPermission(requireContext(), PERMISSIONS_REQUIRED)) {
            navigateToCamera()
        } else {
            XXPermissions.with(this)
                .permission(PERMISSIONS_REQUIRED)
                .request { grantedList, deniedList ->
                    val allGranted = deniedList.isEmpty()
                    if (allGranted) {
                        // Take the user to the success fragment when permission is granted
                        Toast.makeText(context, "Permission request granted", Toast.LENGTH_LONG).show()
                        navigateToCamera()
                    } else {
                        //  val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(
                        //      this@PermissionsFragment.requireActivity(),
                        //      deniedList
                        //  )
                        Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun navigateToCamera() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                requireActivity().findNavController(R.id.fragment_container_camerax).navigate(
                    PermissionsFragmentDirections.actionPermissionsToCamera()
                )
            }
        }
    }
}
