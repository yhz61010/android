package com.leovp.demo.jetpackcomponents.examples.navigation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.leovp.androidbase.framework.BaseFragment
import com.leovp.demo.R
import com.leovp.demo.databinding.FragmentForNavThirdBinding
import com.leovp.log.base.ITAG

class FragmentForNavThird : BaseFragment<FragmentForNavThirdBinding>(R.layout.fragment_for_nav_third) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentForNavThirdBinding {
        return FragmentForNavThirdBinding.inflate(inflater, container, false)
    }
}
