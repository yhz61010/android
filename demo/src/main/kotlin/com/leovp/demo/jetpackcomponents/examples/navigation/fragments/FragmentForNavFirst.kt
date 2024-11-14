package com.leovp.demo.jetpackcomponents.examples.navigation.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.androidbase.framework.BaseFragment
import com.leovp.demo.R
import com.leovp.demo.databinding.FragmentForNavFirstBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class FragmentForNavFirst : BaseFragment<FragmentForNavFirstBinding>(R.layout.fragment_for_nav_first) {
    //    companion object {
    //        private const val ARG_PARAM1 = "param1"
    //        private const val ARG_PARAM2 = "param2"
    //    }

    override fun getTagName(): String = ITAG

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): FragmentForNavFirstBinding {
        return FragmentForNavFirstBinding.inflate(inflater, container, false)
    }

    //    private var param1: String? = null
    //    private var param2: String? = null

    //    override fun onCreate(savedInstanceState: Bundle?) {
    //        super.onCreate(savedInstanceState)
    //        arguments?.let {
    //            param1 = it.getString(ARG_PARAM1)
    //            param2 = it.getString(ARG_PARAM2)
    //        }
    //    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        LogContext.log.w("FragmentForNavFirst")
        binding.gotoSecondActivity.setOnSingleClickListener {
            val secondAction =
                FragmentForNavFirstDirections.actionFragmentForNavFirstToFragmentForNavSecond(
                    "P-One",
                    "P-Two"
                )
            it.findNavController().navigate(secondAction)
        }
        binding.gotoThirdActivity.setOnSingleClickListener {
            val thirdAction =
                FragmentForNavFirstDirections.actionFragmentForNavFirstToFragmentForNavThird()
            it.findNavController().navigate(thirdAction)
        }
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //        val param1: String = args.param1
        //        val param2: String = args.param2
        //        val paramDefault: String = args.paramDefault
        //        binding.tvArgs.text = "param1=$param1 param2=$param2 default=$paramDefault"
    }
}
