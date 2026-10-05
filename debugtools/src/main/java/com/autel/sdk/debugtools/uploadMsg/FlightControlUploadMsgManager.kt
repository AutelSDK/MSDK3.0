package com.autel.sdk.debugtools.uploadMsg

import com.autel.drone.sdk.vmodelx.interfaces.IBaseDevice
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.common.bean.WarningAtom
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateHFNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateLFNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneWarningStateNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.FlightControlStatusInfo
import com.autel.sdk.debugtools.helper.DroneUploadMsgData
import com.autel.sdk.debugtools.uploadMsg.inter.IAircraftUpMsgHandler

/**
 * Flight control report message handler
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/10/11
 *
 */
object FlightControlUploadMsgManager : IAircraftUpMsgHandler {
    //FC high-frequency report parameters
    val systemStateData = DroneUploadMsgData<DroneSystemStateHFNtfyBean>()

    //Drone common parameter reporting
    val systemLFStateData = DroneUploadMsgData<DroneSystemStateLFNtfyBean>()

    //Drone working state reporting
    val flightControlStatusInfoData = DroneUploadMsgData<FlightControlStatusInfo>()

    //Warning report parameters
    val warningStateData = DroneUploadMsgData<DroneWarningStateNtfyBean>()

    //Drone warning notification
    val warningAtomListData = DroneUploadMsgData<List<WarningAtom>>()

    //Drone real-time warning notification
    val warningAtomData = DroneUploadMsgData<WarningAtom>()

    private var curWarnTime = 0L//drone warning info; write at most once per 2s
    override fun listenMsg(aircraftDevice: IBaseDevice) {
        val keyManager = aircraftDevice.getKeyManager()
        //FC high-frequency info
        keyManager.listen(UploadMsgKeyTools.KeyFlightControlSystemHFState,
            object : CommonCallbacks.KeyListener<DroneSystemStateHFNtfyBean> {
                override fun onValueChange(
                    oldValue: DroneSystemStateHFNtfyBean?, newValue: DroneSystemStateHFNtfyBean
                ) {
                    newValue?.let {
                        systemStateData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        //FC low-frequency info
        keyManager.listen(UploadMsgKeyTools.KeyFlightControlSystemLFState,
            object : CommonCallbacks.KeyListener<DroneSystemStateLFNtfyBean> {
                override fun onValueChange(
                    oldValue: DroneSystemStateLFNtfyBean?, newValue: DroneSystemStateLFNtfyBean
                ) {
                    newValue?.let {
                        systemLFStateData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        //Drone working state info
        keyManager.listen(UploadMsgKeyTools.KeyDroneWorkStatusInfoReport,
            object : CommonCallbacks.KeyListener<FlightControlStatusInfo> {
                override fun onValueChange(
                    oldValue: FlightControlStatusInfo?, newValue: FlightControlStatusInfo
                ) {
                    newValue?.let {
                        flightControlStatusInfoData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        //FC warning info
        keyManager.listen(UploadMsgKeyTools.KeyFlightStateWarning,
            object : CommonCallbacks.KeyListener<DroneWarningStateNtfyBean> {
                override fun onValueChange(
                    oldValue: DroneWarningStateNtfyBean?, newValue: DroneWarningStateNtfyBean
                ) {
                    newValue?.let {
                        warningStateData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        //Drone warning notification
        keyManager.listen(
            UploadMsgKeyTools.KeyDroneWarning,
            object : CommonCallbacks.KeyListener<List<WarningAtom>> {
                override fun onValueChange(
                    oldValue: List<WarningAtom>?,
                    newValue: List<WarningAtom>
                ) {
                    newValue?.let {
                        //persist the latest drone state at most once per 2s
                        if (System.currentTimeMillis() - curWarnTime > 1900) {
                            curWarnTime = System.currentTimeMillis()
                        }
                        warningAtomListData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        //Drone real-time warning notification
        keyManager.listen(
            UploadMsgKeyTools.KeyDroneRuntimeWarning,
            object : CommonCallbacks.KeyListener<WarningAtom> {
                override fun onValueChange(
                    oldValue: WarningAtom?,
                    newValue: WarningAtom
                ) {
                    newValue?.let {
                        warningAtomData.putValue(aircraftDevice, newValue)
                    }
                }
            })
    }

    override fun cancelListenMsg(aircraftDevice: IBaseDevice) {
        systemStateData.remove(aircraftDevice)
        systemLFStateData.remove(aircraftDevice)
        flightControlStatusInfoData.remove(aircraftDevice)
        warningStateData.remove(aircraftDevice)
        warningAtomData.remove(aircraftDevice)
    }
}