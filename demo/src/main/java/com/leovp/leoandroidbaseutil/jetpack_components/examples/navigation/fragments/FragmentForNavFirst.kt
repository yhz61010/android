package com.leovp.leoandroidbaseutil.jetpack_components.examples.navigation.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.leovp.leoandroidbaseutil.databinding.FragmentForNavFirstBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.LogContext

class FragmentForNavFirst : Fragment() {
    //    companion object {
    //        private const val ARG_PARAM1 = "param1"
    //        private const val ARG_PARAM2 = "param2"
    //    }

    private var _binding: FragmentForNavFirstBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

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
        LogContext.log.w("FragmentForNavFirst")
        _binding = FragmentForNavFirstBinding.inflate(inflater, container, false)
        binding.gotoSecondActivity.setOnSingleClickListener {
            val secondAction = FragmentForNavFirstDirections.actionFragmentForNavFirstToFragmentForNavSecond("P-One", "P-Two")
            it.findNavController().navigate(secondAction)
        }
        binding.gotoThirdActivity.setOnSingleClickListener {
            val thirdAction = FragmentForNavFirstDirections.actionFragmentForNavFirstToFragmentForNavThird()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}