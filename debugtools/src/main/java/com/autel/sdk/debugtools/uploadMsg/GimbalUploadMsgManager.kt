package com.autel.sdk.debugtools.uploadMsg

import com.autel.drone.sdk.vmodelx.interfaces.IBaseDevice
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.gimbal.bean.DroneGimbalStateBean
import com.autel.sdk.debugtools.helper.DroneUploadMsgData
import com.autel.sdk.debugtools.uploadMsg.inter.IAircraftUpMsgHandler

/**
 * gimble message passing and canceling
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/10/12
 */
object GimbalUploadMsgManager : IAircraftUpMsgHandler {

    /**
     * Gimbal reporting parameters
     */
    val gimbalHeatBeatData = DroneUploadMsgData<DroneGimbalStateBean>()
    val imuCalibrationData = DroneUploadMsgData<DroneGimbalStateBean>()
    override fun listenMsg(aircraftDevice: IBaseDevice) {
        val keyManager = aircraftDevice.getKeyManager()
        keyManager.listen(
            UploadMsgKeyTools.KeyGimbalHeatBeat,
            object : CommonCallbacks.KeyListener<DroneGimbalStateBean> {
                override fun onValueChange(
                    oldValue: DroneGimbalStateBean?,
                    newValue: DroneGimbalStateBean
                ) {
                    newValue?.let {
                        gimbalHeatBeatData.putValue(aircraftDevice, newValue)
                    }
                }
            })

        keyManager.listen(
            UploadMsgKeyTools.KeyGimbalHeatBeat,
            object : CommonCallbacks.KeyListener<DroneGimbalStateBean> {
                override fun onValueChange(
                    oldValue: DroneGimbalStateBean?,
                    newValue: DroneGimbalStateBean
                ) {
                    newValue?.let {
                        imuCalibrationData.putValue(aircraftDevice, newValue)
                    }
                }
            })
    }

    override fun cancelListenMsg(aircraftDevice: IBaseDevice) {
        gimbalHeatBeatData.remove(aircraftDevice)
    }


}