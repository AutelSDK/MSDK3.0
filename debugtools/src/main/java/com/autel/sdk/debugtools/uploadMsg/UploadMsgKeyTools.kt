package com.autel.sdk.debugtools.uploadMsg


import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.*
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.alink.enums.AirLinkMatchStatusEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.bean.*
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.enums.AFStateEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.enums.DisplayModeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.enums.ExposureEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.common.bean.RCBandInfoTypeBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.common.bean.RockerCalibrationStateNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.common.bean.WarningAtom
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateHFNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateLFNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneWarningStateNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.FlightControlStatusInfo
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.gimbal.bean.DroneGimbalStateBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.MissionWaypointStatusReportNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.bean.HardwareButtonInfoBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.bean.RCHardwareStateNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.bean.RCStateNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.vision.bean.VisionRadarInfoBean

/**
 * uploading messages passing key identifiers
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/10/11
 */
object UploadMsgKeyTools {
    /**--------------------------- Flight control ----------------------------*/
    /**
     * Flight control high-frequency messages
     */
    val KeyFlightControlSystemHFState: AutelKey<DroneSystemStateHFNtfyBean> = KeyTools.createKey(
        CommonKey.KeyDroneSystemStatusHFNtfy
    )

    /**
     * Flight control low-frequency messages
     */
    val KeyFlightControlSystemLFState: AutelKey<DroneSystemStateLFNtfyBean> = KeyTools.createKey(
        CommonKey.KeyDroneSystemStatusLFNtfy
    )

    /**
     * Drone working state info
     */
    val KeyDroneWorkStatusInfoReport: AutelKey<FlightControlStatusInfo> = KeyTools.createKey(
        CommonKey.KeyDroneWorkStatusInfoReport
    )

    /**
     * Flight control warning messages
     */
    val KeyFlightStateWarning: AutelKey<DroneWarningStateNtfyBean> = KeyTools.createKey(
        CommonKey.KeyDroneWarningMFNtfy
    )

    //Drone warning notification
    val KeyDroneWarning: AutelKey<List<WarningAtom>> = KeyTools.createKey(
        CommonKey.KeyDroneWarning
    )

    //Drone real-time warning notification
    val KeyDroneRuntimeWarning: AutelKey<WarningAtom> = KeyTools.createKey(
        CommonKey.KeyDroneRuntimeWarning
    )

    /**--------------------------- Mission ----------------------------------*/
    /**
     * Mission execution info
     */
    val KeyStatusReportNtfy: AutelKey<MissionWaypointStatusReportNtfyBean> = KeyTools.createKey(
        FlightMissionKey.KeyStatusReportNtfy
    )


    /**--------------------------- Camera -----------------------------------*/
    /**
     * Camera message reporting
     */
    val KeyCameraStatus: AutelKey<CameraStatusBean> = KeyTools.createKey(CameraKey.KeyCameraStatus)

    /**
     * Capture file info reporting
     */
    val KeyPhotoFileInfo: AutelKey<PhotoFileInfoBean> =
        KeyTools.createKey(CameraKey.KeyPhotoFileInfo)

    /**
     * Capture status reporting
     */
    val KeyTakePhotoStatus: AutelKey<TakePhotoStatusBean> =
        KeyTools.createKey(CameraKey.KeyTakePhotoStatus)

    /**
     * Record status reporting
     */
    val KeyRecordStatus: AutelKey<RecordStatusBean> = KeyTools.createKey(CameraKey.KeyRecordStatus)

    /**
     * Record file info reporting
     */
    val KeyRecordFileInfo: AutelKey<RecordFileInfoBean> =
        KeyTools.createKey(CameraKey.KeyRecordFileInfo)

    /**
     * SD card status reporting
     */
    val KeySDCardStatus: AutelKey<CardStatusBean> = KeyTools.createKey(CameraKey.KeySDCardStatus)

    /**
     * MMC status reporting
     */
    val KeyMMCStatusInfo: AutelKey<CardStatusBean> = KeyTools.createKey(CameraKey.KeyMMCStatusInfo)

    /**
     * Timed-shot countdown reporting
     */
    val KeyIntervalShotStatus: AutelKey<Int> = KeyTools.createKey(CameraKey.KeyIntervalShotStatus)

    /**
     * Panorama status reporting
     */
    val KeyPanoramaStatus: AutelKey<PanoramaStatusBean> =
        KeyTools.createKey(CameraKey.KeyPanoramaStatus)

    /**
     * Hyperlapse status reporting
     */
    val KeyDelayShotStatus: AutelKey<DelayShotStatusBean> =
        KeyTools.createKey(CameraKey.KeyDelayShotStatus)

    /**
     * Camera reset status reporting
     */
    val KeyResetCameraState: AutelKey<Boolean> = KeyTools.createKey(CameraKey.KeyResetCameraState)

    /**
     * AE/AF state change
     */
    val KeyAeAfStatusChange: AutelKey<CameraAFAEStatusBean> =
        KeyTools.createKey(CameraKey.KeyAeAfStatusChange)

    /**
     * Temperature alarm event
     */
    val KeyTempAlarm: AutelKey<TempAlarmBean> = KeyTools.createKey(CameraKey.KeyTempAlarm)

    /**
     * Moving hyperlapse status
     */
    val KeyMotionDelayShotStatus: AutelKey<MotionDelayShotBean> =
        KeyTools.createKey(CameraKey.KeyMotionDelayShotStatus)

    /**
     * Camera exposure status
     */
    val KeyPhotoExposure: AutelKey<ExposureEnum> = KeyTools.createKey(CameraKey.KeyPhotoExposure)

    /**
     * Mission waypoint recording info reporting
     */
    val KeyMissionRecordWaypoint: AutelKey<MissionRecordWaypointBean> =
        KeyTools.createKey(CameraKey.KeyMissionRecordWaypoint)

    /**
     * Camera focus status
     */
    val KeyAFState: AutelKey<AFStateEnum> = KeyTools.createKey(CameraKey.KeyAFState)

    /**
     * Camera mode switch reporting
     */
    val KeyCameraModeSwitch: AutelKey<CameraModeSwitchBean> =
        KeyTools.createKey(CameraKey.KeyCameraModeSwitch)

    /**
     * Display mode switch reporting
     */
    val KeyDisplayModeSwitch: AutelKey<DisplayModeEnum> =
        KeyTools.createKey(CameraKey.KeyDisplayModeSwitch)

    /**
     * IR camera temperature info reporting
     */
    val KeyInfraredCameraTempInfo: AutelKey<InfraredCameraTempInfoBean> = KeyTools.createKey(
        CameraKey.KeyInfraredCameraTempInfo
    )

    /**
     * Pro parameter info reporting
     */
    val KeyProfessionalParamInfo: AutelKey<ProfessionalParamInfoBean> =
        KeyTools.createKey(CameraKey.KeyProfessionalParamInfo)

    /**
     * Storage status info reporting
     */
    val KeyStorageStatusInfo: AutelKey<StorageStatusInfoBean> =
        KeyTools.createKey(CameraKey.KeyStorageStatusInfo)

    /**
     * Spot metering reporting
     */
    val KeyLocationMeterInfo: AutelKey<MeteringPointBean> =
        KeyTools.createKey(CameraKey.KeyLocationMeterInfo)

    /**
     * White balance parameter reporting
     */
    val KeyWhiteBalanceNtfy: AutelKey<WhiteBalanceBean> =
        KeyTools.createKey(CameraKey.KeyWhiteBalanceNtfy)

    /**
     * AELock info reporting
     */
    val KeyAeLockNtfy: AutelKey<Boolean> = KeyTools.createKey(CameraKey.KeyAeLockNtfy)

    /**
     * HDR info reporting
     */
    val KeyHdrNtfy: AutelKey<Boolean> = KeyTools.createKey(CameraKey.KeyHdrNtfy)

    /**
     * ROI config reporting
     */
    val KeyRoiNtfy: AutelKey<ROIBean> = KeyTools.createKey(CameraKey.KeyRoiNtfy)

    /**
     * Photo resolution info reporting
     */
    val KeyPhotoResolutionNtfy: AutelKey<PhotoResolutionEnum> =
        KeyTools.createKey(CameraKey.KeyPhotoResolutionNtfy)

    /**
     * Video resolution info reporting
     */
    val KeyVideoResolutionNtfy: AutelKey<VideoResolutionBean> =
        KeyTools.createKey(CameraKey.KeyVideoResolutionNtfy)

    /**--------------------------- Gimbal -----------------------------------*/

    /**
     * Gimbal reporting parameters
     */
    val KeyGimbalHeatBeat: AutelKey<DroneGimbalStateBean> =
        KeyTools.createKey(GimbalKey.KeyHeatBeat)

    /**--------------------------- Vision -----------------------------------*/

    /**
     * Vision warnings
     */
    val KeyWarning: AutelKey<List<VisionRadarInfoBean>> =
        KeyTools.createKey(VisionKey.KeyReportEmergency)

    /**--------------------------- Remote controller -------------------------*/

    /**
     * RC fixed-frequency reporting
     */
    val KeyRCHardwareStateReport: AutelKey<RCHardwareStateNtfyBean> =
        KeyTools.createKey(RemoteControllerKey.KeyRCHardwareState)

    /**
     * RC custom button reporting parameters
     */
    val KeyRCHardwareButtonInfo: AutelKey<HardwareButtonInfoBean> =
        KeyTools.createKey(RemoteControllerKey.KeyRCHardwareInfo)

    /**
     * RC status reporting parameters
     */
    val KeyRCState: AutelKey<RCStateNtfyBean> = KeyTools.createKey(RemoteControllerKey.KeyRCState)

    /**
     * RC calibration status reporting
     */
    val KeyRCRockerCalibrationState: AutelKey<RockerCalibrationStateNtfyBean> =
        KeyTools.createKey(RemoteControllerKey.KeyRCRockerCalibrationState)

    /**
     * RC pairing
     */
    val keyLinkMatch: AutelKey<AirLinkMatchStatusEnum> =
        KeyTools.createKey(AirLinkKey.KeyALinkMatchingStatus)

    /**
     * RC bandwidth and type reporting
     */
    val KeyRCBandInfoType: AutelKey<RCBandInfoTypeBean> =
        KeyTools.createKey(RemoteControllerKey.KeyRCBandInfoTypeNtfy)
}