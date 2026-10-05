package com.autel.sdk.debugtools.uploadMsg

import com.autel.drone.sdk.vmodelx.interfaces.IBaseDevice
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.bean.*
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.enums.AFStateEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.enums.DisplayModeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.enums.ExposureEnum
import com.autel.sdk.debugtools.helper.DroneUploadMsgData
import com.autel.sdk.debugtools.uploadMsg.inter.IAircraftUpMsgHandler

/**
 * camera status with aircraft/drone messages
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/10/11
 */
object CameraStatusUpMsgManager : IAircraftUpMsgHandler {
    /**
     * Camera system status
     */
    val cameraStatusData = DroneUploadMsgData<CameraStatusBean>()

    /**
     * Capture file info
     */
    val cameraTakePhotoData = DroneUploadMsgData<PhotoFileInfoBean>()

    /**
     * Capture status reporting
     */
    val takePhotoStatusData = DroneUploadMsgData<TakePhotoStatusBean>()

    /**
     * Record status reporting
     */
    val recordStatusData = DroneUploadMsgData<RecordStatusBean>()

    /**
     * Record file info
     */
    val recordFileInfoData = DroneUploadMsgData<RecordFileInfoBean>()

    /**
     * SD card status
     */
    val SDCardStatusData = DroneUploadMsgData<CardStatusBean>()

    /**
     * MMC status reporting
     */
    val MMCStatusData = DroneUploadMsgData<CardStatusBean>()

    /**
     * Timed-shot countdown reporting
     */
    val intervalShotStatusData = DroneUploadMsgData<Int>()

    /**
     * Panorama status info reporting
     */
    val panoramaStatusData = DroneUploadMsgData<PanoramaStatusBean>()

    /**
     * Hyperlapse status info reporting
     */
    val delayShotStatusData = DroneUploadMsgData<DelayShotStatusBean>()

    /**
     * Camera reset status reporting
     */
    val resetCameraStateData = DroneUploadMsgData<Boolean>()

    /**
     * AE/AF state change
     */
    val aeAfStatusData = DroneUploadMsgData<CameraAFAEStatusBean>()

    /**
     * Temperature alarm event
     */
    val tempAlarmData = DroneUploadMsgData<TempAlarmBean>()

    /**
     * Moving hyperlapse status reporting
     */
    val motionDelayShotData = DroneUploadMsgData<MotionDelayShotBean>()

    /**
     * Capture over/under-exposure
     */
    val photoExposureData = DroneUploadMsgData<ExposureEnum>()

    /**
     * Mission waypoint recording info reporting
     */
    val missionRecordWaypointData = DroneUploadMsgData<MissionRecordWaypointBean>()

    /**
     * Camera focus status
     */
    val aFStateData = DroneUploadMsgData<AFStateEnum>()

    /**
     * Camera mode switch
     */
    val cameraModeSwitchData = DroneUploadMsgData<CameraModeSwitchBean>()

    /**
     * Display mode
     */
    val displayModeSwitchData = DroneUploadMsgData<DisplayModeEnum>()

    /**
     * IR camera temperature info
     */
    val infraredCameraTempInfoData = DroneUploadMsgData<InfraredCameraTempInfoBean>()

    /**
     * Pro parameter info reporting
     */
    val professionalParamData = DroneUploadMsgData<ProfessionalParamInfoBean>()

    /**
     * Storage status info
     */
    val storageStatusInfoData = DroneUploadMsgData<StorageStatusInfoBean>()

    /**
     * Metering point
     */
    val locationMeterInfoData = DroneUploadMsgData<MeteringPointBean>()

    /**
     * White balance
     */
    val whiteBalanceData = DroneUploadMsgData<WhiteBalanceBean>()

    /**
     * AE lock
     */
    val aeLockData = DroneUploadMsgData<Boolean>()

    /**
     * HDR info reporting
     */
    val hdrData = DroneUploadMsgData<Boolean>()

    /**
     * ROI config
     */
    val roiData = DroneUploadMsgData<ROIBean>()

    /**
     * Photo resolution info reporting
     */
    val photoResolutionData = DroneUploadMsgData<PhotoResolutionEnum>()

    /**
     * Video resolution info
     */
    val videoResolutionData = DroneUploadMsgData<VideoResolutionBean>()

    override fun listenMsg(aircraftDevice: IBaseDevice) {
        val keyManager = aircraftDevice.getKeyManager()
        keyManager.listen(
            UploadMsgKeyTools.KeyCameraStatus,
            object : CommonCallbacks.KeyListener<CameraStatusBean> {
                override fun onValueChange(
                    oldValue: CameraStatusBean?,
                    newValue: CameraStatusBean
                ) {
                    newValue?.let {
                        cameraStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })

        keyManager.listen(
            UploadMsgKeyTools.KeyPhotoFileInfo,
            object : CommonCallbacks.KeyListener<PhotoFileInfoBean> {
                override fun onValueChange(
                    oldValue: PhotoFileInfoBean?,
                    newValue: PhotoFileInfoBean
                ) {
                    newValue?.let {
                        cameraTakePhotoData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyTakePhotoStatus,
            object : CommonCallbacks.KeyListener<TakePhotoStatusBean> {
                override fun onValueChange(
                    oldValue: TakePhotoStatusBean?,
                    newValue: TakePhotoStatusBean
                ) {
                    newValue?.let {
                        takePhotoStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyRecordStatus,
            object : CommonCallbacks.KeyListener<RecordStatusBean> {
                override fun onValueChange(
                    oldValue: RecordStatusBean?,
                    newValue: RecordStatusBean
                ) {
                    newValue?.let {
                        recordStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyRecordFileInfo,
            object : CommonCallbacks.KeyListener<RecordFileInfoBean> {
                override fun onValueChange(
                    oldValue: RecordFileInfoBean?,
                    newValue: RecordFileInfoBean
                ) {
                    newValue?.let {
                        recordFileInfoData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeySDCardStatus,
            object : CommonCallbacks.KeyListener<CardStatusBean> {
                override fun onValueChange(
                    oldValue: CardStatusBean?,
                    newValue: CardStatusBean
                ) {
                    newValue?.let {
                        SDCardStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyMMCStatusInfo,
            object : CommonCallbacks.KeyListener<CardStatusBean> {
                override fun onValueChange(
                    oldValue: CardStatusBean?,
                    newValue: CardStatusBean
                ) {
                    newValue?.let {
                        MMCStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyIntervalShotStatus,
            object : CommonCallbacks.KeyListener<Int> {
                override fun onValueChange(
                    oldValue: Int?,
                    newValue: Int
                ) {
                    newValue?.let {
                        intervalShotStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyPanoramaStatus,
            object : CommonCallbacks.KeyListener<PanoramaStatusBean> {
                override fun onValueChange(
                    oldValue: PanoramaStatusBean?,
                    newValue: PanoramaStatusBean
                ) {
                    newValue?.let {
                        panoramaStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyDelayShotStatus,
            object : CommonCallbacks.KeyListener<DelayShotStatusBean> {
                override fun onValueChange(
                    oldValue: DelayShotStatusBean?,
                    newValue: DelayShotStatusBean
                ) {
                    newValue?.let {
                        delayShotStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyResetCameraState,
            object : CommonCallbacks.KeyListener<Boolean> {
                override fun onValueChange(
                    oldValue: Boolean?,
                    newValue: Boolean
                ) {
                    newValue?.let {
                        resetCameraStateData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyAeAfStatusChange,
            object : CommonCallbacks.KeyListener<CameraAFAEStatusBean> {
                override fun onValueChange(
                    oldValue: CameraAFAEStatusBean?,
                    newValue: CameraAFAEStatusBean
                ) {
                    newValue?.let {
                        aeAfStatusData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyTempAlarm,
            object : CommonCallbacks.KeyListener<TempAlarmBean> {
                override fun onValueChange(
                    oldValue: TempAlarmBean?,
                    newValue: TempAlarmBean
                ) {
                    newValue?.let {
                        tempAlarmData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyMotionDelayShotStatus,
            object : CommonCallbacks.KeyListener<MotionDelayShotBean> {
                override fun onValueChange(
                    oldValue: MotionDelayShotBean?,
                    newValue: MotionDelayShotBean
                ) {
                    newValue?.let {
                        motionDelayShotData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyPhotoExposure,
            object : CommonCallbacks.KeyListener<ExposureEnum> {
                override fun onValueChange(
                    oldValue: ExposureEnum?,
                    newValue: ExposureEnum
                ) {
                    newValue?.let {
                        photoExposureData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyMissionRecordWaypoint,
            object : CommonCallbacks.KeyListener<MissionRecordWaypointBean> {
                override fun onValueChange(
                    oldValue: MissionRecordWaypointBean?,
                    newValue: MissionRecordWaypointBean
                ) {
                    newValue?.let {
                        missionRecordWaypointData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyAFState,
            object : CommonCallbacks.KeyListener<AFStateEnum> {
                override fun onValueChange(
                    oldValue: AFStateEnum?,
                    newValue: AFStateEnum
                ) {
                    newValue?.let {
                        aFStateData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyCameraModeSwitch,
            object : CommonCallbacks.KeyListener<CameraModeSwitchBean> {
                override fun onValueChange(
                    oldValue: CameraModeSwitchBean?,
                    newValue: CameraModeSwitchBean
                ) {
                    newValue?.let {
                        cameraModeSwitchData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyDisplayModeSwitch,
            object : CommonCallbacks.KeyListener<DisplayModeEnum> {
                override fun onValueChange(
                    oldValue: DisplayModeEnum?,
                    newValue: DisplayModeEnum
                ) {
                    newValue?.let {
                        displayModeSwitchData.putValue(aircraftDevice, it)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyInfraredCameraTempInfo,
            object : CommonCallbacks.KeyListener<InfraredCameraTempInfoBean> {
                override fun onValueChange(
                    oldValue: InfraredCameraTempInfoBean?,
                    newValue: InfraredCameraTempInfoBean
                ) {
                    newValue?.let {
                        infraredCameraTempInfoData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyProfessionalParamInfo,
            object : CommonCallbacks.KeyListener<ProfessionalParamInfoBean> {
                override fun onValueChange(
                    oldValue: ProfessionalParamInfoBean?,
                    newValue: ProfessionalParamInfoBean
                ) {
                    newValue?.let {
                        professionalParamData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyStorageStatusInfo,
            object : CommonCallbacks.KeyListener<StorageStatusInfoBean> {
                override fun onValueChange(
                    oldValue: StorageStatusInfoBean?,
                    newValue: StorageStatusInfoBean
                ) {
                    newValue?.let {
                        storageStatusInfoData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyLocationMeterInfo,
            object : CommonCallbacks.KeyListener<MeteringPointBean> {
                override fun onValueChange(
                    oldValue: MeteringPointBean?,
                    newValue: MeteringPointBean
                ) {
                    newValue?.let {
                        locationMeterInfoData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyWhiteBalanceNtfy,
            object : CommonCallbacks.KeyListener<WhiteBalanceBean> {
                override fun onValueChange(
                    oldValue: WhiteBalanceBean?,
                    newValue: WhiteBalanceBean
                ) {
                    newValue?.let {
                        whiteBalanceData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyAeLockNtfy,
            object : CommonCallbacks.KeyListener<Boolean> {
                override fun onValueChange(
                    oldValue: Boolean?,
                    newValue: Boolean
                ) {
                    newValue?.let {
                        aeLockData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyHdrNtfy,
            object : CommonCallbacks.KeyListener<Boolean> {
                override fun onValueChange(
                    oldValue: Boolean?,
                    newValue: Boolean
                ) {
                    newValue?.let {
                        hdrData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyRoiNtfy,
            object : CommonCallbacks.KeyListener<ROIBean> {
                override fun onValueChange(
                    oldValue: ROIBean?,
                    newValue: ROIBean
                ) {
                    newValue?.let {
                        roiData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyPhotoResolutionNtfy,
            object : CommonCallbacks.KeyListener<PhotoResolutionEnum> {
                override fun onValueChange(
                    oldValue: PhotoResolutionEnum?,
                    newValue: PhotoResolutionEnum
                ) {
                    newValue?.let {
                        photoResolutionData.putValue(aircraftDevice, newValue)
                    }
                }
            })
        keyManager.listen(
            UploadMsgKeyTools.KeyVideoResolutionNtfy,
            object : CommonCallbacks.KeyListener<VideoResolutionBean> {
                override fun onValueChange(
                    oldValue: VideoResolutionBean?,
                    newValue: VideoResolutionBean
                ) {
                    newValue?.let {
                        videoResolutionData.putValue(aircraftDevice, newValue)
                    }
                }
            })
    }

    override fun cancelListenMsg(aircraftDevice: IBaseDevice) {
        cameraStatusData.remove(aircraftDevice)
        cameraTakePhotoData.remove(aircraftDevice)
        takePhotoStatusData.remove(aircraftDevice)
        recordStatusData.remove(aircraftDevice)
        recordFileInfoData.remove(aircraftDevice)
        SDCardStatusData.remove(aircraftDevice)
        MMCStatusData.remove(aircraftDevice)
        intervalShotStatusData.remove(aircraftDevice)
        panoramaStatusData.remove(aircraftDevice)
        delayShotStatusData.remove(aircraftDevice)
        resetCameraStateData.remove(aircraftDevice)
        aeAfStatusData.remove(aircraftDevice)
        tempAlarmData.remove(aircraftDevice)
        motionDelayShotData.remove(aircraftDevice)
        photoExposureData.remove(aircraftDevice)
        missionRecordWaypointData.remove(aircraftDevice)
        aFStateData.remove(aircraftDevice)
        cameraModeSwitchData.remove(aircraftDevice)
        displayModeSwitchData.remove(aircraftDevice)
        infraredCameraTempInfoData.remove(aircraftDevice)
        professionalParamData.remove(aircraftDevice)
        storageStatusInfoData.remove(aircraftDevice)
        locationMeterInfoData.remove(aircraftDevice)
        whiteBalanceData.remove(aircraftDevice)
        aeLockData.remove(aircraftDevice)
        hdrData.remove(aircraftDevice)
        roiData.remove(aircraftDevice)
        photoResolutionData.remove(aircraftDevice)
        videoResolutionData.remove(aircraftDevice)
    }

}