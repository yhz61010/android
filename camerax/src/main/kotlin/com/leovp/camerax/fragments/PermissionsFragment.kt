package com.leovp.camerax.fragments

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.navigation.findNavController
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.leovp.camerax.R
import com.leovp.log.LogContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The sole purpose of this fragment is to request permissions and, once granted, display the
 * camera fragment to the user.
 */
class PermissionsFragment : Fragment() {

    companion object {
        private const val TAG = "PermissionsFragment"

        //        val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)
        val PERMISSIONS_REQUIRED = PermissionLists.getCameraPermission()
    }

    private val navigationGate = OneShotGate()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (XXPermissions.isGrantedPermission(requireContext(), PERMISSIONS_REQUIRED)) {
            navigateToCamera()
        } else {
            XXPermissions.with(this)
                .permission(PERMISSIONS_REQUIRED)
                .request { grantedList, deniedList ->
                    val allGranted = deniedList.isEmpty()
                    if (allGranted) {
                        // Take the user to the success fragment when permission is granted
                        Toast.makeText(
                            context,
                            "Permission request granted",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToCamera()
                    } else {
                        //  val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(
                        //      this@PermissionsFragment.requireActivity(),
                        //      deniedList
                        //  )
                        Toast.makeText(
                            context,
                            "Permission request denied",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        navigationGate.reset()
        super.onDestroyView()
    }

    private fun navigateToCamera() {
        if (!navigationGate.tryAcquire()) return
        val owner = viewLifecycleOwnerLiveData.value ?: run {
            navigationGate.reset()
            return
        }
        owner.lifecycleScope.launch {
            try {
                owner.lifecycle.withStarted {
                    requireActivity().findNavController(R.id.fragment_container_camerax).navigate(
                        PermissionsFragmentDirections.actionPermissionsToCamera()
                    )
                }
            } catch (e: CancellationException) {
                navigationGate.reset()
                throw e
            } catch (e: Exception) {
                navigationGate.reset()
                LogContext.log.e(TAG, "Navigate to camera failed", e)
            }
        }
    }
}

internal class OneShotGate {
    private val acquired = AtomicBoolean(false)

    fun tryAcquire(): Boolean = acquired.compareAndSet(false, true)

    fun reset() {
        acquired.set(false)
    }
}
