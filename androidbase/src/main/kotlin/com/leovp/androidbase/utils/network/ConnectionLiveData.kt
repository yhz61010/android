@file:Suppress("unused")

package com.leovp.androidbase.utils.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import com.leovp.android.utils.NetworkUtil
import java.util.concurrent.atomic.AtomicReference

/**
 * Usage:
 * ```kotlin
 * val connectionLiveData = ConnectionLiveData(context)
 * connectionLiveData.observe(this) { (online, type) ->
 *     // Add your codes here.
 * }
 * ```
 * Author: Michael Leo
 * Date: 2022/1/6 17:25
 *
 * https://stackoverflow.com/a/52718543
 */
class ConnectionLiveData(private val context: Context) : LiveData<ConnectionLiveData.ConnectionStatus>() {
    companion object {
        private const val TAG = "Connection"
    }

    private val lastNetworkType: AtomicReference<String> = AtomicReference(NetworkUtil.TYPE_OFFLINE)

    data class ConnectionStatus(val online: Boolean, val changed: Boolean, val type: String,)

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    private lateinit var connectivityManagerCallback: ConnectivityManager.NetworkCallback

    private val networkRequestBuilder: NetworkRequest.Builder = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

    @SuppressLint("ObsoleteSdkInt")
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun onActive() {
        super.onActive()
        updateConnection()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ->
                connectivityManager.registerDefaultNetworkCallback(
                    getConnectivityMarshmallowManagerCallback()
                )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                marshmallowNetworkAvailableRequest()

            else -> lollipopNetworkAvailableRequest() // For above LOLLIPOP or higher
        }
    }

    override fun onInactive() {
        super.onInactive()
        lastNetworkType.set(NetworkUtil.TYPE_OFFLINE)
        connectivityManager.unregisterNetworkCallback(connectivityManagerCallback)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun lollipopNetworkAvailableRequest() {
        connectivityManager.registerNetworkCallback(
            networkRequestBuilder.build(),
            getConnectivityLollipopManagerCallback()
        )
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun marshmallowNetworkAvailableRequest() {
        connectivityManager.registerNetworkCallback(
            networkRequestBuilder.build(),
            getConnectivityMarshmallowManagerCallback()
        )
    }

    private fun getConnectivityLollipopManagerCallback(): ConnectivityManager.NetworkCallback {
        connectivityManagerCallback = object : ConnectivityManager.NetworkCallback() {
            @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
            override fun onAvailable(network: Network) {
                val networkType = getNetworkType()
                postValue(
                    ConnectionStatus(
                        online = true,
                        changed = lastNetworkType.get() != networkType,
                        type = NetworkUtil.TYPE_OFFLINE
                    )
                )
                lastNetworkType.set(networkType)
            }

            override fun onLost(network: Network) {
                postValue(
                    ConnectionStatus(
                        online = false,
                        changed = true,
                        type = NetworkUtil.TYPE_OFFLINE
                    )
                )
            }
        }
        return connectivityManagerCallback
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun getConnectivityMarshmallowManagerCallback(): ConnectivityManager.NetworkCallback {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManagerCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ) {
                        val connectionType = when {
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                                NetworkUtil.TYPE_WIFI

                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                                NetworkUtil.TYPE_CELLULAR

                            else -> NetworkUtil.TYPE_OTHER
                        }

                        postValue(
                            ConnectionStatus(
                                online = true,
                                changed = lastNetworkType.get() != connectionType,
                                type = connectionType
                            )
                        )
                        lastNetworkType.set(connectionType)
                    }
                }

                // override fun onAvailable(network: Network) {
                //     super.onAvailable(network)
                // }

                override fun onLost(network: Network) {
                    postValue(
                        ConnectionStatus(
                            online = false,
                            changed = true,
                            type = NetworkUtil.TYPE_OFFLINE
                        )
                    )
                }
            }
            return connectivityManagerCallback
        } else {
            throw IllegalAccessError("Accessing wrong API version")
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getNetworkType(): String = NetworkUtil.getNetworkTypeName(context) ?: NetworkUtil.TYPE_OTHER

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun updateConnection() {
        val isOnline = NetworkUtil.isOnline(context)
        val networkType = getNetworkType()
        lastNetworkType.set(networkType)
        postValue(ConnectionStatus(isOnline, true, networkType))
    }
}
