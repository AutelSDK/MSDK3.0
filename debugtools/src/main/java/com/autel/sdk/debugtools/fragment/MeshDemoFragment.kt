package com.autel.sdk.debugtools.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.KeyManagerExt
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.DeviceActionFailureList
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.DeviceActionResultList
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.ext.create
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.common.bean.DroneLedStatusBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.common.enums.DroneLedStatusEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.common.enums.DroneLedTypeEnum
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.sdk.debugtools.helper.PlayerWeightHelper.applySplitScreenWeight
import com.autel.sdk.debugtools.R
import com.robotics.sdk.ros.data.ControlAction
import com.robotics.sdk.ros.data.DeviceControlAction
import com.robotics.sdk.ros.data.SelectAction

/**
 * Multi-device mesh networking sample Fragment
 *
 * Features:
 * 1. Show the networked device list (ALL / single)
 * 2. Tap a device to switch its wide-angle / IR stream
 * 3. Three display modes: dual (wide + IR), wide only, IR only
 * 4. Request control of one device or all devices
 * 5. Control the downward and night lights of one or all devices
 *
 * Usage:
 *   Add this Fragment to an Activity, e.g.:
 *   supportFragmentManager.beginTransaction()
 *       .replace(R.id.container, MeshDemoFragment())
 *       .commit()
 */
class MeshDemoFragment : AutelFragment() {

    companion object {
        private const val TAG = "MeshDemoFragment"
    }

    // ========== UI references ==========
    private lateinit var llDeviceTabs: LinearLayout
    private lateinit var flVisibleVideo: FrameLayout
    private lateinit var flInfraredVideo: FrameLayout
    private lateinit var playerViewVisible: AutelPlayerView
    private lateinit var playerViewInfrared: AutelPlayerView
    private lateinit var tvVisibleLabel: TextView
    private lateinit var tvInfraredLabel: TextView
    private lateinit var tvNoVideoVisible: TextView
    private lateinit var tvNoVideoInfrared: TextView
    private lateinit var dividerVideo: View
    private lateinit var btnDualView: TextView
    private lateinit var btnVisibleOnly: TextView
    private lateinit var btnInfraredOnly: TextView
    private lateinit var btnRequestControl: TextView
    private lateinit var btnDownLight: TextView
    private lateinit var btnNightLight: TextView
    private lateinit var tvStatus: TextView

    // ========== Players ==========
    private var visiblePlayer: AutelPlayer? = null
    private var infraredPlayer: AutelPlayer? = null

    // ========== State ==========
    private var selectedDevice: IAutelDroneDevice? = null // null = ALL
    private var isAllMode = true
    private var downLightOn = false
    private var nightLightOn = false

    private enum class VideoMode { DUAL, VISIBLE_ONLY, INFRARED_ONLY }
    private var videoMode = VideoMode.DUAL

    // ========== Device listeners ==========
    private val droneListener = object : IAutelDroneListener {
        override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
            safePost { refreshDeviceTabs() }
        }
        override fun onDroneCreate(drone: IAutelDroneDevice) {
            safePost { refreshDeviceTabs() }
        }
        override fun onDroneDestroy(drone: IAutelDroneDevice) {
            safePost {
                if (selectedDevice == drone) {
                    selectAll()
                }
                refreshDeviceTabs()
            }
        }
    }

    // =============================================
    // Fragment lifecycle
    // =============================================

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_mesh_demo, container, false)
        bindViews(root)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()
        DeviceManager.getDeviceManager().addDroneListener(droneListener)
        refreshDeviceTabs()
        selectAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAllPlayers()
        DeviceManager.getDeviceManager().removeDroneListener(droneListener)
    }

    // =============================================
    // View binding
    // =============================================

    private fun bindViews(root: View) {
        llDeviceTabs = root.findViewById(R.id.ll_device_tabs)
        flVisibleVideo = root.findViewById(R.id.fl_visible_video)
        flInfraredVideo = root.findViewById(R.id.fl_infrared_video)
        playerViewVisible = root.findViewById(R.id.player_view_visible)
        playerViewInfrared = root.findViewById(R.id.player_view_infrared)
        tvVisibleLabel = root.findViewById(R.id.tv_visible_label)
        tvInfraredLabel = root.findViewById(R.id.tv_infrared_label)
        tvNoVideoVisible = root.findViewById(R.id.tv_no_video_visible)
        tvNoVideoInfrared = root.findViewById(R.id.tv_no_video_infrared)
        dividerVideo = root.findViewById(R.id.divider_video)
        btnDualView = root.findViewById(R.id.btn_dual_view)
        btnVisibleOnly = root.findViewById(R.id.btn_visible_only)
        btnInfraredOnly = root.findViewById(R.id.btn_infrared_only)
        btnRequestControl = root.findViewById(R.id.btn_request_control)
        btnDownLight = root.findViewById(R.id.btn_down_light)
        btnNightLight = root.findViewById(R.id.btn_night_light)
        tvStatus = root.findViewById(R.id.tv_status)
    }

    // =============================================
    // Init event listeners
    // =============================================

    private fun initListeners() {
        // ----- Video mode switch -----
        btnDualView.setOnClickListener { switchVideoMode(VideoMode.DUAL) }
        btnVisibleOnly.setOnClickListener { switchVideoMode(VideoMode.VISIBLE_ONLY) }
        btnInfraredOnly.setOnClickListener { switchVideoMode(VideoMode.INFRARED_ONLY) }

        // ----- Tap wide/IR area => toggle fullscreen -----
        flVisibleVideo.setOnClickListener {
            if (videoMode == VideoMode.DUAL) switchVideoMode(VideoMode.VISIBLE_ONLY)
            else switchVideoMode(VideoMode.DUAL)
        }
        flInfraredVideo.setOnClickListener {
            if (videoMode == VideoMode.DUAL) switchVideoMode(VideoMode.INFRARED_ONLY)
            else switchVideoMode(VideoMode.DUAL)
        }

        // ----- Request control -----
        btnRequestControl.setOnClickListener { requestControl() }

        // ----- Downward light -----
        btnDownLight.setOnClickListener { toggleDownLight() }

        // ----- Night light -----
        btnNightLight.setOnClickListener { toggleNightLight() }
    }

    // =============================================
    // Device selector bar
    // =============================================

    private fun refreshDeviceTabs() {
        if (!isAdded || view == null) return
        llDeviceTabs.removeAllViews()
        val devices = DeviceManager.getDeviceManager().getDroneDevices()

        // ALL button
        llDeviceTabs.addView(createTabView("ALL (${devices.size})", isAllMode) {
            selectAll()
        })

        // Per-device buttons
        devices.forEachIndexed { index, device ->
            val name = device.getDeviceInfoBean()?.deviceName ?: getString(R.string.mesh_drone_name_fallback, index + 1)
            val hasCtrl = device.hasControlRight()
            val label = "$name\nH:${formatAlt(device)} ${if (hasCtrl) "✓" else "✗"}"
            val selected = !isAllMode && selectedDevice == device
            llDeviceTabs.addView(createTabView(label, selected) {
                selectDevice(device)
            })
        }
    }

    private fun createTabView(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 11f
            setTextColor(if (selected) Color.WHITE else Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(24, 4, 24, 4)
            setBackgroundColor(if (selected) Color.parseColor("#E94560") else Color.parseColor("#253956"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            lp.marginEnd = 6
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun formatAlt(device: IAutelDroneDevice): String {
        val heartbeat = device.getKeyManager()
            ?.getCacheValue(CommonKey.KeyMissionHeartbeatStatusReport.create())
        // distanceToHome is in 0.1m units
        val dist = (heartbeat?.distanceToHome ?: 0) * 0.1
        return String.format("%.1fm", dist)
    }

    // =============================================
    // Device selection logic
    // =============================================

    private fun selectAll() {
        isAllMode = true
        selectedDevice = null
        refreshDeviceTabs()
        updateStatusText(getString(R.string.mesh_select_all))
        // In ALL mode use the first drone stream as preview
        val first = DeviceManager.getDeviceManager().getDroneDevices().firstOrNull()
        if (first != null) {
            startVideoForDevice(first)
        } else {
            stopAllPlayers()
        }
    }

    private fun selectDevice(device: IAutelDroneDevice) {
        isAllMode = false
        selectedDevice = device
        refreshDeviceTabs()
        updateStatusText(getString(R.string.mesh_select_device, device.getDeviceInfoBean()?.deviceName, device.getDeviceNumber()))
        startVideoForDevice(device)
    }

    // =============================================
    // Stream playback (wide-angle + IR)
    // =============================================

    private fun startVideoForDevice(device: IAutelDroneDevice) {
        val deviceId = device.getDeviceNumber().toString()

        // Get the wide-angle lens ID
        val zoomLensType = getDefaultZoomLensType(device)
        val zoomLensId = zoomLensType?.let { getCameraLensId(device, it) }

        // Get the IR lens ID
        val thermalLensType = getDefaultThermalLensType(device)
        val thermalLensId = thermalLensType?.let { getCameraLensId(device, it) }

        // ---- Wide-angle player ----
        if (zoomLensId != null) {
            tvNoVideoVisible.visibility = View.GONE
            tvVisibleLabel.text = lensTypeName(zoomLensType)
            val player = visiblePlayer
            if (player == null) {
                val newPlayer = AutelPlayer(deviceId, zoomLensId)
                newPlayer.addVideoView(playerViewVisible)
                newPlayer.startPlayer()
                newPlayer.applySplitScreenWeight()
                visiblePlayer = newPlayer
            } else {
                player.switchCamera(deviceId, zoomLensId)
                player.applySplitScreenWeight()
            }
        } else {
            stopVisiblePlayer()
            tvNoVideoVisible.visibility = View.VISIBLE
        }

        // ---- IR player ----
        if (thermalLensId != null) {
            tvNoVideoInfrared.visibility = View.GONE
            tvInfraredLabel.text = lensTypeName(thermalLensType)
            val player = infraredPlayer
            if (player == null) {
                val newPlayer = AutelPlayer(deviceId, thermalLensId)
                newPlayer.addVideoView(playerViewInfrared)
                newPlayer.startPlayer()
                newPlayer.applySplitScreenWeight()
                infraredPlayer = newPlayer
            } else {
                player.switchCamera(deviceId, thermalLensId)
                player.applySplitScreenWeight()
            }
        } else {
            stopInfraredPlayer()
            tvNoVideoInfrared.visibility = View.VISIBLE
        }

        applyVideoModeLayout()
    }

    private fun stopAllPlayers() {
        stopVisiblePlayer()
        stopInfraredPlayer()
    }

    private fun stopVisiblePlayer() {
        visiblePlayer?.let {
            it.removeVideoView()
            it.stopPlayer()
        }
        visiblePlayer = null
    }

    private fun stopInfraredPlayer() {
        infraredPlayer?.let {
            it.removeVideoView()
            it.stopPlayer()
        }
        infraredPlayer = null
    }

    // =============================================
    // Video display mode (dual / wide only / IR only)
    // =============================================

    private fun switchVideoMode(mode: VideoMode) {
        videoMode = mode
        applyVideoModeLayout()
        highlightModeButton(mode)
    }

    private fun applyVideoModeLayout() {
        when (videoMode) {
            VideoMode.DUAL -> {
                flVisibleVideo.visibility = View.VISIBLE
                dividerVideo.visibility = View.VISIBLE
                flInfraredVideo.visibility = View.VISIBLE
                (flVisibleVideo.layoutParams as LinearLayout.LayoutParams).weight = 1f
                (flInfraredVideo.layoutParams as LinearLayout.LayoutParams).weight = 1f
            }
            VideoMode.VISIBLE_ONLY -> {
                flVisibleVideo.visibility = View.VISIBLE
                dividerVideo.visibility = View.GONE
                flInfraredVideo.visibility = View.GONE
                (flVisibleVideo.layoutParams as LinearLayout.LayoutParams).weight = 1f
            }
            VideoMode.INFRARED_ONLY -> {
                flVisibleVideo.visibility = View.GONE
                dividerVideo.visibility = View.GONE
                flInfraredVideo.visibility = View.VISIBLE
                (flInfraredVideo.layoutParams as LinearLayout.LayoutParams).weight = 1f
            }
        }
        flVisibleVideo.requestLayout()
        flInfraredVideo.requestLayout()
    }

    private fun highlightModeButton(mode: VideoMode) {
        val active = Color.parseColor("#E94560")
        val inactive = Color.parseColor("#334756")
        btnDualView.setBackgroundColor(if (mode == VideoMode.DUAL) active else inactive)
        btnVisibleOnly.setBackgroundColor(if (mode == VideoMode.VISIBLE_ONLY) active else inactive)
        btnInfraredOnly.setBackgroundColor(if (mode == VideoMode.INFRARED_ONLY) active else inactive)
    }

    // =============================================
    // Request control
    // =============================================

    private fun requestControl() {
        val netMeshMgr = DeviceManager.getMultiDeviceOperator().getNetMeshManager()

        if (isAllMode) {
            // ALL mode => group control
            updateStatusText(getString(R.string.mesh_requesting_all_control))
            val ids = DeviceManager.getDeviceManager().getDroneDevices().map { it.getDeviceNumber() }
            val actions = ids.map {
                DeviceControlAction(it, ControlAction.Control, SelectAction.Select)
            }
            netMeshMgr.requestControlGroup(actions) { success, statusCode ->
                safePost {
                    if (success) {
                        updateStatusText(getString(R.string.mesh_all_control_success))
                        refreshDeviceTabs()
                    } else {
                        updateStatusText(getString(R.string.mesh_control_failed, statusCode.msg))
                    }
                }
            }
        } else {
            // Single mode => single control
            val deviceId = selectedDevice?.getDeviceNumber() ?: return
            updateStatusText(getString(R.string.mesh_requesting_device_control, deviceId))
            netMeshMgr.requestControlGroup(
                listOf(DeviceControlAction(deviceId, ControlAction.Control, SelectAction.Select))
            ) { success, statusCode ->
                safePost {
                    if (success) {
                        updateStatusText(getString(R.string.mesh_device_control_success, deviceId))
                        refreshDeviceTabs()
                    } else {
                        updateStatusText(getString(R.string.mesh_control_failed, statusCode.msg))
                    }
                }
            }
        }
    }

    // =============================================
    // Downward light control (via KeyManagerExt multicast APIs)
    // =============================================

    private fun toggleDownLight() {
        downLightOn = !downLightOn
        val targetStatus = if (downLightOn) DroneLedStatusEnum.OPEN else DroneLedStatusEnum.CLOSE
        val bean = DroneLedStatusBean(DroneLedTypeEnum.BOTTOM_LED, targetStatus)
        val controlLedKey = CommonKey.KeyControlLed.create()

        val devices = getTargetDevices()
        if (devices.isEmpty()) {
            toast(getString(R.string.mesh_no_controllable_device))
            downLightOn = !downLightOn
            return
        }

        val statusStr = if (downLightOn) getString(R.string.mesh_on) else getString(R.string.mesh_off)
        updateStatusText(getString(R.string.mesh_down_light_operating, statusStr))

        if (devices.size == 1) {
            // Single: call the single-device API directly
            devices[0].getKeyManager()?.setValue(controlLedKey, bean,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        safePost {
                            updateStatusText(getString(R.string.mesh_down_light_done, statusStr))
                            updateDownLightUI()
                        }
                    }
                    override fun onFailure(error: com.autel.drone.sdk.libbase.error.IAutelCode, msg: String?) {
                        safePost {
                            downLightOn = !downLightOn
                            updateStatusText(getString(R.string.mesh_down_light_failed, statusStr, msg))
                        }
                    }
                })
        } else {
            // Multi: use the KeyManagerExt multicast API (core!)
            val idList = devices.map { it.deviceNumber() }
            KeyManagerExt.setValue(idList, controlLedKey, bean,
                object : CommonCallbacks.CompletionCallbackWithParamList<DeviceActionResultList<DroneLedStatusBean>> {
                    override fun onSuccess(t: DeviceActionResultList<DroneLedStatusBean>?) {
                        safePost {
                            updateStatusText(getString(R.string.mesh_down_light_done_all, statusStr, devices.size))
                            updateDownLightUI()
                        }
                    }
                    override fun onFailure(failure: DeviceActionFailureList?) {
                        safePost {
                            val failedIds = failure?.idList ?: emptyList()
                            val successCount = devices.size - failedIds.size
                            updateStatusText(getString(R.string.mesh_down_light_partial, statusStr, successCount, devices.size, failedIds))
                            updateDownLightUI()
                        }
                    }
                })
        }
    }

    private fun updateDownLightUI() {
        if (!isAdded || view == null) return
        btnDownLight.text = getString(R.string.mesh_down_light_btn, if (downLightOn) getString(R.string.mesh_on) else getString(R.string.mesh_off))
        btnDownLight.setBackgroundColor(
            if (downLightOn) Color.parseColor("#E94560") else Color.parseColor("#334756")
        )
    }

    // =============================================
    // Night light control (via KeyManagerExt multicast APIs)
    // =============================================

    private fun toggleNightLight() {
        nightLightOn = !nightLightOn
        val nightNaviKey = CommonKey.KeyDroneControlNightNavigationLed.create()

        val devices = getTargetDevices()
        if (devices.isEmpty()) {
            toast(getString(R.string.mesh_no_controllable_device))
            nightLightOn = !nightLightOn
            return
        }

        val statusStr = if (nightLightOn) getString(R.string.mesh_on) else getString(R.string.mesh_off)
        updateStatusText(getString(R.string.mesh_night_light_operating, statusStr))

        if (devices.size == 1) {
            // Single: call the single-device API directly
            devices[0].getKeyManager()?.setValue(nightNaviKey, nightLightOn,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        safePost {
                            updateStatusText(getString(R.string.mesh_night_light_done, statusStr))
                            updateNightLightUI()
                        }
                    }
                    override fun onFailure(error: com.autel.drone.sdk.libbase.error.IAutelCode, msg: String?) {
                        safePost {
                            nightLightOn = !nightLightOn
                            updateStatusText(getString(R.string.mesh_night_light_failed, statusStr, msg))
                        }
                    }
                })
        } else {
            // Multi: use the KeyManagerExt multicast API (core!)
            val idList = devices.map { it.deviceNumber() }
            KeyManagerExt.setValue(idList, nightNaviKey, nightLightOn,
                object : CommonCallbacks.CompletionCallbackWithParamList<DeviceActionResultList<Boolean>> {
                    override fun onSuccess(t: DeviceActionResultList<Boolean>?) {
                        safePost {
                            updateStatusText(getString(R.string.mesh_night_light_done_all, statusStr, devices.size))
                            updateNightLightUI()
                        }
                    }
                    override fun onFailure(failure: DeviceActionFailureList?) {
                        safePost {
                            val failedIds = failure?.idList ?: emptyList()
                            val successCount = devices.size - failedIds.size
                            updateStatusText(getString(R.string.mesh_night_light_partial, statusStr, successCount, devices.size, failedIds))
                            updateNightLightUI()
                        }
                    }
                })
        }
    }

    private fun updateNightLightUI() {
        if (!isAdded || view == null) return
        btnNightLight.text = getString(R.string.mesh_night_light_btn, if (nightLightOn) getString(R.string.mesh_on) else getString(R.string.mesh_off))
        btnNightLight.setBackgroundColor(
            if (nightLightOn) Color.parseColor("#E94560") else Color.parseColor("#334756")
        )
    }

    // =============================================
    // Utilities
    // =============================================

    /** Target device list for the current action (all online devices for ALL, the selected one otherwise) */
    private fun getTargetDevices(): List<IAutelDroneDevice> {
        return if (isAllMode) {
            DeviceManager.getDeviceManager().getDroneDevices().filter { it.isConnected() }
        } else {
            listOfNotNull(selectedDevice).filter { it.isConnected() }
        }
    }

    /** Get the wide-angle/zoom lens type */
    private fun getDefaultZoomLensType(device: IAutelDroneDevice): LensTypeEnum? {
        val zoomList = listOf(LensTypeEnum.Zoom, LensTypeEnum.TeleZoom, LensTypeEnum.WideAngle)
        val lensList = device.getCameraAbilitySetManger()
            .getLensList(device.getGimbalDeviceType())
        zoomList.forEach { if (lensList?.contains(it) == true) return it }
        return null
    }

    /** Get the IR lens type */
    private fun getDefaultThermalLensType(device: IAutelDroneDevice): LensTypeEnum? {
        val thermalList = listOf(LensTypeEnum.Thermal, LensTypeEnum.TeleThermal)
        val lensList = device.getCameraAbilitySetManger()
            .getLensList(device.getGimbalDeviceType())
        thermalList.forEach { if (lensList?.contains(it) == true) return it }
        return null
    }

    /** Get the lens ID */
    private fun getCameraLensId(device: IAutelDroneDevice, lensType: LensTypeEnum): Int? {
        return device.getCameraAbilitySetManger()
            .getLenId(lensType, device.getGimbalDeviceType())
    }

    /** Lens type name */
    private fun lensTypeName(type: LensTypeEnum?): String = when (type) {
        LensTypeEnum.Zoom, LensTypeEnum.TeleZoom -> getString(R.string.mesh_lens_zoom)
        LensTypeEnum.WideAngle -> getString(R.string.mesh_lens_wide)
        LensTypeEnum.Thermal, LensTypeEnum.TeleThermal -> getString(R.string.mesh_lens_thermal)
        LensTypeEnum.NightVision -> getString(R.string.mesh_lens_night)
        else -> getString(R.string.mesh_lens_unknown)
    }

    private fun updateStatusText(msg: String) {
        safePost {
            if (view == null) return@safePost
            tvStatus.text = msg
        }
    }

    private fun toast(msg: String) {
        showToast(msg)
    }
}
