package com.leovp.demo.jetpack_components.examples.navigation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.leovp.androidbase.framework.BaseFragment
import com.leovp.demo.R
import com.leovp.demo.databinding.FragmentForNavForthBinding
import com.leovp.log.base.ITAG

class FragmentForNavForth : BaseFragment<FragmentForNavForthBinding>(R.layout.fragment_for_nav_forth) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentForNavForthBinding {
        return FragmentForNavForthBinding.inflate(inflater, container, false)
    }
}
