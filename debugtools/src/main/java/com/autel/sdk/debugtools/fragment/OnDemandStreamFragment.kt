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
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.bean.WatermarkInfoBean
import com.autel.drone.sdk.vmodelx.module.camera.bean.GimbalTypeEnum
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.player.MediaInfo
import com.autel.player.codec.StreamData
import com.autel.player.player.IVideoStreamListener
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentOndemandStreamBinding
import java.nio.ByteBuffer

/**
 * MSDK 3.0 on-demand streaming sample Fragment
 *
 * Features:
 * 1. Dynamically show players based on the number of lenses the gimbal supports
 * 2. Single-stream / multi-stream view switching
 * 3. Lens switching (release old player → create new player, see AIDetectTrackFragment.switchPlaySource)
 * 4. On-demand streaming via the new AutelPlayer(deviceId, lensId) API
 *
 */
class OnDemandStreamFragment : AutelFragment(), IAutelDroneListener {

    private lateinit var binding: FragmentOndemandStreamBinding

    /** Currently connected drone */
    private var droneDevice: IAutelDroneDevice? = null

    /** Device ID */
    private var deviceId: String? = null

    /** Current gimbal type */
    private var gimbalType: GimbalTypeEnum? = null

    /** Available lens list */
    private var lensList: List<LensTypeEnum> = emptyList()

    /** Lens ID map <LensTypeEnum, lensId> */
    private var lensIdMap: Map<LensTypeEnum, Int> = emptyMap()

    /** Currently selected lens */
    private var currentLens: LensTypeEnum? = null

    /** Single-stream player */
    private var singlePlayer: AutelPlayer? = null

    /** Single-stream player view (reused; not destroyed when switching lenses) */
    private var singlePlayerView: AutelPlayerView? = null

    /** Currently playing lensId (to decide whether switching is needed) */
    private var mPlayingLensId: Int? = null

    /** Multi-stream player list */
    private val multiPlayers = mutableMapOf<LensTypeEnum, AutelPlayer>()

    /** Multi-stream player view map */
    private val multiPlayerViews = mutableMapOf<LensTypeEnum, AutelPlayerView>()

    /** Whether in multi-stream mode */
    private var isMultiViewMode = false

    /** Lens tab view map */
    private val lensTabViews = mutableMapOf<LensTypeEnum, TextView>()

    /** Watermark state */
    private var watermarkInfoBean: WatermarkInfoBean? = null
    private var watermarkPanelVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOndemandStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SDKLog.i(TAG, "onViewCreated")

        initViews()
        setupListeners()
        checkAndInitDevice()

        DeviceManager.getDeviceManager().addDroneListener(this)
    }

    private fun initViews() {
        binding.tvLoading.visibility = View.VISIBLE
        binding.btnToggleView.text = getString(R.string.debug_ondemand_single_view)
    }

    private fun setupListeners() {
        binding.btnToggleView.setOnClickListener {
            toggleViewMode()
        }
        binding.btnToggleWatermark.setOnClickListener {
            watermarkPanelVisible = !watermarkPanelVisible
            binding.watermarkPanel.visibility = if (watermarkPanelVisible) View.VISIBLE else View.GONE
            if (watermarkPanelVisible) loadWatermarkState()
        }
        initWatermarkControls()
    }

    /**
     * Check and initialize devices
     */
    private fun checkAndInitDevice() {
        val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
        if (device != null) {
            onDeviceConnected(device)
        } else {
            updateDeviceInfo(null)
        }
    }

    /**
     * Called when a device connects
     */
    private fun onDeviceConnected(device: IAutelDroneDevice) {
        SDKLog.i(TAG, "onDeviceConnected: device=${device.deviceNumber()}")
        droneDevice = device
        deviceId = device.deviceNumber().toString()
        gimbalType = device.getGimbalDeviceType()

        updateDeviceInfo(device)
        loadLensList()
    }

    /**
     * Called when a device disconnects
     */
    private fun onDeviceDisconnected() {
        SDKLog.i(TAG, "onDeviceDisconnected")
        stopAllPlayers()

        droneDevice = null
        deviceId = null
        gimbalType = null
        lensList = emptyList()
        lensIdMap = emptyMap()
        currentLens = null

        handler.post {
            updateDeviceInfo(null)
            clearLensTabs()
            binding.tvLoading.visibility = View.VISIBLE
            binding.tvLoading.text = getString(R.string.debug_ondemand_waiting_device)
        }
    }

    /**
     * Update device info display
     */
    private fun updateDeviceInfo(device: IAutelDroneDevice?) {
        handler.post {
            if (device != null) {
                binding.tvDeviceInfo.text = getString(R.string.debug_ondemand_device_info, device.deviceNumber())
                binding.tvGimbalInfo.text = getString(R.string.debug_ondemand_gimbal_info, gimbalType?.value ?: "--")
            } else {
                binding.tvDeviceInfo.text = getString(R.string.debug_ondemand_device_disconnected)
                binding.tvGimbalInfo.text = getString(R.string.debug_ondemand_gimbal_default)
            }
        }
    }

    /**
     * Load the lens list
     *
     * Note: keeps the currently selected lens to avoid reset by onCameraAbilityFetchListener
     */
    private fun loadLensList() {
        val device = droneDevice ?: return
        val cameraManager = device.getCameraAbilitySetManger()

        // Get the lens list
        val list = cameraManager.getLensList(gimbalType)
        if (list.isNullOrEmpty()) {
            SDKLog.w(TAG, "loadLensList: lens list is empty")
            handler.post {
                binding.tvLoading.text = getString(R.string.debug_ondemand_no_lens)
            }
            return
        }

        lensList = list
        SDKLog.i(TAG, "loadLensList: found ${lensList.size} lenses - $lensList")

        // Get the lens ID map
        val idMap = mutableMapOf<LensTypeEnum, Int>()
        for (lens in lensList) {
            val lensId = cameraManager.getLenId(lens, gimbalType)
            if (lensId != null) {
                idMap[lens] = lensId
                SDKLog.i(TAG, "loadLensList: lens=$lens, lensId=$lensId")
            }
        }
        lensIdMap = idMap

        handler.post {
            binding.tvLoading.visibility = View.GONE
            createLensTabs()

            // Keep the selected lens if still present, otherwise pick the first
            val lensToSelect = if (currentLens != null && lensList.contains(currentLens)) {
                currentLens!!
            } else if (lensList.isNotEmpty()) {
                lensList.first()
            } else {
                null
            }
            lensToSelect?.let { selectLens(it) }
        }
    }

    /**
     * Create lens tabs
     */
    private fun createLensTabs() {
        clearLensTabs()

        val container = binding.lensTabContainer
        for (lens in lensList) {
            val tab = createLensTab(lens)
            container.addView(tab)
            lensTabViews[lens] = tab
        }
    }

    /**
     * Create a single lens tab
     */
    private fun createLensTab(lens: LensTypeEnum): TextView {
        return TextView(requireContext()).apply {
            text = getLensDisplayName(lens)
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 8, 32, 8)
            setBackgroundColor(Color.TRANSPARENT)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            params.marginEnd = 8
            layoutParams = params

            setOnClickListener {
                if (!isMultiViewMode) {
                    selectLens(lens)
                }
            }
        }
    }

    /**
     * Get lens display name
     */
    private fun getLensDisplayName(lens: LensTypeEnum): String {
        return when (lens) {
            LensTypeEnum.Zoom -> getString(R.string.debug_ondemand_lens_zoom)
            LensTypeEnum.TeleZoom -> getString(R.string.debug_ondemand_lens_telezoom)
            LensTypeEnum.Thermal -> getString(R.string.debug_ondemand_lens_thermal)
            LensTypeEnum.TeleThermal -> getString(R.string.debug_ondemand_lens_telethermal)
            LensTypeEnum.WideAngle -> getString(R.string.debug_ondemand_lens_wideangle)
            LensTypeEnum.NightVision -> getString(R.string.debug_ondemand_lens_nightvision)
        }
    }

    /**
     * Clear lens tabs
     */
    private fun clearLensTabs() {
        binding.lensTabContainer.removeAllViews()
        lensTabViews.clear()
    }

    /**
     * Select a lens
     */
    private fun selectLens(lens: LensTypeEnum) {
        SDKLog.i(TAG, "selectLens: lens=$lens, currentLens=$currentLens")
        currentLens = lens

        // Update tab highlight
        updateLensTabHighlight(lens)

        // Update bottom status
        binding.tvCurrentLens.text = getString(R.string.debug_ondemand_current_lens, getLensDisplayName(lens))

        if (!isMultiViewMode) {
            startSinglePlayer(lens)
        }
    }

    /**
     * Update lens tab highlight
     */
    private fun updateLensTabHighlight(selectedLens: LensTypeEnum) {
        for ((lens, tab) in lensTabViews) {
            if (lens == selectedLens) {
                tab.setBackgroundColor(Color.parseColor("#4CAF50"))
                tab.setTextColor(Color.WHITE)
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT)
                tab.setTextColor(Color.parseColor("#AAAAAA"))
            }
        }
    }

    /**
     * Update lens tab enabled/disabled state
     * Greyed out and disabled in multi-stream mode; restored in single-stream mode
     */
    private fun updateLensTabsEnabled(enabled: Boolean) {
        for ((lens, tab) in lensTabViews) {
            tab.isEnabled = enabled
            tab.alpha = if (enabled) 1.0f else 0.4f
        }
        if (enabled && currentLens != null) {
            updateLensTabHighlight(currentLens!!)
        }
    }

    /**
     * Switch single/multi-stream mode
     */
    private fun toggleViewMode() {
        isMultiViewMode = !isMultiViewMode
        SDKLog.i(TAG, "toggleViewMode: isMultiViewMode=$isMultiViewMode")

        if (isMultiViewMode) {
            binding.btnToggleView.text = getString(R.string.debug_ondemand_multi_view)
            binding.singlePlayerContainer.visibility = View.GONE
            binding.multiPlayerContainer.visibility = View.VISIBLE

            // Grey out lens tabs in multi-stream mode (all lenses shown; no manual switching)
            updateLensTabsEnabled(false)

            stopSinglePlayer()
            startMultiPlayers()
        } else {
            binding.btnToggleView.text = getString(R.string.debug_ondemand_single_view)
            binding.singlePlayerContainer.visibility = View.VISIBLE
            binding.multiPlayerContainer.visibility = View.GONE

            // Restore lens tab clicks in single-stream mode
            updateLensTabsEnabled(true)

            stopMultiPlayers()
            currentLens?.let { startSinglePlayer(it) }
        }
    }

    /**
     * Start the single-stream player (release old → create new)
     *
     * Follows the AIDetectTrackFragment.switchPlaySource() pattern:
     * - On each lens switch: release the old player → create a new player
     * - Uses the new API: AutelPlayer(devId, lensId)
     * - Reuses AutelPlayerView; the view container is not destroyed
     */
    private fun startSinglePlayer(lens: LensTypeEnum) {
        val devId = deviceId ?: return
        val lensId = lensIdMap[lens] ?: run {
            SDKLog.w(TAG, "startSinglePlayer: no lensId for lens=$lens")
            return
        }

        // No-op if already playing this lens
        if (lensId == mPlayingLensId && singlePlayer != null) {
            SDKLog.i(TAG, "startSinglePlayer: same lensId=$lensId, skip")
            return
        }

        SDKLog.i(TAG, "startSinglePlayer: lens=$lens, lensId=$lensId, devId=$devId")

        // 1. Release the old player (keep the PlayerView container)
        singlePlayer?.let { player ->
            SDKLog.i(TAG, "startSinglePlayer: releasing old player, old lensId=$mPlayingLensId")
            player.removeVideoInfoListener(singlePlayerVideoListener)
            player.removeVideoView()
            player.stopPlayer()
            player.releasePlayer()
            singlePlayer = null
        }

        // 2. Ensure PlayerView exists (created on first entry or switch back from multi)
        if (singlePlayerView == null) {
            SDKLog.i(TAG, "startSinglePlayer: creating new PlayerView")
            val playerView = AutelPlayerView(requireContext())
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            playerView.layoutParams = params
            binding.singlePlayerContainer.addView(playerView)
            singlePlayerView = playerView
        }

        // 3. Create the new player
        SDKLog.i(TAG, "startSinglePlayer: creating new player with devId=$devId, lensId=$lensId")
        mPlayingLensId = lensId
        val player = AutelPlayer(devId, lensId)
        player.addVideoInfoListener(singlePlayerVideoListener)
        player.addVideoView(singlePlayerView)
        player.startPlayer()
        singlePlayer = player

        binding.tvPlayerStatus.text = getString(R.string.debug_ondemand_status_playing)
    }

    /**
     * Stop and release the single-stream player
     */
    private fun stopSinglePlayer() {
        SDKLog.i(TAG, "stopSinglePlayer")

        singlePlayer?.let { player ->
            player.removeVideoInfoListener(singlePlayerVideoListener)
            player.removeVideoView()
            player.stopPlayer()
            player.releasePlayer()
        }
        singlePlayer = null
        mPlayingLensId = null

        singlePlayerView?.let {
            binding.singlePlayerContainer.removeView(it)
        }
        singlePlayerView = null

        binding.tvPlayerStatus.text = getString(R.string.debug_ondemand_status_stopped)
    }

    /**
     * Start multi-stream players
     */
    private fun startMultiPlayers() {
        val devId = deviceId ?: return
        if (lensList.isEmpty()) return

        SDKLog.i(TAG, "startMultiPlayers: deviceId=$devId, lensCount=${lensList.size}")

        // Adjust layout by lens count
        val lensCount = lensList.size
        if (lensCount > 2) {
            binding.bottomRow.visibility = View.VISIBLE
        } else {
            binding.bottomRow.visibility = View.GONE
        }

        // Get player slots
        val slots = listOf(
            binding.playerSlot0,
            binding.playerSlot1,
            binding.playerSlot2,
            binding.playerSlot3
        )

        // Create a player per lens
        for ((index, lens) in lensList.withIndex()) {
            if (index >= slots.size) break

            val lensId = lensIdMap[lens] ?: continue
            val slot = slots[index]

            SDKLog.i(TAG, "startMultiPlayers: creating player for lens=$lens, lensId=$lensId, slot=$index")

            // Create the player view
            val playerView = AutelPlayerView(requireContext())
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            playerView.layoutParams = params

            // Add lens name label
            val label = TextView(requireContext()).apply {
                text = getLensDisplayName(lens)
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#80000000"))
                setPadding(16, 4, 16, 4)
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                lp.gravity = Gravity.TOP or Gravity.START
                lp.topMargin = 8
                lp.marginStart = 8
                layoutParams = lp
            }

            slot.removeAllViews()
            slot.addView(playerView)
            slot.addView(label)
            multiPlayerViews[lens] = playerView

            // Create players
            val player = AutelPlayer(devId, lensId)
            player.addVideoView(playerView)
            player.startPlayer()
            multiPlayers[lens] = player

            SDKLog.i(TAG, "startMultiPlayers: player started for lens=$lens")
        }

        binding.tvPlayerStatus.text = getString(R.string.debug_ondemand_status_multi_playing, multiPlayers.size)
    }

    /**
     * Stop all multi-stream players
     */
    private fun stopMultiPlayers() {
        SDKLog.i(TAG, "stopMultiPlayers: count=${multiPlayers.size}")

        for ((lens, player) in multiPlayers) {
            SDKLog.i(TAG, "stopMultiPlayers: stopping player for lens=$lens")
            player.removeVideoView()
            player.stopPlayer()
            player.releasePlayer()
        }
        multiPlayers.clear()

        // Clean up views
        binding.playerSlot0.removeAllViews()
        binding.playerSlot1.removeAllViews()
        binding.playerSlot2.removeAllViews()
        binding.playerSlot3.removeAllViews()
        multiPlayerViews.clear()
    }

    /**
     * Stop all players
     */
    private fun stopAllPlayers() {
        stopSinglePlayer()
        stopMultiPlayers()
    }

    // IAutelDroneListener implementation
    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        SDKLog.i(TAG, "onDroneChangedListener: connected=$connected, device=${drone.deviceNumber()}")
        if (connected) {
            onDeviceConnected(drone)
        } else {
            if (drone.deviceNumber() == droneDevice?.deviceNumber()) {
                onDeviceDisconnected()
            }
        }
    }

    override fun onCameraAbilityFetchListener(
        localFetched: Boolean,
        remoteFetched: Boolean,
        drone: IAutelDroneDevice
    ) {
        SDKLog.i(TAG, "onCameraAbilityFetchListener: localFetched=$localFetched, remoteFetched=$remoteFetched")
        if (drone.deviceNumber() == droneDevice?.deviceNumber()) {
            // Reload the lens list (loadLensList keeps the current selection)
            handler.post {
                loadLensList()
            }
        }
    }

    /** Single-stream player video listener */
    private val singlePlayerVideoListener = object : IVideoStreamListener {
        override fun onVideoSizeChanged(playerId: Int, width: Int, height: Int) {
            SDKLog.i(TAG, "onVideoSizeChanged: playerId=$playerId, width=$width, height=$height")
        }

        override fun onVideoInfoCallback(playerId: Int, x: Int, y: Int, w: Int, h: Int) {
            // NO-OP
        }

        override fun onFrameYuv(yuv: ByteBuffer?, mediaInfo: MediaInfo?) {
            // NO-OP
        }

        override fun onVideoErrorCallback(playerId: Int, type: Int, errorContent: String?) {
            SDKLog.e(TAG, "onVideoErrorCallback: playerId=$playerId, type=$type, error=$errorContent")
        }

        override fun onStreamSourceChanged(playerId: Int, newType: StreamData.StreamSourceType?) {
            SDKLog.i(TAG, "onStreamSourceChanged: playerId=$playerId, newType=$newType")
        }
    }

    // ======================== Watermark control ========================

    private val keyWatermarkNew by lazy { KeyTools.createKey(CameraKey.KeyWatermarkNew) }

    private fun initWatermarkControls() {
        binding.switchWatermarkEnable.setOnCheckedChangeListener { _, checked -> updateWatermark { it.enable = checked } }
        binding.switchWatermarkTime.setOnCheckedChangeListener { _, checked -> updateWatermark { if (checked) it.enable = true; it.enableTime = checked } }
        binding.switchWatermarkGps.setOnCheckedChangeListener { _, checked -> updateWatermark { if (checked) it.enable = true; it.enableGPS = checked } }
        binding.switchWatermarkSn.setOnCheckedChangeListener { _, checked -> updateWatermark { if (checked) it.enable = true; it.enableDeviceSN = checked } }
        binding.switchWatermarkCustom.setOnCheckedChangeListener { _, checked ->
            updateWatermark { if (checked) it.enable = true; it.enableCustom = checked }
            if (checked) {
                val keyContent = KeyTools.createKey(CameraKey.KeyCameraCustomWatermarkContent)
                droneDevice?.getKeyManager()?.setValue(keyContent, "Autel", object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() { SDKLog.i(TAG, "setCustomWatermarkContent success") }
                    override fun onFailure(code: IAutelCode, msg: String?) { SDKLog.e(TAG, "setCustomWatermarkContent failed: $code $msg") }
                })
            }
        }
    }

    private fun loadWatermarkState() {
        droneDevice?.getKeyManager()?.getValue(
            keyWatermarkNew,
            object : CommonCallbacks.CompletionCallbackWithParam<WatermarkInfoBean> {
                override fun onSuccess(bean: WatermarkInfoBean?) {
                    watermarkInfoBean = bean
                    bean ?: return
                    SDKLog.i(TAG, "loadWatermarkState: $bean")
                    handler.post {
                        if (!isAdded) return@post
                        binding.switchWatermarkEnable.isChecked = bean.enable
                        binding.switchWatermarkTime.isChecked = bean.enableTime
                        binding.switchWatermarkGps.isChecked = bean.enableGPS
                        binding.switchWatermarkSn.isChecked = bean.enableDeviceSN
                        binding.switchWatermarkCustom.isChecked = bean.enableCustom
                    }
                }

                override fun onFailure(code: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "loadWatermarkState failed: $code $msg")
                }
            })
    }

    private fun updateWatermark(mutator: (WatermarkInfoBean) -> Unit) {
        val bean = watermarkInfoBean ?: return
        mutator(bean)
        droneDevice?.getKeyManager()?.setValue(
            keyWatermarkNew, bean, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() { SDKLog.i(TAG, "updateWatermark success: $bean") }
                override fun onFailure(code: IAutelCode, msg: String?) { SDKLog.e(TAG, "updateWatermark failed: $code $msg") }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SDKLog.i(TAG, "onDestroyView")
        DeviceManager.getDeviceManager().removeDroneListener(this)
        stopAllPlayers()
        if (watermarkInfoBean?.enableCustom == true) {
            updateWatermark { it.enableCustom = false }
        }
    }
}
