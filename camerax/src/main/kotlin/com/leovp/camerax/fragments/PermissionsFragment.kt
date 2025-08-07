package com.leovp.camerax.fragments

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
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
                .request(object : OnPermissionCallback {
                    override fun onGranted(granted: MutableList<IPermission>, all: Boolean) {
                        // Take the user to the success fragment when permission is granted
                        Toast.makeText(context, "Permission request granted", Toast.LENGTH_LONG).show()
                        navigateToCamera()
                    }

                    override fun onDenied(denied: MutableList<IPermission>, never: Boolean) {
                        Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
                    }
                })
        }
    }

    private fun navigateToCamera() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Navigation.findNavController(requireActivity(), R.id.fragment_container_camerax).navigate(
                    PermissionsFragmentDirections.actionPermissionsToCamera()
                )
            }
        }
    }
}
