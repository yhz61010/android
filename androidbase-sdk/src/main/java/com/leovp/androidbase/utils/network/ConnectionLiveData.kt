package com.leovp.androidbase.utils.network

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.*
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData

/**
 * Usage:
 * ```kotlin
 * val connectionLiveData = ConnectionLiveData(context)
 * connectionLiveData.observe(this, { isConnected ->
 *     // Do your job
 * })
 * ```
 * Author: Michael Leo
 * Date: 2022/1/6 17:25
 *
 * https://stackoverflow.com/a/52718543
 */
class ConnectionLiveData(context: Context) : LiveData<Boolean>() {
    private var connectivityManager: ConnectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    private lateinit var connectivityManagerCallback: ConnectivityManager.NetworkCallback

    private val networkRequestBuilder: NetworkRequest.Builder = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onActive() {
        super.onActive()
        updateConnection()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> connectivityManager.registerDefaultNetworkCallback(getConnectivityMarshmallowManagerCallback())
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> marshmallowNetworkAvailableRequest()
            else -> lollipopNetworkAvailableRequest() // For above LOLLIPOP or higher
        }
    }

    override fun onInactive() {
        super.onInactive()
        connectivityManager.unregisterNetworkCallback(connectivityManagerCallback)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun lollipopNetworkAvailableRequest() {
        connectivityManager.registerNetworkCallback(networkRequestBuilder.build(), getConnectivityLollipopManagerCallback())
    }

    @TargetApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun marshmallowNetworkAvailableRequest() {
        connectivityManager.registerNetworkCallback(networkRequestBuilder.build(), getConnectivityMarshmallowManagerCallback())
    }

    private fun getConnectivityLollipopManagerCallback(): ConnectivityManager.NetworkCallback {
        connectivityManagerCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                postValue(true)
            }

            override fun onLost(network: Network) {
                postValue(false)
            }
        }
        return connectivityManagerCallback
    }

    private fun getConnectivityMarshmallowManagerCallback(): ConnectivityManager.NetworkCallback {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManagerCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ) {
                        postValue(true)
                    }
                }

                override fun onLost(network: Network) {
                    postValue(false)
                }
            }
            return connectivityManagerCallback
        } else {
            throw IllegalAccessError("Accessing wrong API version")
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun updateConnection() {
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        postValue(activeNetwork?.isConnected == true)
    }
}