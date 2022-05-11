package com.leovp.demo.jetpack_components.examples.navigation.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.leovp.demo.databinding.FragmentForNavSecondBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener

class FragmentForNavSecond : Fragment() {
    private var _binding: FragmentForNavSecondBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val args by navArgs<FragmentForNavSecondArgs>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentForNavSecondBinding.inflate(inflater, container, false)
        binding.btnGotoForthActivity.setOnSingleClickListener {
            val forthAction = FragmentForNavSecondDirections.actionFragmentForNavSecondToFragmentForNavForth()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}