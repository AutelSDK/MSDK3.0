package com.autel.sdk.debugtools.fragment

import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.autel.drone.sdk.SDKConstants
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AIServiceKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AITrackingKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.AIDetectConfigBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.ActionFuncEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.DetectTrackNotifyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.TrackAreaBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.enums.AiDetectSceneTypeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.enums.AiLensTypeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aitrack.TrackTargetRectBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.MissionHeartbeatStatusInfo
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.player.player.AutelPlayerManager
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentAiDetectTrackingBinding
import com.autel.sdk.debugtools.helper.AiDemoStatusPanel
import com.autel.sdk.debugtools.listener.OnItemSelectedListener
import com.autel.sdk.debugtools.tracking.AITrackingView
import com.autel.sdk.debugtools.tracking.AITrackingView.Companion.TRACK_STATUS_TRACKING
import com.autel.sdk.debugtools.tracking.AITrackingView.Companion.TRACK_STATUS_NONE

/**
 * AI detection & tracking demo (MSDK 2.5 IntelligentLock API); status display aligned with Athena + persistent error panel.
 */
class AIDetectTrackFragment : AutelFragment(), IAutelDroneListener {

    private lateinit var binding: FragmentAiDetectTrackingBinding
    private lateinit var statusPanel: AiDemoStatusPanel

    private var isRunning = false
    private var isProcessing = false
    private var autelPlayer: AutelPlayer? = null
    private var lensType: LensTypeEnum = LensTypeEnum.Zoom
    private var lensId: Int = 0
    private var droneDevice: IAutelDroneDevice? = null

    private val keyHeartbeatInfo = KeyTools.createKey(CommonKey.KeyMissionHeartbeatStatusReport)

    private val heartbeatCallback = object : CommonCallbacks.KeyListener<MissionHeartbeatStatusInfo> {
        override fun onValueChange(oldValue: MissionHeartbeatStatusInfo?, newValue: MissionHeartbeatStatusInfo) {
            if (!isAdded) return
            statusPanel.setHeartbeat(newValue.aiEnableFunc)
            if (isProcessing) return
            when (newValue.aiEnableFunc) {
                ActionFuncEnum.AI_TRACK -> {
                    if (isRunning) {
                        statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.RUNNING)
                    }
                }
                ActionFuncEnum.AI_NONE -> {
                    if (isRunning) {
                        isRunning = false
                        statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.IDLE)
                        statusPanel.clearTargets()
                        updateTrackStatusLabel()
                        safePost { updateStartButton(false) }
                    }
                }
                else -> Unit
            }
        }
    }

    private val aiDetectKey = KeyTools.createKey(AIServiceKey.KeyAiDetectTarget)
    private var aiDetectCallBack = object : CommonCallbacks.KeyListener<DetectTrackNotifyBean> {
        override fun onValueChange(oldValue: DetectTrackNotifyBean?, newValue: DetectTrackNotifyBean) {
            if (lensId != newValue.lensId) {
                clearAiView()
                return
            }
            if (newValue.infoList.isNotEmpty()) {
                binding.aiTrackingView.refreshTrackableTarget(newValue)
                if (isAdded) {
                    statusPanel.setTargetInfo(newValue.infoList.size, 0, 0)
                    val detail = StringBuilder()
                    newValue.infoList.forEach { info ->
                        detail.append("[type=${info.type}] id=${info.objectId}\n")
                    }
                    statusPanel.setDetail(detail.toString().trim())
                }
            } else if (isAdded) {
                statusPanel.setTargetInfo(0, 0, 0)
                statusPanel.setDetail("")
            }
        }
    }

    private val aiTrackKey = KeyTools.createKey(AIServiceKey.KeyTrackingTargetObject)
    private var aiTrackCallBack = object : CommonCallbacks.KeyListener<TrackAreaBean> {
        override fun onValueChange(oldValue: TrackAreaBean?, newValue: TrackAreaBean) {
            if (lensId != newValue.lensId) {
                clearAiView()
                return
            }
            if (newValue.infoList?.isNotEmpty() == true) {
                binding.aiTrackingView.selectTarget(newValue)
                if (binding.aiTrackingView.trackWorkStatus != TRACK_STATUS_TRACKING) {
                    binding.aiTrackingView.trackWorkStatus = TRACK_STATUS_TRACKING
                } else if (droneDevice?.getDeviceStateData()?.flightControlData?.trackWorkStatus?.value != binding.aiTrackingView.trackWorkStatus) {
                    binding.aiTrackingView.trackWorkStatus = droneDevice?.getDeviceStateData()?.flightControlData?.trackWorkStatus?.value ?: 0
                }
                updateTrackStatusLabel()
            }
        }
    }

    private var trackViewListener = object : AITrackingView.OnTrackChangeListener {
        override fun onDragSelectTarget(view: AITrackingView, x: Float, y: Float, width: Float, height: Float) {
            SDKLog.d(TAG, "selected: x:$x, $y, $width, $height")
            startLock(RectF(x, y, width, height))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAiDetectTrackingBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        droneDevice = DeviceManager.getDeviceManager().getFirstDroneDevice()

        statusPanel = AiDemoStatusPanel(binding.aiStatusPanel.root, ActionFuncEnum.AI_TRACK)
        refreshDeviceAndLensStatus()
        droneDevice?.getKeyManager()?.listen(keyHeartbeatInfo, heartbeatCallback)
        getAiEnableFunc().let { statusPanel.setHeartbeat(it) }
        updateTrackStatusLabel()

        initLensSpinner()
        initPlayer()
        listenResult()

        binding.btnStartRecognize.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            if (isRunning) {
                exitIntelligentLock()
            } else {
                enterIntelligentLock()
            }
        }
        binding.aiTrackingView.setOnTrackChangeListener(trackViewListener)
    }

    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        super.onDroneChangedListener(connected, drone)
        droneDevice = drone
        refreshDeviceAndLensStatus()
        if (connected) {
            drone.getKeyManager().listen(keyHeartbeatInfo, heartbeatCallback)
            getAiEnableFunc().let { statusPanel.setHeartbeat(it) }
        } else {
            statusPanel.setDevice(false, null)
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.DISCONNECTED)
        }
    }

    private fun refreshDeviceAndLensStatus() {
        val device = droneDevice
        if (device == null) {
            statusPanel.setDevice(false, null)
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.DISCONNECTED)
            return
        }
        statusPanel.setDevice(true, device.deviceNumber().toString())
        statusPanel.setLens(lensType, lensId)
    }

    private fun getAiEnableFunc(): ActionFuncEnum {
        return droneDevice?.getKeyManager()?.getCacheValue(keyHeartbeatInfo)?.aiEnableFunc
            ?: ActionFuncEnum.UNKNOWN
    }

    private fun updateTrackStatusLabel() {
        val tracking = binding.aiTrackingView.trackWorkStatus == TRACK_STATUS_TRACKING
        statusPanel.setTrackStatus(
            getString(
                if (tracking) R.string.ai_demo_track_status_tracking else R.string.ai_demo_track_status_none
            )
        )
    }

    private fun updateStartButton(running: Boolean) {
        binding.btnStartRecognize.isEnabled = true
        binding.btnStartRecognize.text = if (running) {
            getString(R.string.debug_stop)
        } else {
            getString(R.string.debug_start)
        }
    }

    private fun listenResult() {
        droneDevice?.getKeyManager()?.listen(aiDetectKey, aiDetectCallBack)
        droneDevice?.getKeyManager()?.listen(aiTrackKey, aiTrackCallBack)
    }

    private fun startLock(rect: RectF) {
        if (!isRunning) {
            statusPanel.setError(getString(R.string.ai_demo_start_failed, "AI not started"))
            return
        }
        val createKey = KeyTools.createKey(AITrackingKey.KeyIntelligentLockStart)
        val bean = TrackTargetRectBean(rect.left, rect.top, rect.right, rect.bottom, lensId, AiDetectSceneTypeEnum.SCENE_TYPE_UNIVERSAL)
        droneDevice?.getKeyManager()?.performAction(createKey, bean, object : CommonCallbacks.CompletionCallbackWithParam<Void> {
            override fun onSuccess(t: Void?) {
                SDKLog.i(TAG, "startLock success, rect: $rect lensId: $lensId")
                safePost {
                    statusPanel.setError(null)
                    updateTrackStatusLabel()
                }
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                SDKLog.e(TAG, "startLock failed, rect: $rect lensId: $lensId， err:$msg")
                safePost {
                    statusPanel.setError(getString(R.string.ai_demo_lock_failed, msg ?: error.code.toString()))
                }
            }
        }) ?: run {
            SDKLog.e(TAG, "startLock failed, droneDevice is null")
            statusPanel.setError(getString(R.string.ai_detect_device_disconnected))
        }
    }

    private fun initLensSpinner() {
        val cameraAbilitySetManager = droneDevice?.getAbilitySetManager()?.getCameraAbilitySetManager()
        val lens = cameraAbilitySetManager?.getLensList(droneDevice?.getGimbalDeviceType())?.filter {
            cameraAbilitySetManager.getCameraSupport2()?.getAiServiceEnabled(it) == true
        } ?: listOf()

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lens.map { it.value })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLens.adapter = adapter

        lensType = lens.firstOrNull() ?: LensTypeEnum.Zoom
        binding.spinnerLens.setSelection(0)
        binding.spinnerLens.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val posType = lens[position]
                if (lensType != posType) {
                    switchLensAndRestartAiIfNeeded(posType)
                }
            }
        }
    }

    private fun switchLensAndRestartAiIfNeeded(newLensType: LensTypeEnum) {
        if (lensType == newLensType) {
            return
        }
        if (!isRunning) {
            lensType = newLensType
            switchPlaySource()
            refreshDeviceAndLensStatus()
            return
        }

        SDKLog.i(TAG, "switchLensAndRestartAiIfNeeded: exit and restart, newLens=$newLensType")
        isProcessing = true
        statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.RESTARTING_LENS)
        statusPanel.setError(null)
        binding.btnStartRecognize.isEnabled = false
        val key = KeyTools.createKey(AITrackingKey.KeyIntelligentLockExit)
        droneDevice?.getKeyManager()?.performAction(key, callback = object : CommonCallbacks.CompletionCallbackWithParam<Void> {
            override fun onSuccess(t: Void?) {
                SDKLog.i(TAG, "switchLensAndRestartAiIfNeeded: exit success")
                clearAiView()
                lensType = newLensType
                switchPlaySource()
                refreshDeviceAndLensStatus()
                isProcessing = false
                enterIntelligentLock()
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                SDKLog.e(TAG, "switchLensAndRestartAiIfNeeded: exit failed error=$error msg=$msg")
                isProcessing = false
                lensType = newLensType
                switchPlaySource()
                refreshDeviceAndLensStatus()
                isRunning = false
                safePost {
                    statusPanel.setError(getString(R.string.ai_demo_stop_failed, msg ?: error.code.toString()))
                    statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.ERROR)
                    updateStartButton(false)
                }
            }
        }) ?: run {
            lensType = newLensType
            switchPlaySource()
            refreshDeviceAndLensStatus()
            resetButtonAfterLensSwitchFailure()
        }
    }

    private fun resetButtonAfterLensSwitchFailure() {
        isRunning = false
        isProcessing = false
        updateStartButton(false)
    }

    private fun initPlayer() {
        lensId = droneDevice?.getAbilitySetManager()?.getCameraAbilitySetManager()
            ?.getLenId(lensType, droneDevice?.getGimbalDeviceType()) ?: 0
        val channelId = getChannelId()
        autelPlayer = AutelPlayer(channelId)
        AutelPlayerManager.getInstance().addAutelPlayer(autelPlayer)
        autelPlayer!!.addVideoView(binding.renderView)
        refreshDeviceAndLensStatus()
    }

    private fun switchPlaySource() {
        autelPlayer?.removeVideoView()
        autelPlayer?.releasePlayer()
        clearAiView()
        statusPanel.clearTargets()

        lensId = droneDevice?.getAbilitySetManager()?.getCameraAbilitySetManager()
            ?.getLenId(lensType, droneDevice?.getGimbalDeviceType()) ?: 0
        val channelId = getChannelId()
        autelPlayer = AutelPlayer(channelId)
        AutelPlayerManager.getInstance().addAutelPlayer(autelPlayer)
        autelPlayer!!.addVideoView(binding.renderView)
        refreshDeviceAndLensStatus()
    }

    override fun onDestroyView() {
        droneDevice?.getKeyManager()?.cancelListen(keyHeartbeatInfo, heartbeatCallback)
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        droneDevice?.getKeyManager()?.let {
            it.cancelListen(aiDetectKey, aiDetectCallBack)
            it.cancelListen(aiTrackKey, aiTrackCallBack)
        }

        autelPlayer?.removeVideoView()
        autelPlayer?.releasePlayer()
    }

    private fun getChannelId(): Int {
        droneDevice ?: return SDKConstants.getZoomChancelId()
        val lens = droneDevice?.getAbilitySetManager()?.getCameraAbilitySetManager()
            ?.getLensList(droneDevice?.getGimbalDeviceType()) ?: emptyList()
        return when (lensType) {
            LensTypeEnum.Zoom -> if (lens.contains(LensTypeEnum.WideAngle)) SDKConstants.getTelZoomChancelId() else SDKConstants.getZoomChancelId()
            LensTypeEnum.TeleZoom -> SDKConstants.getTelZoomChancelId()
            LensTypeEnum.WideAngle -> SDKConstants.getWideAngleChannelId()
            LensTypeEnum.Thermal, LensTypeEnum.TeleThermal -> SDKConstants.getInfraredChannelId()
            LensTypeEnum.NightVision -> SDKConstants.getNightVisionChannelId()
            else -> SDKConstants.getZoomChancelId()
        }
    }

    private fun enterIntelligentLock() {
        val device = droneDevice ?: run {
            statusPanel.setError(getString(R.string.ai_detect_device_disconnected))
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.DISCONNECTED)
            return
        }
        val cameraManager = device.getAbilitySetManager()?.getCameraAbilitySetManager()
        if (cameraManager?.getCameraSupport2()?.getAiServiceEnabled(lensType) != true) {
            SDKLog.e(TAG, "The lens[${lensType.name}] does not support AI recognition")
            val msg = getString(R.string.ai_demo_lens_not_support, lensType.name)
            statusPanel.setError(msg)
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.UNSUPPORTED)
            isRunning = false
            updateStartButton(false)
            return
        }

        val droneLensId = cameraManager.getLenId(lensType, device.getGimbalDeviceType())
        lensId = droneLensId ?: 0
        statusPanel.setLens(lensType, lensId)

        val bean = AIDetectConfigBean().apply {
            sceneType = AiDetectSceneTypeEnum.SCENE_TYPE_UNIVERSAL
            targetTypeList = null
            lensId = droneLensId ?: 0
            aiLensTypeEnum = when {
                lensType.isThermal() -> AiLensTypeEnum.INFRARED
                lensType == LensTypeEnum.NightVision -> AiLensTypeEnum.Night
                else -> AiLensTypeEnum.Visible
            }
        }

        SDKLog.i(TAG, "enterIntelligentLock lensType:$lensType lensId:${bean.lensId}")
        isProcessing = true
        statusPanel.setError(null)
        statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.STARTING)
        binding.btnStartRecognize.isEnabled = false
        binding.btnStartRecognize.text = getString(R.string.debug_start) + "..."

        val key = KeyTools.createKey(AITrackingKey.KeyIntelligentLockEnter)
        device.getKeyManager()?.performAction(key, bean, object : CommonCallbacks.CompletionCallbackWithParam<Void> {
            override fun onSuccess(t: Void?) {
                SDKLog.d(TAG, "enterIntelligentLock success")
                isProcessing = false
                isRunning = true
                safePost {
                    statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.RUNNING)
                    updateStartButton(true)
                }
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                SDKLog.d(TAG, "enterIntelligentLock failed $error $msg")
                isProcessing = false
                isRunning = false
                safePost {
                    statusPanel.setError(getString(R.string.ai_demo_start_failed, msg ?: error.code.toString()))
                    statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.ERROR)
                    updateStartButton(false)
                }
            }
        })
    }

    private fun exitIntelligentLock() {
        isProcessing = true
        statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.STOPPING)
        statusPanel.setError(null)
        binding.btnStartRecognize.isEnabled = false
        binding.btnStartRecognize.text = getString(R.string.debug_stop) + "..."

        val key = KeyTools.createKey(AITrackingKey.KeyIntelligentLockExit)
        droneDevice?.getKeyManager()?.performAction(key, callback = object : CommonCallbacks.CompletionCallbackWithParam<Void> {
            override fun onSuccess(t: Void?) {
                isProcessing = false
                isRunning = false
                safePost {
                    clearAiView()
                    statusPanel.clearTargets()
                    statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.IDLE)
                    updateStartButton(false)
                }
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                isProcessing = false
                safePost {
                    statusPanel.setError(getString(R.string.ai_demo_stop_failed, msg ?: error.code.toString()))
                    statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.ERROR)
                    updateStartButton(isRunning)
                }
            }
        })
        handler.postDelayed({ clearAiView() }, 500)
    }

    private fun clearAiView() {
        binding.aiTrackingView.cleanup()
        binding.aiTrackingView.trackWorkStatus = TRACK_STATUS_NONE
        updateTrackStatusLabel()
    }
}
