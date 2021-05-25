package com.leovp.leoandroidbaseutil.basic_components.examples

import android.net.wifi.WifiConfiguration
import android.os.Bundle
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.androidbase.exts.android.wifiManager
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.network.IpUtil
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityStaticIpBinding
import java.net.InetAddress


class StaticIpActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityStaticIpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaticIpBinding.inflate(layoutInflater).apply { setContentView(root) }

        val ipByCommand = IpUtil.getByCommand()
        val ipByReflection = IpUtil.getByReflection()
        IpUtil.getAboveAndroid8(this)

        if (LogContext.enableLog) LogContext.log.i("ipByCommand=$ipByCommand")
        if (LogContext.enableLog) LogContext.log.i("ipByReflection=$ipByReflection")

        binding.btnSet.setOnSingleClickListener {
            val ip = binding.etIp.text.toString()
            val prefixLength = binding.etPrefixLen.text.toString().toInt()
            val gateway = binding.etGateway.text.toString()
            val dns1 = binding.etDns1.text.toString()
            val dns2 = binding.etDns2.text.toString()

            try {
                val wifiConfig = WifiConfiguration()
                IpUtil.setStaticIpConfiguration(
                    app.wifiManager, wifiConfig,
                    InetAddress.getByName(ip),
                    prefixLength,
                    InetAddress.getByName(gateway),
                    arrayOf(
                        InetAddress.getByName(dns1),
                        InetAddress.getByName(dns2)
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}