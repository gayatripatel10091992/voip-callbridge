package com.gayatripatel.callbridge.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.gayatripatel.callbridge.core.call.CallNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits which network a call would currently use, or null when there is none.
 * The in-call screen feeds this into the call state machine to hand calls over between Wi-Fi and mobile data.
 */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext private val context: Context) {

    val network: Flow<CallNetwork?> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toCallNetwork())
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }
        trySend(cm.getNetworkCapabilities(cm.activeNetwork)?.toCallNetwork())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities.toCallNetwork(): CallNetwork? = when {
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> null
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CallNetwork.MOBILE_DATA
        else -> CallNetwork.WIFI
    }
}
