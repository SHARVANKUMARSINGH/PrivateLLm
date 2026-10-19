package com.privtellm.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String {
        try {
            // Check NetworkInterfaces directly (covers Wi-Fi, Ethernet, Hotspot AP)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: ""
                        if (hostAddress.startsWith("192.168.") ||
                            hostAddress.startsWith("10.") ||
                            hostAddress.startsWith("172.")
                        ) {
                            return hostAddress
                        }
                    }
                }
            }

            // Fallback to WifiManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val ip = wifiManager.connectionInfo.ipAddress
                if (ip != 0) {
                    @Suppress("DEPRECATION")
                    return Formatter.formatIpAddress(ip)
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
        return "127.0.0.1"
    }

    fun getFormattedApiUrl(context: Context, port: Int = 8080): String {
        val ip = getLocalIpAddress(context)
        return "http://$ip:$port"
    }
}
