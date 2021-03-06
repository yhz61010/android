package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityWifiBinding

class WifiActivity : BaseDemonstrationActivity() {
    private var _binding: ActivityWifiBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityWifiBinding.inflate(layoutInflater).apply {
            setContentView(this.root)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}