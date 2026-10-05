package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AITrackingKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.AIDisableConfigBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.AIEnableConfigBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.ActionFuncEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.enums.AiDetectSceneTypeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.MissionHeartbeatStatusInfo
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.player.player.ai.AIInfo
import com.autel.player.player.ai.AIInfoManager
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.render.RenderMode
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentAiRecognizeBinding
import com.autel.sdk.debugtools.helper.AiDemoRecognitionSoundPlayer
import com.autel.sdk.debugtools.helper.AiDemoSessionRecordStore
import com.autel.sdk.debugtools.helper.AiDemoStatusPanel
import com.autel.sdk.debugtools.helper.AiDemoTargetTypeCatalog
import com.autel.sdk.debugtools.helper.AiDemoTargetTypePrefs
import kotlinx.coroutines.launch

/**
 * AI recognition demo; pipeline aligned with Athena [AIDetectionViewModel] + [AIWidgetModel]:
 * - Default wide-angle lens prefers Zoom (then TeleZoom)
 * - KeyAiEnter / AutelPlayer / SEI callbacks share the same lensId
 * - Button/session state driven by KeyAiEnter/Exit (aligned with Athena UX; no Idle revert on heartbeat jitter)
 * - Boxes driven by heartbeat aiEnableFunc==AI_DETECT (aligned with [AIDetectionViewModel.aiEnabled])
 * - Auto stop + restart on lens switch; state/errors shown persistently
 * - Multi-select recognition types, alert sound, lightweight per-session record (subset of the Athena settings page)
 */
class AIRecognizeFragment : AutelFragment(), IAutelDroneListener, AIInfoManager.AIInfoCallback {

    private lateinit var binding: FragmentAiRecognizeBinding
    private lateinit var statusPanel: AiDemoStatusPanel

    /** User started a recognition session (from Enter success to Stop success); controls button and Stop label */
    private var isRunning = false
    private var isProcessing = false
    /** After a lens switch, wait for KeyAiExit then auto KeyAiEnter */
    private var restartRecognizeAfterStop = false
    /** Aligned with Athena aiEnabled: draw SEI boxes only when heartbeat aiEnableFunc==AI_DETECT */
    private var aiDetectEnabled = false

    private var autelPlayerZoom: AutelPlayer? = null
    private var autelPlayerThermal: AutelPlayer? = null
    private val keyHeartbeatInfo = KeyTools.createKey(CommonKey.KeyMissionHeartbeatStatusReport)
    private var droneDevice: IAutelDroneDevice? = null
    private var lastAiInfoLogMs = 0L

    private val detectionDelegate = AIDetectionOverlayDelegate()
    private val soundPlayer = AiDemoRecognitionSoundPlayer()
    private val sessionRecordStore = AiDemoSessionRecordStore()
    private val targetTypeCheckBoxes = mutableListOf<CheckBox>()
    private var targetTypesExpanded = false

    private val heartbeatCallback = object : CommonCallbacks.KeyListener<MissionHeartbeatStatusInfo> {
        override fun onValueChange(oldValue: MissionHeartbeatStatusInfo?, newValue: MissionHeartbeatStatusInfo) {
            if (!isAdded) return
            val wasDetectEnabled = aiDetectEnabled
            aiDetectEnabled = newValue.aiEnableFunc == ActionFuncEnum.AI_DETECT
            statusPanel.setHeartbeat(newValue.aiEnableFunc)

            if (isProcessing || restartRecognizeAfterStop) {
                return
            }

            // Heartbeat only drives box/target-area cleanup; it does not cancel a user-started session (aligned with AIDetectionViewModel)
            if (wasDetectEnabled && !aiDetectEnabled) {
                SDKLog.i(TAG, "heartbeat: AI_DETECT->${newValue.aiEnableFunc}, clear overlay only, isRunning=$isRunning")
                detectionDelegate.clear()
                statusPanel.clearTargets()
            }

            if (isRunning) {
                statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.RUNNING)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentAiRecognizeBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        droneDevice = DeviceManager.getDeviceManager().getFirstDroneDevice()
        statusPanel = AiDemoStatusPanel(binding.aiStatusPanel.root, ActionFuncEnum.AI_DETECT)
        refreshDeviceAndLensStatus()

        SDKLog.i(TAG, "onViewCreated=>AIEnable:${getAiEnableFunc()}")

        AIInfoManager.getInstance().addAIInfoCallback(this)
        droneDevice?.getKeyManager()?.listen(keyHeartbeatInfo, heartbeatCallback)
        syncAiDetectEnabledFromCache()

        initPlayer()
        detectionDelegate.attach(requireContext(), binding.playerContainer)
        initRecognizeOptions()

        binding.btnStartRecognize.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            if (isRunning) {
                stopRecognize()
            } else {
                startRecognize()
            }
        }

        binding.lensType.setOnCheckedChangeListener { _, id ->
            val wasRunning = isRunning
            switchPlaySource(id == R.id.rb_zoom)
            refreshDeviceAndLensStatus()
            if (wasRunning) {
                restartRecognizeAfterStop = true
                statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.RESTARTING_LENS)
                statusPanel.setError(null)
                stopRecognize()
            }
        }
    }

    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        super.onDroneChangedListener(connected, drone)
        droneDevice = drone
        refreshDeviceAndLensStatus()
        if (connected) {
            drone.getKeyManager().listen(keyHeartbeatInfo, heartbeatCallback)
            syncAiDetectEnabledFromCache()
        } else {
            statusPanel.setDevice(false, null)
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.DISCONNECTED)
        }
    }

    private fun initRecognizeOptions() {
        val selectedIds = AiDemoTargetTypePrefs.getSelectedTypeIds(requireContext())
        binding.layoutTargetTypeChecks.removeAllViews()
        targetTypeCheckBoxes.clear()
        AiDemoTargetTypeCatalog.options.forEach { option ->
            val checkBox = CheckBox(requireContext()).apply {
                text = option.label
                textSize = 11f
                isChecked = option.typeId in selectedIds
                setOnCheckedChangeListener { _, _ -> onTargetTypeSelectionChanged() }
            }
            targetTypeCheckBoxes.add(checkBox)
            binding.layoutTargetTypeChecks.addView(checkBox)
        }
        updateTargetTypeSummaryLabel()

        binding.switchSoundAlert.isChecked = AiDemoTargetTypePrefs.isSoundAlertEnabled(requireContext())
        soundPlayer.setSoundEnabled(binding.switchSoundAlert.isChecked)
        binding.switchSoundAlert.setOnCheckedChangeListener { _, checked ->
            AiDemoTargetTypePrefs.setSoundAlertEnabled(requireContext(), checked)
            soundPlayer.setSoundEnabled(checked)
        }

        binding.tvToggleTargetTypes.setOnClickListener {
            if (isRunning || isProcessing) {
                statusPanel.setError(getString(R.string.ai_demo_target_types_running_hint))
                return@setOnClickListener
            }
            targetTypesExpanded = !targetTypesExpanded
            binding.layoutTargetTypeChecks.isVisible = targetTypesExpanded
            updateTargetTypeSummaryLabel()
        }

        binding.btnClearSessionRecord.setOnClickListener {
            sessionRecordStore.clear()
            refreshSessionRecordUi()
        }
        refreshSessionRecordUi()
    }

    private fun onTargetTypeSelectionChanged() {
        if (isRunning || isProcessing) {
            return
        }
        val selected = getSelectedTargetTypeIds()
        AiDemoTargetTypePrefs.saveSelectedTypeIds(requireContext(), selected)
        updateTargetTypeSummaryLabel()
        statusPanel.setError(null)
    }

    private fun getSelectedTargetTypeIds(): Set<Int> {
        return targetTypeCheckBoxes.mapIndexedNotNull { index, box ->
            if (box.isChecked) {
                AiDemoTargetTypeCatalog.options.getOrNull(index)?.typeId
            } else {
                null
            }
        }.toSet()
    }

    private fun updateTargetTypeSummaryLabel() {
        val count = getSelectedTargetTypeIds().size
        val labelRes = if (targetTypesExpanded) {
            R.string.ai_demo_target_types_collapse
        } else {
            R.string.ai_demo_target_types_expand
        }
        binding.tvToggleTargetTypes.text = getString(labelRes, count)
        val canEditTypes = !isRunning && !isProcessing
        targetTypeCheckBoxes.forEach { it.isEnabled = canEditTypes }
        binding.tvToggleTargetTypes.alpha = if (canEditTypes) 1f else 0.5f
    }

    private fun refreshSessionRecordUi() {
        val count = sessionRecordStore.eventCount()
        binding.tvSessionRecordTitle.text = if (count == 0) {
            getString(R.string.ai_demo_session_record_empty)
        } else {
            getString(R.string.ai_demo_session_record_title, count)
        }
        binding.tvSessionRecord.text = sessionRecordStore.formatForDisplay()
    }

    private fun setTargetTypeControlsEnabled(processing: Boolean) {
        binding.switchSoundAlert.isEnabled = !processing
        binding.tvToggleTargetTypes.isEnabled = !processing && !isRunning
        binding.btnClearSessionRecord.isEnabled = !processing
        updateTargetTypeSummaryLabel()
    }

    private fun recordSessionTargets(aiInfo: AIInfo) {
        aiInfo.nodes?.forEach { node ->
            sessionRecordStore.recordIfNew(node.objectID, node.type, node.confidence)
        }
        refreshSessionRecordUi()
    }

    private fun refreshDeviceAndLensStatus() {
        val device = droneDevice
        if (device == null) {
            statusPanel.setDevice(false, null)
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.DISCONNECTED)
            return
        }
        statusPanel.setDevice(true, device.deviceNumber().toString())
        val lensType = getLensType()
        val lensId = getCameraLensId(device, lensType) ?: 0
        statusPanel.setLens(lensType, lensId)
    }

    private fun initPlayer() {
        val device = droneDevice ?: return
        val deviceId = device.deviceNumber()

        val zoomLensType = getLensType(isZoom = true)
        val zoomCameraId = getCameraLensId(droneDevice = device, lensType = zoomLensType) ?: 0

        SDKLog.i(TAG, "initPlayer=>[zoom] deviceId:$deviceId;lensType:$zoomLensType;cameraId:$zoomCameraId")
        autelPlayerZoom = AutelPlayer(deviceId.toString(), zoomCameraId, RenderMode.RenderWithSurface)
        autelPlayerZoom?.removeVideoView()
        autelPlayerZoom?.addVideoView(binding.playerViewZoom)
        autelPlayerZoom?.startPlayer()

        val thermalLensType = getLensType(isZoom = false)
        val thermalCameraId = getCameraLensId(droneDevice = device, lensType = thermalLensType) ?: 0

        SDKLog.i(TAG, "initPlayer=>[thermal] deviceId:$deviceId;lensType:$thermalLensType;cameraId:$thermalCameraId")
        autelPlayerThermal = AutelPlayer(deviceId.toString(), thermalCameraId, RenderMode.RenderWithSurface)
        autelPlayerThermal?.removeVideoView()
        autelPlayerThermal?.addVideoView(binding.playerViewThermal)
    }

    private fun switchPlaySource(isZoom: Boolean) {
        SDKLog.i(TAG, "switchPlaySource=>isZoom:$isZoom")
        detectionDelegate.clear()
        statusPanel.clearTargets()

        if (isZoom) {
            autelPlayerThermal?.stopPlayer()
            binding.playerViewThermal.isVisible = false
            binding.playerViewZoom.isVisible = true
            autelPlayerZoom?.startPlayer()
        } else {
            autelPlayerZoom?.stopPlayer()
            binding.playerViewZoom.isVisible = false
            binding.playerViewThermal.isVisible = true
            autelPlayerThermal?.startPlayer()
        }
    }

    private fun getLensType(isZoom: Boolean): LensTypeEnum {
        val lens = droneDevice?.getCameraAbilitySetManger()
            ?.getLensList(droneDevice?.getGimbalDeviceType())
        return if (isZoom) {
            resolveVisibleLensType(lens)
        } else {
            resolveThermalLensType(lens)
        }
    }

    private fun getLensType(): LensTypeEnum {
        return if (binding.rbZoom.isChecked) {
            getLensType(isZoom = true)
        } else {
            getLensType(isZoom = false)
        }
    }

    private fun resolveVisibleLensType(lens: List<LensTypeEnum>?): LensTypeEnum {
        if (lens?.contains(LensTypeEnum.Zoom) == true) return LensTypeEnum.Zoom
        if (lens?.contains(LensTypeEnum.TeleZoom) == true) return LensTypeEnum.TeleZoom
        return LensTypeEnum.Zoom
    }

    private fun resolveThermalLensType(lens: List<LensTypeEnum>?): LensTypeEnum {
        if (lens?.contains(LensTypeEnum.Thermal) == true) return LensTypeEnum.Thermal
        if (lens?.contains(LensTypeEnum.TeleThermal) == true) return LensTypeEnum.TeleThermal
        return LensTypeEnum.Thermal
    }

    private fun getCameraLensId(droneDevice: IAutelDroneDevice, lensType: LensTypeEnum): Int? {
        return droneDevice.getCameraAbilitySetManger()
            .getLenId(lensType, droneDevice.getGimbalDeviceType())
    }

    private fun getAiEnableFunc(): ActionFuncEnum {
        return droneDevice?.getKeyManager()?.getCacheValue(keyHeartbeatInfo)?.aiEnableFunc
            ?: ActionFuncEnum.UNKNOWN
    }

    private fun syncAiDetectEnabledFromCache() {
        val func = getAiEnableFunc()
        aiDetectEnabled = func == ActionFuncEnum.AI_DETECT
        statusPanel.setHeartbeat(func)
    }

    @Suppress("DEPRECATION")
    private fun startRecognize() {
        val device = droneDevice ?: run {
            statusPanel.setError(getString(R.string.ai_detect_device_disconnected))
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.ERROR)
            return
        }
        val key = KeyTools.createKey(AITrackingKey.KeyAiEnter)
        val lensType = getLensType()
        val cameraLenId = getCameraLensId(droneDevice = device, lensType = lensType) ?: 0
        statusPanel.setLens(lensType, cameraLenId)

        val isSupport = device.getCameraAbilitySetManger().getCameraSupport2()?.getAiServiceEnabled(lensType)
        if (isSupport != true) {
            SDKLog.e(TAG, "startRecognize->AI not supported on lensType:$lensType")
            isRunning = false
            val msg = getString(R.string.ai_demo_lens_not_support, lensType.name)
            statusPanel.setError(msg)
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.UNSUPPORTED)
            updateStartButton(false)
            return
        }

        val targetList = getSelectedTargetTypeIds().toList()
        if (targetList.isEmpty()) {
            statusPanel.setError(getString(R.string.ai_demo_target_types_empty))
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.ERROR)
            return
        }

        val bean = AIEnableConfigBean().apply {
            sceneType = AiDetectSceneTypeEnum.SCENE_TYPE_UNIVERSAL
            targetTypes = targetList
            propertyInfos = emptyList()
            cameraId = cameraLenId
        }

        SDKLog.i(TAG, "startRecognize->lensType:$lensType;cameraLenId:$cameraLenId;params:$bean")
        isProcessing = true
        statusPanel.setError(null)
        statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.STARTING)
        setTargetTypeControlsEnabled(false)
        binding.btnStartRecognize.isEnabled = false
        binding.btnStartRecognize.text = getString(R.string.debug_recognition_start) + "..."

        val seiKey = KeyTools.createLensKey(CameraKey.KeyCameraTransferSeiEnable, cameraLenId)
        device.getKeyManager().setValue(seiKey, true, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                SDKLog.i(TAG, "startRecognize->enable SEI success cameraId:$cameraLenId")
                performAiEnter(device, key, bean)
            }

            override fun onFailure(code: IAutelCode, msg: String?) {
                SDKLog.e(TAG, "startRecognize->enable SEI failed cameraId:$cameraLenId,error:${code.code},msg:$msg, continue AI enter")
                performAiEnter(device, key, bean)
            }
        })
    }

    private fun performAiEnter(
        device: IAutelDroneDevice,
        key: AutelKey.ActionKey<AIEnableConfigBean, Void>,
        bean: AIEnableConfigBean,
    ) {
        device.getKeyManager().performAction(key, bean, object : CommonCallbacks.CompletionCallbackWithParam<Void> {
            override fun onSuccess(t: Void?) {
                SDKLog.d(TAG, "start recognize success")
                isProcessing = false
                isRunning = true
                sessionRecordStore.startSession()
                soundPlayer.setSessionActive(true)
                safePost {
                    refreshSessionRecordUi()
                    statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.RUNNING)
                    updateStartButton(true)
                }
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                SDKLog.d(TAG, "start recognize failed $error $msg")
                isProcessing = false
                isRunning = false
                soundPlayer.setSessionActive(false)
                safePost {
                    statusPanel.setError(getString(R.string.ai_demo_start_failed, msg ?: error.code.toString()))
                    statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.ERROR)
                    updateStartButton(false)
                }
            }
        })
    }

    private fun stopRecognize() {
        val key = KeyTools.createKey(AITrackingKey.KeyAiExit)
        val bean = AIDisableConfigBean(0)
        isProcessing = true
        if (!restartRecognizeAfterStop) {
            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.STOPPING)
        }
        if (isAdded) {
            binding.btnStartRecognize.isEnabled = false
            binding.btnStartRecognize.text = getString(R.string.debug_recognition_stop) + "..."
        }
        setTargetTypeControlsEnabled(false)
        droneDevice?.getKeyManager()?.performAction(
            key, bean, callback = object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                override fun onSuccess(t: Void?) {
                    SDKLog.i(TAG, "stopRecognize->on success, restartAfterStop=$restartRecognizeAfterStop")
                    isProcessing = false
                    isRunning = false
                    soundPlayer.setSessionActive(false)
                    val shouldRestart = restartRecognizeAfterStop
                    restartRecognizeAfterStop = false
                    safePost {
                        detectionDelegate.clear()
                        statusPanel.clearTargets()
                        if (shouldRestart) {
                            SDKLog.i(TAG, "stopRecognize->auto restart on new lens:${getLensType()}")
                            refreshDeviceAndLensStatus()
                            startRecognize()
                        } else {
                            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.IDLE)
                            statusPanel.setError(null)
                            updateStartButton(false)
                        }
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "stopRecognize->on failure:[error:${error.code},msg:$msg]")
                    isProcessing = false
                    val shouldRestart = restartRecognizeAfterStop
                    restartRecognizeAfterStop = false
                    safePost {
                        if (shouldRestart) {
                            SDKLog.w(TAG, "stopRecognize->exit failed, still try restart on new lens")
                            statusPanel.setError(getString(R.string.ai_demo_stop_failed, msg ?: error.code.toString()))
                            refreshDeviceAndLensStatus()
                            startRecognize()
                        } else {
                            statusPanel.setError(getString(R.string.ai_demo_stop_failed, msg ?: error.code.toString()))
                            statusPanel.setUiState(AiDemoStatusPanel.AiDemoUiState.ERROR)
                            updateStartButton(isRunning)
                        }
                    }
                }
            })
    }

    private fun updateStartButton(running: Boolean) {
        binding.btnStartRecognize.isEnabled = true
        binding.btnStartRecognize.text = if (running) {
            getString(R.string.debug_recognition_stop)
        } else {
            getString(R.string.debug_recognition_start)
        }
        setTargetTypeControlsEnabled(!isProcessing)
    }

    override fun onDestroyView() {
        droneDevice?.getKeyManager()?.cancelListen(keyHeartbeatInfo, heartbeatCallback)
        super.onDestroyView()
    }

    override fun onDestroy() {
        SDKLog.i(TAG, "onDestroy=>AIEnable:${getAiEnableFunc()}")
        restartRecognizeAfterStop = false
        soundPlayer.release()
        stopRecognize()
        autelPlayerZoom?.releasePlayer()
        autelPlayerThermal?.releasePlayer()
        AIInfoManager.getInstance().removeAIInfoCallback(this)
        detectionDelegate.detach()
        super.onDestroy()
    }

    override fun onAIInfoReceived(portId: Int, aiInfo: AIInfo?) {
        logAiInfo("port:$portId", portId, aiInfo)
    }

    override fun onAIInfoReceived(deviceId: String?, cameraId: Int, aiInfo: AIInfo?) {
        if (!isAdded) return
        logAiInfo("device:$deviceId", cameraId, aiInfo)
        if (aiInfo == null) return
        if (aiInfo.aiMode != 0) {
            SDKLog.d(TAG, "onAIInfoReceived->ignore aiMode:${aiInfo.aiMode}, cameraId:$cameraId")
            return
        }

        if (!aiDetectEnabled) {
            safePost {
                detectionDelegate.clear()
                statusPanel.clearTargets()
                soundPlayer.onTargetPresenceChanged(false)
            }
            return
        }

        val device = droneDevice ?: return
        val expectedLensId = getCameraLensId(device, getLensType()) ?: return
        if (device.deviceNumber().toString() != deviceId || aiInfo.lensId != expectedLensId) {
            safePost {
                detectionDelegate.clear()
                statusPanel.setTargetInfo(0, aiInfo.width, aiInfo.height)
            }
            return
        }

        val targets = detectionDelegate.processAIInfo(aiInfo) ?: return
        recordSessionTargets(aiInfo)
        soundPlayer.onTargetPresenceChanged(targets.isNotEmpty())

        viewLifecycleOwner.lifecycleScope.launch {
            statusPanel.setTargetInfo(targets.size, aiInfo.width, aiInfo.height)
            val builder = StringBuilder()
            targets.forEach {
                builder.append("[${it.typeName}] (${it.rect.left.toInt()},${it.rect.top.toInt()})-(${it.rect.right.toInt()},${it.rect.bottom.toInt()})\n")
            }
            statusPanel.setDetail(builder.toString().trim())
        }
    }

    private fun logAiInfo(source: String, cameraId: Int, aiInfo: AIInfo?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAiInfoLogMs < AI_INFO_LOG_INTERVAL_MS) {
            return
        }
        lastAiInfoLogMs = now
        if (aiInfo == null) {
            SDKLog.w(TAG, "onAIInfoReceived->$source cameraId:$cameraId aiInfo=null")
            return
        }
        SDKLog.i(
            TAG,
            "onAIInfoReceived->$source callbackCameraId:$cameraId valid:${aiInfo.valid} mode:${aiInfo.aiMode} " +
                "count:${aiInfo.count} nodes:${aiInfo.nodes?.size ?: 0} lensId:${aiInfo.lensId} " +
                "size:${aiInfo.width}x${aiInfo.height} seq:${aiInfo.seqNum} aiDetectEnabled:$aiDetectEnabled heartbeat:${getAiEnableFunc()}"
        )
    }

    companion object {
        private const val AI_INFO_LOG_INTERVAL_MS = 1000L
    }
}
