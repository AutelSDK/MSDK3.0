package com.autel.sdk.debugtools.uploadMsg.inter

import com.autel.drone.sdk.vmodelx.interfaces.IBaseDevice

/**
 * RC report info
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/11/5
 */
interface IRemoteUpMsgHandler {
    /**
     * Listen to messages
     * @param remoterDevice device
     */
    fun listenMsg(remoterDevice: IBaseDevice)
}