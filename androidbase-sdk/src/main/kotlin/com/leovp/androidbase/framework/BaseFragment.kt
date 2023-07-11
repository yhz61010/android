package com.leovp.androidbase.framework

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Usage:
 * ```
 * class YourFragment : BaseFragment<YourFragmentBinding>(R.layout.fragment_your_layout) {
 *    override fun getTagName(): String = "LogTag"
 *
 *    override fun getViewBinding(inflater: LayoutInflater,
 *        container: ViewGroup?,
 *        savedInstanceState: Bundle?): YourFragmentBinding {
 *        return YourFragmentBinding.inflate(inflater, container, false)
 *    }
 *    // Your class contents here.
 * }
 * ```
 *
 * Author: Michael Leo
 * Date: 2022/6/28 15:46
 */
abstract class BaseFragment<B : ViewBinding>(@LayoutRes layoutResId: Int) : Fragment(layoutResId) {
    abstract fun getTagName(): String

    @Suppress("WeakerAccess", "unused")
    val logTag: String by lazy { getTagName() }

    @Suppress("WeakerAccess")
    /**
     * Most of time, you should use [binding] property.
     */
    private var _binding: B? = null

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    protected val binding: B get() = _binding!!

    protected fun getNullableBinding(): B? = _binding

    abstract fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): B

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = getViewBinding(inflater, container, savedInstanceState)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
