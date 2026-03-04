package com.example.jremote.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import java.net.Inet4Address

/**
 * WiFi 工具类，用于获取手机当前连接的 WiFi 信息
 */
class WifiUtils(private val context: Context) {

    /**
     * 获取当前连接的 WiFi 名称 (SSID)
     * 需要 ACCESS_FINE_LOCATION 权限
     */
    @Suppress("DEPRECATION")
    fun getCurrentSsid(): String? {
        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo

        if (connectionInfo != null) {
            val ssid = connectionInfo.ssid
            // SSID 会被引号包裹，需要去除
            if (ssid != null && ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                return ssid.removeSurrounding("\"")
            }
        }

        return null
    }

    /**
     * 获取当前连接的 WiFi IP 地址
     */
    @Suppress("DEPRECATION")
    fun getCurrentWifiIp(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo

        if (connectionInfo != null && connectionInfo.ipAddress != 0) {
            val ip = connectionInfo.ipAddress
            return String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        }

        return null
    }

    /**
     * 检查是否有活动的 WiFi 连接
     */
    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 获取 WiFi 信号强度 (RSSI)
     */
    fun getRssi(): Int? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo

        return if (connectionInfo != null && connectionInfo.rssi != -127) {
            connectionInfo.rssi
        } else {
            null
        }
    }
}
