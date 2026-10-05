package com.autel.sdk.debugtools.uploadMsg.inter

import com.autel.drone.sdk.vmodelx.interfaces.IBaseDevice

/**
 * Aircraft report info interface
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/10/11
 *
 */
interface IAircraftUpMsgHandler {
    /**
     * Listen to messages
     * @param aircraftDevice drone device
     */
    fun listenMsg(aircraftDevice: IBaseDevice)

    /**
     * Stop listening to messages
     * @param aircraftDevice drone device
     */
    fun cancelListenMsg(aircraftDevice: IBaseDevice)
}