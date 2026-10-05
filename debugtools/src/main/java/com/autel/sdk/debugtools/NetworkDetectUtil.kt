package com.autel.sdk.debugtools


/**
 * network detection util singleton class
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/10/12.
 */
class NetworkDetectUtil {

    companion object {
        fun getInstance() = InstanceHelper.sSingle
    }

    object InstanceHelper {
        val sSingle = NetworkDetectUtil()
    }

    /**
     * Ping command format: ping -c count -w timeOut ip
     * Parameters:
     * -c: number of pings
     * -w: deadline in minutes
     *
     * @param ip    the IP to ping
     */
    public fun ping(ip: String): Boolean {
        return ping(ip, 1, 5)
    }

    /**
     * Ping command format: ping -c count -w timeOut ip
     * Parameters:
     * -c: number of pings
     * -w: deadline in minutes
     *
     * @param ip        the IP to ping
     * @param count     number of pings
     * @param timeOut   timeout
     */
    public fun ping(ip: String, count: Int, timeOut: Int): Boolean {
        val cmd = "ping -c $count -w $timeOut $ip"
        val process = Runtime.getRuntime().exec(cmd)
        val status = process.waitFor()
        return status == 0
    }

}