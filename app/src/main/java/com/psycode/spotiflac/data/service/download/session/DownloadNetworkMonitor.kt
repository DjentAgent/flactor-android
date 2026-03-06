package com.psycode.spotiflac.data.service.download.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.psycode.spotiflac.data.service.download.core.DownloadLog

class DownloadNetworkMonitor(
    context: Context,
    private val onNetworkChanged: (String) -> Unit
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastSignature: Int? = null
    private var lastOnline: Boolean = false

    fun start() {
        if (callback != null) return
        seedCurrentNetworkState()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val nc = cm.getNetworkCapabilities(network)
                val online = nc?.isUsableInternet() == true
                val signature = nc?.signature()
                if (online && !lastOnline) {
                    lastOnline = true
                    lastSignature = signature
                    onNetworkChanged("online")
                }
            }

            override fun onLost(network: Network) {
                if (lastOnline) {
                    lastOnline = false
                    lastSignature = null
                    onNetworkChanged("lost")
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val online = networkCapabilities.isUsableInternet()
                val signature = networkCapabilities.signature()
                if (online && !lastOnline) {
                    lastOnline = true
                    lastSignature = signature
                    onNetworkChanged("online")
                    return
                }
                if (!online && lastOnline) {
                    lastOnline = false
                    lastSignature = null
                    onNetworkChanged("lost")
                    return
                }
                if (online && lastOnline && lastSignature != null && lastSignature != signature) {
                    lastSignature = signature
                    onNetworkChanged("transport_changed")
                }
            }
        }
        callback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onFailure { DownloadLog.e("Failed to register network callback", it) }
            .onSuccess { DownloadLog.d("Network monitor started") }
    }

    fun stop() {
        val cb = callback ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
        callback = null
        DownloadLog.d("Network monitor stopped")
    }

    private fun seedCurrentNetworkState() {
        val nc = cm.getNetworkCapabilities(cm.activeNetwork)
        lastOnline = nc?.isUsableInternet() == true
        lastSignature = nc?.signature()
    }
}

private fun NetworkCapabilities.isUsableInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

private fun NetworkCapabilities.signature(): Int {
    var value = 1
    if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) value = value * 31 + 1
    if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) value = value * 31 + 2
    if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) value = value * 31 + 3
    if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) value = value * 31 + 4
    if (hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) value = value * 31 + 5
    return value
}
