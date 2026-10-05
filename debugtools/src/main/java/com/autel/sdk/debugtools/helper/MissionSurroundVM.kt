package com.autel.sdk.debugtools.helper

import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.MissionManagerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.MissionOrbitModelingSettingMsgBean

object MissionSurroundVM {


        const val TAG = "MissionSurroundVM"

    /**
     * Enter surround mode
     */
    fun enterSurround(onSuccess: () -> Unit, onError: (str: String) -> Unit,deviceId:Int) {
        val key = KeyTools.createKey(MissionManagerKey.KeyOrbitModelingEnterMsg)
        DeviceManager.getMultiDeviceOperator().performActionList(
            arrayListOf(deviceId),key,null,object :DeviceManager.CompletionCallbackWithParam<DeviceManager.DeviceActionResult<Void>>{
                override fun onSuccess(t: DeviceManager.DeviceActionResult<Void>?) {
                    SDKLog.i(TAG, "enterSurround success")
                    onSuccess.invoke()
                }

                override fun onFailure(failure: DeviceManager.DeviceActionFailure?) {
                    SDKLog.i(TAG, "enterSurround error : ${failure?.msg}")
                   onError.invoke(failure?.msg?:"Start failed, reason unknown")
                }
            }
        )

    }

    /**
     * Exit surround mode
     */
    fun exitSurround(deviceId:Int,onSuccess: () -> Unit, onError: (str: String) -> Unit) {
        val key = KeyTools.createKey(MissionManagerKey.KeyOrbitModelingExitMsg)
        DeviceManager.getMultiDeviceOperator().performActionList(
            arrayListOf(deviceId),key,null,object :DeviceManager.CompletionCallbackWithParam<DeviceManager.DeviceActionResult<Void>>{
                override fun onSuccess(t: DeviceManager.DeviceActionResult<Void>?) {
                    SDKLog.i(TAG, "exitSurround success")
                    onSuccess.invoke()
                }

                override fun onFailure(failure: DeviceManager.DeviceActionFailure?) {
                    SDKLog.i(TAG, "exitSurround getKeyManager is null : ${failure?.msg}")
                    onError.invoke(failure?.msg?:"exitSurround getKeyManager is null")
                }
            }
        )
    }

    /**
     * Start surround
     * @param radius radius
     * @param height altitude
     * @param speed speed
     * @param dir direction
     */
    fun startSurround(
        radius: Int,
        height: Int,
        speed: Int,
        dir: Int,
        deviceId:Int,
        onSuccess: () -> Unit,
        onError: (str: String) -> Unit
    ) {
        val key = KeyTools.createKey(MissionManagerKey.KeyOrbitModelingStartMsg)
        val settingMsgBean = MissionOrbitModelingSettingMsgBean(radius, height, speed, dir)
        DeviceManager.getMultiDeviceOperator().performActionList(
            arrayListOf(deviceId),key,settingMsgBean,object :DeviceManager.CompletionCallbackWithParam<DeviceManager.DeviceActionResult<Void>>{
                override fun onSuccess(t: DeviceManager.DeviceActionResult<Void>?) {
                    SDKLog.i(TAG, "startSurround success")
                    onSuccess.invoke()
                }

                override fun onFailure(failure: DeviceManager.DeviceActionFailure?) {
                    onError.invoke("startSurround error:${failure?.msg?:""}")
                    SDKLog.i(TAG, "startSurround error:${failure?.msg?:""}")
                }
            }
        )

    }

    /**
     * Pause surround
     */
    fun pauseSurround(deviceId: Int,onSuccess: () -> Unit, onError: (str: String) -> Unit) {

        val key = KeyTools.createKey(MissionManagerKey.KeyOrbitModelingPauseMsg)
        DeviceManager.getMultiDeviceOperator().performActionList(
            arrayListOf(deviceId),key,null,object :DeviceManager.CompletionCallbackWithParam<DeviceManager.DeviceActionResult<Void>>{
                override fun onSuccess(t: DeviceManager.DeviceActionResult<Void>?) {
                    SDKLog.i(TAG, "pauseSurround success")
                    onSuccess.invoke()
                }

                override fun onFailure(failure: DeviceManager.DeviceActionFailure?) {
                    onError.invoke("pauseSurround error:${failure?.msg?:""}")
                    SDKLog.i(TAG, "pauseSurround error:${failure?.msg?:""}")
                }
            }
        )

    }

    /**
     * Resume surround
     */
    fun resumeSurround(deviceId: Int,onSuccess: () -> Unit, onError: (str: String) -> Unit) {

        val key = KeyTools.createKey(MissionManagerKey.KeyOrbitModelingContinueMsg)
        DeviceManager.getMultiDeviceOperator().performActionList(
            arrayListOf(deviceId),key,null,object :DeviceManager.CompletionCallbackWithParam<DeviceManager.DeviceActionResult<Void>>{
                override fun onSuccess(t: DeviceManager.DeviceActionResult<Void>?) {
                    SDKLog.i(TAG, "resumeSurround success")
                    onSuccess.invoke()
                }

                override fun onFailure(failure: DeviceManager.DeviceActionFailure?) {
                    SDKLog.i(TAG, "resumeSurround getKeyManager error:${failure?.msg?:""}")
                    onError.invoke(failure?.msg?:"resumeSurround error:${failure?.msg?:""}")
                }
            }
        )


    }


}