package com.autel.sdk.debugtools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.ConnectivityManager
import android.net.wifi.*
import android.text.TextUtils
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*


/**
 * WiFi and Ethernet utility class
 * @author Autel Robotics
 * @Date 2023-03-15
 */
object WiFiUtils {

    private const val TAG: String = "WiFiUtils"

    /**
     * Whether a wired network is connected, judged by whether an IP address was obtained
     */
    fun isEthernetConnected(): Boolean {
        var ip = getEth0IP()
//        AutelLog.d("NetWork", "isEthernetConnected ip $ip")
        return !TextUtils.isEmpty(ip)
    }

    /**
     * Get the WiFi hotspot state
     */
    fun getWiFiHotSpotState(applicationContext: Context): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val methods = wifiManager.javaClass.declaredMethods
        for (method in methods) {
            if (method.name == "isWifiApEnabled") {
                try {
                    return method.invoke(wifiManager) as Boolean
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                break
            }
        }
        return false

    }



    /**
     * Change the WiFi hotspot name and password
     */
    fun changeWiFiHotSpot(context: Context, ssid: String, password: String) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiConfiguration = WifiConfiguration().apply {
            SSID = ssid // set hotspot name
            preSharedKey = password // set hotspot password
            allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN) // set auth algorithm
            allowedProtocols.set(WifiConfiguration.Protocol.RSN) // set security protocol
            allowedProtocols.set(WifiConfiguration.Protocol.WPA)
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK) // set key management type
        }
        wifiManager.isWifiEnabled = true
        val method = wifiManager.javaClass.getMethod(
            "setWifiApEnabled",
            WifiConfiguration::class.java,
            Boolean::class.java
        )
        method.invoke(wifiManager, wifiConfiguration, true) // enable hotspot

    }

    @SuppressLint("MissingPermission")
    private fun getCurrentWifiConfiguration(wifiManager: WifiManager): WifiConfiguration? {
        val configuredNetworks = wifiManager.configuredNetworks
        if (configuredNetworks != null) {
            for (config in configuredNetworks) {
                if (config.status == WifiConfiguration.Status.CURRENT) {
                    return config
                }
            }
        }
        return null
    }

    /**
     * Get the device ETH0 or WiFi IP address
     */
    fun getIPAddress(): String? {
        var ip = getEth0IP()
        return if (!TextUtils.isEmpty(ip)) {
            ip
        } else {
            getWiFiWlan0IPAddress()
        }
    }

    /**
     * Get the wired network IP address
     */
    private fun getEth0IP(): String {
        val interfaceName = "eth0"
        try {
            val enNetworkInterface = NetworkInterface.getNetworkInterfaces() //list all local network interfaces
            while (enNetworkInterface.hasMoreElements()) {  //whether the Enumeration has more elements
                val networkInterface = enNetworkInterface.nextElement() //get the next element from the Enumeration
                if (!networkInterface.isUp) { // whether the interface is up
                    continue
                }
                if (interfaceName != networkInterface.displayName) { // whether the interface name matches
                    continue
                }
                val enInetAddress =
                    networkInterface.inetAddresses //getInetAddresses returns all IPs bound to this interface.
                while (enInetAddress.hasMoreElements()) {
                    val inetAddress = enInetAddress.nextElement()
                    if (inetAddress is Inet4Address) {  //whether it is IPv4
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * Get the WiFi IP address
     */
    fun getWiFiIP(context: Context): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        val wifiInfo = wifiManager!!.connectionInfo
        val ipAddress = wifiInfo.ipAddress

        return (ipAddress and 0xFF).toString() + "." + (ipAddress shr 8 and 0xFF) + "." + (ipAddress shr 16 and 0xFF) + "." + (ipAddress shr 24 and 0xFF)
    }

    /**
     * Get the WiFi hotspot IP address
     */
    fun getWiFiWlan0IPAddress(): String {
        try {
            val ifaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in ifaces) {
                if (iface.name.equals("wlan0", ignoreCase = true)) {
                    val addrs = Collections.list(iface.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLinkLocalAddress && !addr.isLoopbackAddress) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("getWifiIpAddress", e.toString())
        }
        return "0.0.0.0"
    }

    /**
     * Get the WiFi hotspot IP address; this API returns 0.0.0.0
     */
    fun getWiFiHotSpotIP(context: Context): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        val ipAddress = dhcpInfo.ipAddress
        return "${(ipAddress and 0xFF)}.${(ipAddress shr 8 and 0xFF)}.${(ipAddress shr 16 and 0xFF)}.${(ipAddress shr 24 and 0xFF)}"
    }

    /**
     * Get the current scanned WiFi list
     */
    fun getWifiList(context: Context): List<ScanResult>? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.startScan()
        return wifiManager.scanResults
    }

    /**
     * Get the connected WiFi info; null if not connected
     */
    fun getCurrentWifiInfo(context: Context): WifiInfo? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.connectionInfo
    }

    /**
     * Whether WiFi is enabled
     */
    fun isWiFiState(applicationContext: Context): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    /**
     * Whether WiFi is connected
     */
    fun isWiFiConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        return (networkInfo != null
                && networkInfo.isConnected
                && networkInfo.type == ConnectivityManager.TYPE_WIFI)
    }

    fun connectToWiFi(context: Context, ssid: String, password: String) {
        val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        Log.d("WifiUtils",  "connectWiFi->$ssid $password")
        // Check whether WiFi is enabled
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }

        // Build the WiFi configuration
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = String.format("\"%s\"", ssid)
        wifiConfig.preSharedKey = String.format("\"%s\"", password)

        // Add the WiFi configuration
        val networkId = wifiManager.addNetwork(wifiConfig)

        // Check whether the password is correct
        val isPasswordCorrect = wifiManager.enableNetwork(networkId, false)
        Log.d("WifiUtils",  "connectWiFi password correct->$isPasswordCorrect")
        if (!isPasswordCorrect) {
            // Remove the added network
            wifiManager.removeNetwork(networkId)
            return
        }

        // Connect to WiFi
        wifiManager.disconnect()
        wifiManager.enableNetwork(networkId, true)
        wifiManager.reconnect()
    }


    /**
     * Convert signal strength to a 5-bar level
     */
    fun convertWifiSignalStrength(signalStrength: Int): Int {
        return when {
            signalStrength >= -50 -> 5
            signalStrength >= -60 -> 4
            signalStrength >= -70 -> 3
            signalStrength >= -80 -> 2
            else -> 1
        }
    }

    /**
     * Disconnect the current WiFi connection
     */
    fun disconnectWiFi(context: Context) {
        val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
        if (wifiManager.isWifiEnabled) {
            wifiManager.disconnect()
        }
    }

}

