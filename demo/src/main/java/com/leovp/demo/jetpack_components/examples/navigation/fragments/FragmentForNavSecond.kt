package com.leovp.demo.jetpack_components.examples.navigation.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.leovp.androidbase.framework.BaseFragment
import com.leovp.demo.R
import com.leovp.demo.databinding.FragmentForNavSecondBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.base.ITAG

class FragmentForNavSecond : BaseFragment<FragmentForNavSecondBinding>(R.layout.fragment_for_nav_second) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentForNavSecondBinding {
        return FragmentForNavSecondBinding.inflate(inflater, container, false)
    }

    private val args by navArgs<FragmentForNavSecondArgs>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.btnGotoForthActivity.setOnSingleClickListener {
            val forthAction =
                FragmentForNavSecondDirections.actionFragmentForNavSecondToFragmentForNavForth()
            it.findNavController().navigate(forthAction)
        }
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val param1: String = args.param1
        val param2: String = args.param2
        val paramDefault: String = args.paramDefault
        binding.tvArgs.text = "param1=$param1 param2=$param2 default=$paramDefault"
    }
}
