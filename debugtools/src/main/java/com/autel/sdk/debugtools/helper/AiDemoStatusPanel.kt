package com.autel.sdk.debugtools.helper

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.bean.ActionFuncEnum
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.sdk.debugtools.R

/**
 * AI demo persistent status panel, aligned with Athena heartbeat + local operation state display:
 * - Session state, lens, device and target count always visible
 * - Errors shown persistently (not a fleeting toast)
 */
class AiDemoStatusPanel(
    private val root: View,
    private val expectedHeartbeat: ActionFuncEnum,
) {
    private val tvSessionState: TextView = root.findViewById(R.id.tv_ai_session_state)
    private val tvLensInfo: TextView = root.findViewById(R.id.tv_ai_lens_info)
    private val tvDeviceInfo: TextView = root.findViewById(R.id.tv_ai_device_info)
    private val tvTargetInfo: TextView = root.findViewById(R.id.tv_ai_target_info)
    private val tvError: TextView = root.findViewById(R.id.tv_ai_error)
    private val tvDetail: TextView = root.findViewById(R.id.tv_ai_detail)

    private var uiState: AiDemoUiState = AiDemoUiState.IDLE
    private var heartbeatFunc: ActionFuncEnum = ActionFuncEnum.UNKNOWN
    private var deviceConnected = false
    private var deviceId: String? = null
    private var lensType: LensTypeEnum? = null
    private var lensId: Int? = null
    private var targetCount = 0
    private var streamWidth = 0
    private var streamHeight = 0
    private var trackStatusText: String? = null

    fun setUiState(state: AiDemoUiState) {
        uiState = state
        renderSessionState()
    }

    fun setHeartbeat(aiEnableFunc: ActionFuncEnum) {
        heartbeatFunc = aiEnableFunc
        renderSessionState()
    }

    fun setDevice(connected: Boolean, id: String?) {
        deviceConnected = connected
        deviceId = id
        renderDeviceInfo()
        if (!connected) {
            setUiState(AiDemoUiState.DISCONNECTED)
        }
    }

    fun setLens(type: LensTypeEnum, id: Int) {
        lensType = type
        lensId = id
        renderLensInfo()
    }

    fun setTargetInfo(count: Int, width: Int, height: Int) {
        targetCount = count
        streamWidth = width
        streamHeight = height
        renderTargetInfo()
    }

    fun setTrackStatus(text: String?) {
        trackStatusText = text
        renderTargetInfo()
    }

    fun setError(message: String?) {
        if (message.isNullOrBlank()) {
            tvError.isVisible = false
            tvError.text = ""
        } else {
            tvError.isVisible = true
            tvError.text = message
        }
    }

    fun setDetail(text: String?) {
        tvDetail.text = text.orEmpty()
    }

    fun clearTargets() {
        setTargetInfo(0, streamWidth, streamHeight)
        setDetail("")
    }

    private fun renderSessionState() {
        val (text, colorRes) = when {
            !deviceConnected -> {
                root.context.getString(R.string.ai_demo_state_disconnected) to R.color.debug_color_97_97_97
            }
            uiState == AiDemoUiState.UNSUPPORTED -> {
                root.context.getString(R.string.ai_demo_state_unsupported) to R.color.debug_color_orange
            }
            uiState == AiDemoUiState.ERROR -> {
                root.context.getString(R.string.ai_demo_state_error) to R.color.debug_color_FF0000
            }
            uiState == AiDemoUiState.STARTING -> {
                root.context.getString(R.string.ai_demo_state_starting) to R.color.debug_color_orange
            }
            uiState == AiDemoUiState.STOPPING -> {
                root.context.getString(R.string.ai_demo_state_stopping) to R.color.debug_color_orange
            }
            uiState == AiDemoUiState.RESTARTING_LENS -> {
                root.context.getString(R.string.ai_demo_state_restarting_lens) to R.color.debug_color_orange
            }
            uiState == AiDemoUiState.RUNNING && heartbeatFunc == expectedHeartbeat -> {
                sessionRunningLabel() to R.color.debug_color_4CAF50
            }
            uiState == AiDemoUiState.RUNNING && heartbeatFunc != expectedHeartbeat -> {
                root.context.getString(
                    R.string.ai_demo_state_running_heartbeat_mismatch,
                    heartbeatLabel(heartbeatFunc),
                ) to R.color.debug_color_orange
            }
            heartbeatFunc == expectedHeartbeat -> {
                sessionRunningLabel() to R.color.debug_color_4CAF50
            }
            else -> {
                root.context.getString(R.string.ai_demo_state_idle) to R.color.debug_gray_light
            }
        }
        tvSessionState.text = text
        tvSessionState.setTextColor(root.context.getColor(colorRes))
    }

    private fun sessionRunningLabel(): String {
        return if (expectedHeartbeat == ActionFuncEnum.AI_DETECT) {
            root.context.getString(R.string.ai_demo_state_running_detect, heartbeatLabel(heartbeatFunc))
        } else {
            root.context.getString(R.string.ai_demo_state_running_track, heartbeatLabel(heartbeatFunc))
        }
    }

    private fun renderLensInfo() {
        val type = lensType?.name ?: "-"
        val id = lensId?.toString() ?: "-"
        tvLensInfo.text = root.context.getString(R.string.ai_demo_lens_info, type, id)
    }

    private fun renderDeviceInfo() {
        tvDeviceInfo.text = if (deviceConnected) {
            root.context.getString(R.string.ai_demo_device_connected, deviceId ?: "-")
        } else {
            root.context.getString(R.string.ai_demo_device_disconnected)
        }
    }

    private fun renderTargetInfo() {
        val stream = if (streamWidth > 0 && streamHeight > 0) {
            "${streamWidth}x${streamHeight}"
        } else {
            "-"
        }
        val trackSuffix = trackStatusText?.let { "  |  $it" }.orEmpty()
        tvTargetInfo.text = root.context.getString(
            R.string.ai_demo_target_info,
            targetCount,
            stream,
        ) + trackSuffix
    }

    private fun heartbeatLabel(func: ActionFuncEnum): String {
        return when (func) {
            ActionFuncEnum.AI_DETECT -> "AI_DETECT"
            ActionFuncEnum.AI_TRACK -> "AI_TRACK"
            ActionFuncEnum.AI_NONE -> "AI_NONE"
            else -> func.name
        }
    }

    enum class AiDemoUiState {
        DISCONNECTED,
        IDLE,
        STARTING,
        RUNNING,
        STOPPING,
        RESTARTING_LENS,
        ERROR,
        UNSUPPORTED,
    }
}
