package com.ollitert.llm.server.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/** Checks whether the device currently has an active Wi-Fi connection. */
fun isWifiConnected(context: Context): Boolean {
  val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    ?: return false
  val network = cm.activeNetwork ?: return false
  val caps = cm.getNetworkCapabilities(network) ?: return false
  return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

/**
 * Returns the device's Wi-Fi IPv4 address, or `null` if unavailable.
 *
 * Tries the WifiManager first, then falls back to enumerating network interfaces
 * (works better on newer Android versions where WifiInfo is deprecated).
 */
fun getWifiIpAddress(context: Context): String? {
  // Approach 1: WifiManager (works on most devices)
  val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  if (wifiManager != null) {
    @Suppress("DEPRECATION")
    val ip = wifiManager.connectionInfo.ipAddress
    if (ip != 0) {
      return formatIpAddress(ip)
    }
  }

  // Approach 2: Enumerate network interfaces
  try {
    for (iface in NetworkInterface.getNetworkInterfaces()) {
      if (!iface.isUp || iface.isLoopback) continue
      // Look for wlan interfaces
      if (!iface.name.startsWith("wlan")) continue
      for (addr in iface.inetAddresses) {
        if (addr is Inet4Address && !addr.isLoopbackAddress) {
          return addr.hostAddress
        }
      }
    }
  } catch (_: Exception) {
    // Fall through
  }

  return null
}

private fun formatIpAddress(ip: Int): String {
  return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
}
