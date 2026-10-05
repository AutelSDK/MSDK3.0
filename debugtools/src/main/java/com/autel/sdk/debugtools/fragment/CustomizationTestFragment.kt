package com.autel.sdk.debugtools.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.device.DroneType
import com.autel.drone.sdk.vmodelx.enums.FrequencyBand
import com.autel.drone.sdk.vmodelx.enums.SignalStrengthLevel
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.FrequencyBandManager
import com.autel.drone.sdk.vmodelx.manager.frequency.OnFrequencyBandListener
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.alink.bean.AirLinkSignalStrengthBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightControlKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightMissionKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightPropertyKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.MissionManagerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.bean.WatermarkInfoBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DronePositionCorrectionBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateHFNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateLFNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.LocationCoordinate3D
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.enums.FlightControlMainModeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.CoordinateType
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.UAVImageControlMessageBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.UAVPositionControlMessageBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.UAVVelocityControlMessageBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.enums.AttitudeLostActionEnum
import com.autel.sdk.debugtools.KeyValueDialogUtil
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.SDKSingleButtonDialog
import com.autel.sdk.debugtools.databinding.FragmentCustomizationTestBinding
import com.autel.sdk.debugtools.dialog.HomeLocationDialog
import com.autel.sdk.debugtools.dialog.UAVControlDialog
import com.autel.sdk.debugtools.listener.OnItemSelectedListener
import com.autel.sdk.debugtools.listener.OnSeekBarChangeListener
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * MDE does not currently support RIO
 */
@Keep
class CustomizationTestFragment : AutelFragment() {
    @Keep
    companion object {
        private const val TAG = "CustomizationTestFragment"
        private const val MESSAGE_UPDATE_UI = 0
        private const val LOG_MAX_LENGTH = 8000
    }

    private lateinit var binding: FragmentCustomizationTestBinding

    private val gson = GsonBuilder().create()
    private var keyManager: IKeyManager? = null

    private val keyAttitudeLostAction = KeyTools.createKey(MissionManagerKey.KeyAttitudeLostAction)
    private val keyAttitudeLostHoverHeight =
        KeyTools.createKey(MissionManagerKey.KeyAttitudeLostHoverHeight)
    private val keyAttitudeLostEscapeDirection =
        KeyTools.createKey(MissionManagerKey.KeyAttitudeLostEscapeDirection)
    private val keySetMediaDirName = KeyTools.createKey(CameraKey.KeyCameraSaveMapUserDirName)
    private val keyVelocityControl = KeyTools.createKey(FlightMissionKey.KeyUAVVelocityControl)
    private val keyUAVImageControl = KeyTools.createKey(FlightMissionKey.KeyUAVImageControl)
    private val keyUAVPositionControl = KeyTools.createKey(FlightMissionKey.KeyUAVPositionControl)
    private val keyFollowHeight = KeyTools.createKey(FlightMissionKey.KeyFollowRCHeight)

    private var lastTime = 0L
    @Volatile
    private var airLinkRealSignalStrength: Int = 0
    private val airLinkSignalKey = KeyTools.createKey(AirLinkKey.KeyALinkSignalStrength)
    private val airLinkSignalListener = object : CommonCallbacks.KeyListener<AirLinkSignalStrengthBean> {
        override fun onValueChange(oldValue: AirLinkSignalStrengthBean?, newValue: AirLinkSignalStrengthBean) {
            airLinkRealSignalStrength = newValue.realSignalStrength
        }
    }

    private var isSpinnerInitialized = false
    private val frequencyListener: OnFrequencyBandListener = object : OnFrequencyBandListener {
        override fun onChange(country: String?, list: List<FrequencyBand>) {
            if (isSpinnerInitialized && list.isNotEmpty()) {
                SDKLog.i(TAG, "frequencyListener onChange list.isNotEmpty()")
                return
            }
            handler.post {
                initSpinnerOption(binding.spinnerFrequency, list.map { it.tag })
            }
        }
    }

    private var sequence: Int = 1
    private var isInFollowMission = false

    private val logBuffer = StringBuilder()
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var logPanelExpanded = false

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MESSAGE_UPDATE_UI -> fixedFrequencyRefresh()
        }
        return true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCustomizationTestBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SDKLog.i(TAG, "onViewCreated->isSpinnerInitialized:$isSpinnerInitialized")
        isSpinnerInitialized = false
        FrequencyBandManager.get().removeListener(frequencyListener)
        FrequencyBandManager.get().addListener(frequencyListener)

        // Listen to image transmission signal strength (from the /signal_info ROS2 topic)
        try {
            DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
                .listen(airLinkSignalKey, airLinkSignalListener)
        } catch (e: Exception) {
            SDKLog.e(TAG, "listen airLinkSignalKey failed: ${e.message}")
        }

        keyManager = DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        keyManager?.getValue(
            keySetMediaDirName,
            object : CommonCallbacks.CompletionCallbackWithParam<String> {
                override fun onSuccess(t: String?) {
                    SDKLog.i(TAG, "get KeyCameraSaveMapUserDirName onSuccess($t)")
                    appendLog("get [CameraKey.KeyCameraSaveMapUserDirName]: $t", true)
                    handler.post {
                        binding.tvCurrentDirectory.text =
                            getString(R.string.current_directory, t ?: "")
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "get KeyCameraSaveMapUserDirName onFailure($msg, $error)")
                    appendLog("get [CameraKey.KeyCameraSaveMapUserDirName]: $error $msg", false)
                }
            })


        initAttitudeSetting()
        initJoystickSetting()
        initFollowHeightSetting()

        bindSwitchWithKey(binding.switchVpsEnable, FlightControlKey.KeyFcsVisionEnable, keyManager)
        bindSwitchWithKey(binding.switchGnssEnable, FlightPropertyKey.KeyFcsEnGpsMode, keyManager)

//        bindSwitchWithKey(binding.switchRioEnable, RadarPropertyKey.KeyRadarRIOSwitch, keyManager)
        bindSwitchWithKey(binding.switchAttiEnable, FlightPropertyKey.KeyAttiMode, keyManager)

        binding.btnHomePointSet.setOnClickListener { setHomePoint() }
        binding.btnWatermarkEffect.setOnClickListener {
            val controller: NavController = Navigation.findNavController(requireView())
            controller.navigate(R.id.ondemand_stream_page)
        }
        binding.btnStartPointSet.setOnClickListener {
            setStartPoint()
        }

        binding.btnMediaDirectory.setOnClickListener { setMediaDirectory() }
        binding.btnNightSlam.setOnClickListener { enterNightSlam() }
        binding.btnExitNightSlam.setOnClickListener { exitNightSlam() }

        binding.btnWiredRelay.setOnClickListener {
            val controller: NavController = Navigation.findNavController(requireView())
            controller.navigate(R.id.action_wired_relay)
        }

        binding.btnTaskDirectory.setOnClickListener {
            val controller: NavController = Navigation.findNavController(requireView())
            controller.navigate(R.id.mission_demo_page)
        }

        //binding.btnRotationAttitude.setOnClickListener { rotationGimbalAttitude() }
        //binding.btnGimbalCenter.setOnClickListener { centerGimbalAttitude() }

        binding.btnPositionSet.setOnClickListener { setDronePosition() }
        binding.btnVelocitySet.setOnClickListener { setDroneVelocity() }
        binding.btnImageSet.setOnClickListener {
            val controller: NavController = Navigation.findNavController(requireView())
            controller.navigate(R.id.image_move_test_page)
        }
        binding.btnJoystickSet.setOnClickListener {
            val bundle = Bundle().apply { putBoolean("isNewVersion", true) }
            val controller: NavController = Navigation.findNavController(requireView())
            controller.navigate(R.id.action_virtual_joystick, bundle)
        }

        binding.btnEnterFollowMission.setOnClickListener { enterFollowMission() }
        binding.btnExitFollowMission.setOnClickListener { exitFollowMission() }

        binding.btnTargetTracking.setOnClickListener {
            val controller: NavController = requireView().findNavController()
            controller.navigate(R.id.action_open_ai_track_page)
        }

        binding.btnDataSecurity.isVisible = false
        val drone = DeviceManager.getDeviceManager().getFirstDroneDevice()
        if (drone?.getDroneType() == DroneType.MODEL_H) {
            binding.btnExitNightSlam.isVisible = false
            binding.btnNightSlam.isVisible = false
        }

        //keyManager?.listen(keyDroneSystemStatusLFNtfy, droneSystemStatusListener)
        //keyManager?.listen(keyDroneSystemStatusHFNtfy, droneSystemStatusHFListener)
        handler.sendEmptyMessageDelayed(MESSAGE_UPDATE_UI, 300)
        initLogPanel()
    }


    private fun initLogPanel() {
        binding.logPanelHeader.setOnClickListener {
            logPanelExpanded = !logPanelExpanded
            binding.logScrollView.visibility = if (logPanelExpanded) View.VISIBLE else View.GONE
            binding.tvLogTitle.text = getString(
                if (logPanelExpanded) R.string.debug_log_panel_title_expanded
                else R.string.debug_log_panel_title
            )
            if (logPanelExpanded) {
                binding.logScrollView.post {
                    binding.logScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
        binding.btnLogClear.setOnClickListener {
            logBuffer.clear()
            binding.tvOperationLog.text = ""
        }
    }

    @Synchronized
    private fun appendLog(msg: String, success: Boolean, showToast: Boolean = false) {
        val time = logTimeFormat.format(Date())
        val tag = if (success) "onSuccess" else "onFailure"
        val line = "$time [$tag] $msg\n"
        logBuffer.append(line)
        if (logBuffer.length > LOG_MAX_LENGTH) {
            val trimmed = logBuffer.substring(logBuffer.length - LOG_MAX_LENGTH)
            logBuffer.clear()
            logBuffer.append(trimmed)
        }
        if (!isAdded) return
        handler.post {
            if (!isAdded) return@post
            binding.tvOperationLog.text = logBuffer.toString()
            if (logPanelExpanded) {
                binding.logScrollView.post {
                    binding.logScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
            if (showToast) {
                val prefix = if (success) "Success" else "Failed"
                showToast("$prefix: $msg")
            }
        }
    }

    private fun initAttitudeSetting() {

        keyManager?.getValue(
            keyAttitudeLostAction,
            object : CommonCallbacks.CompletionCallbackWithParam<AttitudeLostActionEnum> {
                override fun onSuccess(t: AttitudeLostActionEnum?) {
                    SDKLog.i(TAG, "get KeyAttitudeLostAction onSuccess($t)")
                    appendLog("get [MissionManagerKey.KeyAttitudeLostAction]: $t", true)
                    activity?.runOnUiThread { initLostAction(t) }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "get KeyAttitudeLostAction onFailure($msg, $error)")
                    appendLog("get [MissionManagerKey.KeyAttitudeLostAction]: $error $msg", false)
                    activity?.runOnUiThread { initLostAction(null) }
                }
            }
        )

        keyManager?.getValue(
            keyAttitudeLostHoverHeight,
            object : CommonCallbacks.CompletionCallbackWithParam<Float> {
                @SuppressLint("DefaultLocale")
                override fun onSuccess(t: Float?) {
                    SDKLog.i(TAG, "get KeyAttitudeLostHoverHeight onSuccess($t)")
                    appendLog("get [MissionManagerKey.KeyAttitudeLostHoverHeight]: $t", true)
                    activity?.runOnUiThread {
                        binding.inputHoverHeight.setText(String.format("%.2f", t ?: 0f))
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "get KeyAttitudeLostHoverHeight onFailure($msg, $error)")
                    appendLog(
                        "get [MissionManagerKey.KeyAttitudeLostHoverHeight]: $error $msg",
                        false
                    )
                    activity?.runOnUiThread {
                        binding.inputHoverHeight.setText("200.0")
                    }
                }
            }
        )

        keyManager?.getValue(
            keyAttitudeLostEscapeDirection,
            object : CommonCallbacks.CompletionCallbackWithParam<Float> {
                override fun onSuccess(t: Float?) {
                    SDKLog.i(TAG, "get KeyAttitudeLostEscapeDirection onSuccess($t)")
                    appendLog("get [MissionManagerKey.KeyAttitudeLostEscapeDirection]: $t", true)
                    activity?.runOnUiThread { initEscapeDirection(t) }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "get KeyAttitudeLostEscapeDirection onFailure($msg, $error)")
                    appendLog(
                        "get [MissionManagerKey.KeyAttitudeLostEscapeDirection]: $error $msg",
                        false
                    )
                    activity?.runOnUiThread { initEscapeDirection(0f) }
                }
            }
        )

        binding.btnSetAttitudeHeight.setOnClickListener {
            val height = binding.inputHoverHeight.text?.toString()?.toFloatOrNull() ?: 0f
            keyManager?.setValue(
                keyAttitudeLostHoverHeight,
                height,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        SDKLog.i(TAG, "set KeyAttitudeLostHoverHeight success($height)")
                        appendLog(
                            "set [MissionManagerKey.KeyAttitudeLostHoverHeight]: $height",
                            true,
                            showToast = true
                        )
                    }

                    override fun onFailure(code: IAutelCode, msg: String?) {
                        SDKLog.e(TAG, "set KeyAttitudeLostHoverHeight onFailure($msg, $code)")
                        appendLog(
                            "set [MissionManagerKey.KeyAttitudeLostHoverHeight]: $code $msg",
                            false,
                            showToast = true
                        )
                    }
                })
        }
    }

    /**
     * ATTI lost-connection action
     */
    private fun initLostAction(action: AttitudeLostActionEnum?) {
        val attitudeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_selected,
            resources.getStringArray(R.array.attitude_lost_actions)
        )
        attitudeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spAttitudeMode.adapter = attitudeAdapter
        binding.spAttitudeMode.setSelection(action?.getValue() ?: 0)
        binding.spAttitudeMode.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val newAction = AttitudeLostActionEnum.findEnum(position)
                SDKLog.i(TAG, "spAttitudeMode onItemSelected: $newAction")
                keyManager?.setValue(
                    keyAttitudeLostAction,
                    newAction,
                    object : CommonCallbacks.CompletionCallback {
                        override fun onSuccess() {
                            SDKLog.i(TAG, "set KeyAttitudeLostAction success: $newAction")
                            appendLog(
                                "set [MissionManagerKey.KeyAttitudeLostAction]: $newAction",
                                true,
                                showToast = true
                            )
                        }

                        override fun onFailure(code: IAutelCode, msg: String?) {
                            SDKLog.e(TAG, "set KeyAttitudeLostAction onFailure($msg, $code)")
                            appendLog(
                                "set [MissionManagerKey.KeyAttitudeLostAction]: $code $msg",
                                false,
                                showToast = true
                            )
                        }
                    })
            }
        }
    }

    private fun initEscapeDirection(direction: Float?) {

        val progress = (direction ?: 0f).toInt()
        binding.sbAttitudeDirection.progress = progress
        binding.tvCurrentDegree.text = "${progress}°"

        binding.sbAttitudeDirection.setOnSeekBarChangeListener(object : OnSeekBarChangeListener() {

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val newProgress = seekBar?.progress?.toFloat() ?: 0f
                SDKLog.i(TAG, "sbAttitudeDirection onStopTrackingTouch $newProgress")
                keyManager?.setValue(
                    keyAttitudeLostEscapeDirection,
                    newProgress,
                    object : CommonCallbacks.CompletionCallback {
                        @SuppressLint("SetTextI18n")
                        override fun onSuccess() {
                            SDKLog.i(TAG, "set KeyAttitudeLostEscapeDirection success")
                            appendLog(
                                "set [MissionManagerKey.KeyAttitudeLostEscapeDirection]: ${newProgress.toInt()}°",
                                true,
                                showToast = true
                            )
                            activity?.runOnUiThread {
                                binding.tvCurrentDegree.text = "${newProgress.toInt()}°"
                            }
                        }

                        override fun onFailure(code: IAutelCode, msg: String?) {
                            SDKLog.e(
                                TAG,
                                "set KeyAttitudeLostEscapeDirection onFailure($msg, $code)"
                            )
                            appendLog(
                                "set [MissionManagerKey.KeyAttitudeLostEscapeDirection]: $code $msg",
                                false
                            )
                        }
                    }
                )
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
//        keyManager?.cancelListen(keyDroneSystemStatusLFNtfy, droneSystemStatusListener)
//        keyManager?.cancelListen(keyDroneSystemStatusHFNtfy, droneSystemStatusHFListener)
        FrequencyBandManager.get().removeListener(frequencyListener)
        try {
            DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
                .cancelListen(airLinkSignalKey, airLinkSignalListener)
        } catch (_: Exception) {}
        isSpinnerInitialized = false
        SDKLog.i(TAG, "onDestroyView")
    }

    /**
     * Set the Home point
     */
    private fun setHomePoint() {
        HomeLocationDialog(requireActivity(), true).apply {
            setOnConfirmListener {
                SDKLog.i(TAG, "setHomePoint $it")
                val key = KeyTools.createKey(FlightControlKey.KeyCustomHomeLocationDouble)
                keyManager?.performAction(
                    key,
                    it,
                    object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                        override fun onSuccess(t: Void?) {
                            SDKLog.i(TAG, "set home position success")
                            appendLog(
                                "performAction [FlightControlKey.KeyCustomHomeLocationDouble]",
                                true,
                                showToast = true
                            )
                        }

                        override fun onFailure(error: IAutelCode, msg: String?) {
                            SDKLog.e(TAG, "set home position onFailure($msg, $error)")
                            appendLog(
                                "performAction [FlightControlKey.KeyCustomHomeLocationDouble]: $error $msg",
                                false,
                                showToast = true
                            )
                        }
                    })
            }
            show()
        }
    }

    /**
     * Set the initial position
     */
    private fun setStartPoint() {

        HomeLocationDialog(requireActivity(), false).apply {
            setOnConfirmListener {
                val dronePosition = DronePositionCorrectionBean(
                    lon = it.longitude.toFloat(),
                    lat = it.latitude.toFloat()
                )
                SDKLog.i(TAG, "setStartPoint $it")
                val key = KeyTools.createKey(FlightControlKey.KeySetDronePosition)
                keyManager?.performAction(
                    key,
                    dronePosition,
                    object : CommonCallbacks.CompletionCallbackWithParam<Int> {
                        override fun onSuccess(t: Int?) {
                            if (t == 1) {
                                SDKLog.e(TAG, "Setting failed because the distance exceeds 5km")
                                appendLog(
                                    "performAction [FlightControlKey.KeySetDronePosition]: distance exceeds 5km",
                                    false,
                                    showToast = true
                                )
                            } else {
                                SDKLog.i(TAG, "setDronePositionCorrection success t=$t")
                                appendLog(
                                    "performAction [FlightControlKey.KeySetDronePosition]: $t",
                                    true,
                                    showToast = true
                                )
                            }
                        }

                        override fun onFailure(error: IAutelCode, msg: String?) {
                            SDKLog.e(TAG, "setDronePositionCorrection onFailure($msg, $error)")
                            appendLog(
                                "performAction [FlightControlKey.KeySetDronePosition]: $error $msg",
                                false,
                                showToast = true
                            )
                        }
                    })
            }
            show()
        }
    }

    private fun initSpinnerOption(spinner: Spinner, data: List<String>) {
        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_selected, data)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val band = FrequencyBand.find(data[position])
                FrequencyBandManager.get().setFrequencyBand(band, {
                    SDKLog.i(TAG, "setCurBandMode $band -> onSuccess ")
                    appendLog("set [FrequencyBandManager.setFrequencyBand]: $band", true, showToast = true)
                    handler.post {
                        binding.tvCurrentFrequency.text =
                            getString(R.string.current_settings, band.tag)
                    }
                }, {
                    SDKLog.i(TAG, "setCurBandMode $band -> onError=$it ")
                    appendLog("set [FrequencyBandManager.setFrequencyBand]: $band error=$it", false, showToast = true)
                })
            }
        }
        if (data.isNotEmpty()) {
            val current = FrequencyBandManager.get().getCurrFrequencyBand()
            SDKLog.i(
                TAG,
                "initSpinnerOption->sync getCurrFrequencyBand result: $current, data size: ${data.size}"
            )
            if (current != FrequencyBand.MODE_UNKNOWN) {
                val index = data.indexOf(current.tag)
                SDKLog.i(
                    TAG,
                    "initSpinnerOption->current band is not UNKNOWN, index: $index, tag: ${current.tag}"
                )
                if (index >= 0) {
                    spinner.setSelection(index)
                    SDKLog.i(
                        TAG,
                        "initSpinnerOption->setSelection to index: $index (${current.tag})"
                    )
                } else {
                    spinner.setSelection(0)
                    SDKLog.i(
                        TAG,
                        "initSpinnerOption->current band tag not found in data list, setSelection to 0"
                    )
                }
            } else {
                // If the current band is UNKNOWN, fetch the real band asynchronously
                SDKLog.i(
                    TAG,
                    "initSpinnerOption->current band is UNKNOWN, start async getCurrFrequencyBand"
                )
                FrequencyBandManager.get().getCurrFrequencyBand { band ->
                    SDKLog.i(
                        TAG,
                        "initSpinnerOption->async getCurrFrequencyBand callback result: $band"
                    )
                    handler.post {
                        if (band != FrequencyBand.MODE_UNKNOWN) {
                            val index = data.indexOf(band.tag)
                            SDKLog.i(
                                TAG,
                                "initSpinnerOption->async band is not UNKNOWN, index: $index, tag: ${band.tag}"
                            )
                            if (index >= 0) {
                                spinner.setSelection(index, false) // false means onItemSelected is not triggered
                                binding.tvCurrentFrequency.text =
                                    getString(R.string.current_settings, band.tag)
                                SDKLog.i(
                                    TAG,
                                    "initSpinnerOption->async setSelection to index: $index (${band.tag}), updated UI text"
                                )
                            } else {
                                spinner.setSelection(0)
                                SDKLog.i(
                                    TAG,
                                    "initSpinnerOption->async band tag not found in data list, setSelection to 0"
                                )
                            }
                        } else {
                            spinner.setSelection(0)
                            SDKLog.i(
                                TAG,
                                "initSpinnerOption->async band is still UNKNOWN, setSelection to 0"
                            )
                        }
                    }
                }
            }
            isSpinnerInitialized = true
        }
    }

    val keyHfDroneData = KeyTools.createKey(CommonKey.KeyDroneSystemStatusHFNtfy)
    private fun getDroneStateData(): DroneSystemStateHFNtfyBean? {
        return DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
            ?.getCacheValue(keyHfDroneData)
    }

    val keyLfDroneData = KeyTools.createKey(CommonKey.KeyDroneSystemStatusLFNtfy)
    private fun getDroneLFData(): DroneSystemStateLFNtfyBean? {
        return DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
            ?.getCacheValue(keyLfDroneData)
    }

    private fun fixedFrequencyRefresh() {
        var level = FrequencyBandManager.get().getSignalStrengthLevel()
        if (level == SignalStrengthLevel.SIGNAL_STRENGTH_NONE && airLinkRealSignalStrength > 0) {
            level = SignalStrengthLevel.parseValue(airLinkRealSignalStrength)
        }
        val strength = when (level) {
            SignalStrengthLevel.SIGNAL_STRENGTH_NONE -> "None"
            SignalStrengthLevel.SIGNAL_STRENGTH_WEAK -> "Weak"
            SignalStrengthLevel.SIGNAL_STRENGTH_MEDIUM -> "Medium"
            SignalStrengthLevel.SIGNAL_STRENGTH_STRONG -> "Strong"
        }
        binding.tvSignalStrength.text = getString(R.string.signal_strength, strength)

        val drone = DeviceManager.getDeviceManager().getFirstDroneDevice()
        getDroneStateData()?.let {
            val xSpeed = it.velocityX
            val ySpeed = it.velocityY
            val hSpeed = sqrt((xSpeed * xSpeed + ySpeed * ySpeed)) * 10 / 10f

            val droneAttitude = it.droneAttitude
            val gimbalAttitude = it.gimbalAttitude
            binding.tvDroneAttitude.text = getString(
                R.string.current_drone_attitude,
                droneAttitude?.getPitchDegree(),
                droneAttitude?.getRollDegree(),
                droneAttitude?.getYawDegree()
            )
            binding.tvGimbalAttitude.text = getString(
                R.string.current_gimbal_attitude,
                gimbalAttitude?.getPitchDegree(),
                gimbalAttitude?.getRollDegree(),
                gimbalAttitude?.getYawDegree()
            )
            binding.tvDronePosition.text = getString(
                R.string.current_drone_position,
                it.droneLatitude,
                it.droneLongitude,
                it.altitude,
                it.distance
            )
            binding.tvDroneSpeed.text =
                getString(R.string.current_drone_speed, hSpeed, it.velocityZ)

            getDroneLFData()?.let {
                calculateWindSpeed(it.estimateWindSpeeds)
            }

            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastTime > 5000) {
                lastTime = currentTime
//                SDKLog.i(
//                    TAG,
//                    "Drone Attitude: pitch: ${droneAttitude?.getPitchDegree()}, roll: ${droneAttitude?.getRollDegree()}, yaw: ${droneAttitude?.getYawDegree()};" +
//                            " Gimbal Attitude pitch: ${gimbalAttitude?.getPitchDegree()}, roll: ${gimbalAttitude?.getRollDegree()}, yaw: ${gimbalAttitude?.getYawDegree()}"
//                )
//                SDKLog.i(
//                    TAG,
//                    "Drone Position: Latitude: ${it.droneLatitude}, Longitude: ${it.droneLongitude}, altitude: ${it.altitude}, distance: ${it.distance};, HSpeed: $hSpeed, VSpeed: ${it.velocityZ}"
//                )

                val windSpeed = getDroneLFData()?.estimateWindSpeeds ?: emptyList()
//                SDKLog.i(TAG, "Estimate wind speed: ${windSpeed.joinToString(", ")}")
            }
            updateFlightControlMode(it.mainMode)
            val builder = StringBuilder()

            it.sensorValid.forEach { sensor ->
                builder.append(sensor.name.substring("SENSOR_TYPE_".length)).append(", ")
            }
            binding.tvCurrentSensor.text = getString(
                R.string.debug_current_sensor,
                if (builder.isNotEmpty()) builder.toString() else "None"
            )
        }

        handler.sendEmptyMessageDelayed(MESSAGE_UPDATE_UI, 300)
    }

    private fun updateFlightControlMode(mainMode: FlightControlMainModeEnum) {
        when (mainMode) {
            FlightControlMainModeEnum.GPS -> {
                binding.tvPositionMode.isVisible = true
                val mode = getString(R.string.debug_text_gnss_mode)
                binding.tvPositionMode.text = getString(R.string.debug_text_control_mode, mode)
                binding.tvPositionMode.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.debug_battery_setting_safe
                    )
                )
            }

            FlightControlMainModeEnum.STARPOINT -> {
                binding.tvPositionMode.isVisible = true
                val mode = getString(R.string.debug_text_visual_positioning_mode)
                binding.tvPositionMode.text = getString(R.string.debug_text_control_mode, mode)
                binding.tvPositionMode.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.debug_battery_setting_safe
                    )
                )
            }

            FlightControlMainModeEnum.ATTITUDE -> {
                binding.tvPositionMode.isVisible = true
                val mode = getString(R.string.debug_text_attitude_mode)
                binding.tvPositionMode.text = getString(R.string.debug_text_control_mode, mode)
                binding.tvPositionMode.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.debug_battery_setting_critical
                    )
                )
                if (isInFollowMission) {
                    SDKSingleButtonDialog(requireContext()).apply {
                        setTitle(getString(R.string.debug_hint))
                        setMessage(getString(R.string.debug_text_follow_exit))
                        show()
                    }
                    isInFollowMission = false
                }
            }

            else -> {
                binding.tvPositionMode.isVisible = false
            }
        }
    }

    private fun calculateWindSpeed(estimateWindSpeeds: List<Float>) {

        val speedN = estimateWindSpeeds.getOrNull(0) ?: 0f
        val speedE = estimateWindSpeeds.getOrNull(1) ?: 0f
        //val speedD = newValue.estimateWindSpeeds.getOrNull(2) ?: 0f
        //horizontal wind speed
        val speed2D = sqrt((speedN * speedN + speedE * speedE).toDouble()).toFloat()
        //3D wind speed
        //val speed3D = sqrt((speedN * speedN + speedE * speedE + speedD * speedD).toDouble()).toFloat()
        //wind direction (angle clockwise from true north)
        val degree = Math.toDegrees(atan2(speedE.toDouble(), speedN.toDouble())) //[-180, 180]
        val windDirection = (360.0 + degree).mod(360.0).toFloat()

        binding.tvWindSpeed.text = getString(R.string.wind_speed, speed2D)
        binding.tvWindDirection.text = getString(R.string.wind_direction, windDirection)
    }

    private fun setMediaDirectory() {
        KeyValueDialogUtil.showInputDialog(
            requireActivity(),
            getString(R.string.debug_set_request_parameters),
            "Test",
            "Test",
            false
        ) { _, it ->
            keyManager?.setValue(
                keySetMediaDirName,
                it,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        SDKLog.d(TAG, "set KeyCameraSaveMapUserDirName onSuccess")
                        appendLog("set [CameraKey.KeyCameraSaveMapUserDirName]", true, showToast = true)
                    }

                    override fun onFailure(code: IAutelCode, msg: String?) {
                        SDKLog.e(TAG, "set KeyCameraSaveMapUserDirName onFailure($msg, $code)")
                        appendLog("set [CameraKey.KeyCameraSaveMapUserDirName]: $code $msg", false, showToast = true)
                    }
                })
        }
    }

    private fun initJoystickSetting() {
        val joystickAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_selected,
            resources.getStringArray(R.array.joystick_priorities)
        )
        joystickAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spJoystickPriority.adapter = joystickAdapter

        keyManager?.getValue(
            KeyTools.createKey(MissionManagerKey.KeySdkCtrlLevel),
            object : CommonCallbacks.CompletionCallbackWithParam<Int> {
                override fun onSuccess(t: Int?) {
                    activity?.runOnUiThread {
                        binding.spJoystickPriority.setSelection(t ?: 0)
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "get KeyJoystickPriority onFailure($msg, $error)")
                    appendLog("get [MissionManagerKey.KeySdkCtrlLevel]: $error $msg", false)
                }
            })

        // Only trigger setValue after the user actually operates the Spinner; skip setAdapter / setSelection
        // auto-dispatched init callbacks; prevents the main thread from synchronously entering MmSetService.set → ParamInfo.<clinit>
        // → System.loadLibrary and concurrently loading typesupport with the ROS2 spin thread, causing a deadlock
        // and an ANR right after opening the page (see anr_2026-05-18-20-09-21-942)
        var userPickedJoystick = false
        binding.spJoystickPriority.setOnTouchListener { v, _ ->
            userPickedJoystick = true
            v.performClick()
            false
        }
        binding.spJoystickPriority.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!userPickedJoystick) return
                keyManager?.setValue(
                    KeyTools.createKey(MissionManagerKey.KeySdkCtrlLevel),
                    position,
                    object : CommonCallbacks.CompletionCallback {
                        override fun onSuccess() {
                            SDKLog.i(TAG, "set KeyJoystickPriority success")
                            appendLog("set [MissionManagerKey.KeySdkCtrlLevel]: $position", true, showToast = true)
                        }

                        override fun onFailure(code: IAutelCode, msg: String?) {
                            SDKLog.e(TAG, "set KeyJoystickPriority onFailure($msg, $code)")
                            appendLog("set [MissionManagerKey.KeySdkCtrlLevel]: $code $msg", false, showToast = true)
                        }
                    })
            }
        }
    }

    private fun setDronePosition() {
        val bean = UAVPositionControlMessageBean(
            coordinateType = CoordinateType.COORDINATE_TYPE_BODY,
            posValid = true,
            positons = listOf(1f, 1f, 1f),
            maxSpeed = 5f,
            yawValid = true,
            yaw = 1.57f,
            maxYawRate = 0.5f,
            gimbalValid = true,
            gimbalPos = listOf(0.1f, -0.5f, 0.1f),
            maxGimbalRate = 0.5f,
        )
        val dialog = UAVControlDialog(requireActivity(), 0, gson.toJson(bean))
        dialog.onDataEntered = { json ->
            val message = try {
                gson.fromJson(json, UAVPositionControlMessageBean::class.java)
            } catch (e: Exception) {
                SDKLog.printSafeStackTrace(e)
                null
            } ?: bean
            if (sequence > 1000000) sequence = 1
            message.sequence = sequence++

            SDKLog.i(TAG, "setDronePosition $message")
            appendLog("performAction [FlightMissionKey.KeyUAVPositionControl]: message=${message}", true)
            keyManager?.performAction(
                keyUAVPositionControl,
                message,
                object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                    override fun onSuccess(t: Void?) {
                        SDKLog.i(TAG, "setDronePosition onSuccess")
                        appendLog("performAction [FlightMissionKey.KeyUAVPositionControl]: success", true, showToast = true)
                    }

                    override fun onFailure(error: IAutelCode, msg: String?) {
                        SDKLog.e(TAG, "setDronePosition onFailure($msg, $error)")
                        appendLog("performAction [FlightMissionKey.KeyUAVPositionControl]: $error $msg", false, showToast = true)
                    }
                })
        }
        dialog.show()
    }

    private fun setDroneVelocity() {
        val bean = UAVVelocityControlMessageBean(
            coordinateType = CoordinateType.COORDINATE_TYPE_BODY,
            velocityValid = true,
            velocity = listOf(1f, 1f, 1f),
            yawRateValid = true,
            yawRate = 0.5f,
            gimbalRateValid = true,
            gimbalRate = listOf(0.1f, -0.3f, 0.1f),
        )
        val dialog = UAVControlDialog(requireActivity(), 1, gson.toJson(bean))
        dialog.onDataEntered = { json ->
            val message = try {
                gson.fromJson(json, UAVVelocityControlMessageBean::class.java)
            } catch (e: Exception) {
                SDKLog.printSafeStackTrace(e)
                null
            } ?: bean
            if (sequence > 1000000) sequence = 1
            message.sequence = sequence++

            SDKLog.i(TAG, "setDroneVelocity $message")
            appendLog("setFrequencyReport [FlightMissionKey.KeyUAVVelocityControl]: seq=${message.sequence}, vel=${message.velocity}, yawRate=${message.yawRate}", true)
            keyManager?.setFrequencyReport(keyVelocityControl, message)
        }
        dialog.show()
    }

    private fun setDroneImageControl() {

        val bean = UAVImageControlMessageBean()
        val dialog = UAVControlDialog(requireActivity(), 2, gson.toJson(bean))
        dialog.onDataEntered = { json ->
            val message = try {
                gson.fromJson(json, UAVImageControlMessageBean::class.java)
            } catch (e: Exception) {
                SDKLog.printSafeStackTrace(e)
                null
            } ?: bean

            if (sequence > 1000000) sequence = 1
            message.sequence = sequence++
            SDKLog.i(TAG, "setDroneImageControl $message")
            keyManager?.setFrequencyReport(keyUAVImageControl, message)
        }
        dialog.show()
    }


    private fun enterNightSlam() {
        //this is how the North America custom version behaves
        val location = LocationCoordinate3D(0.0, 0.0, 0.0)
        SDKLog.i(TAG, "enterNightSlam $location")
        val key = KeyTools.createKey(FlightMissionKey.KeyEnterFastMission)
        keyManager?.performAction(
            key,
            null,
            object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                override fun onSuccess(t: Void?) {
                    SDKLog.i(TAG, "KeyEnterFastMission success")
                    appendLog("performAction [FlightMissionKey.KeyEnterFastMission]", true, showToast = true)
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "KeyEnterFastMission onFailure($msg, $error)")
                    appendLog(
                        "performAction [FlightMissionKey.KeyEnterFastMission]: $error $msg",
                        false,
                        showToast = true
                    )
                }
            })
    }

    private fun exitNightSlam() {
        keyManager?.performAction(
            KeyTools.createKey(FlightMissionKey.KeyExitFastMission),
            null,
            object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                override fun onSuccess(t: Void?) {
                    SDKLog.i(TAG, "exitNightSlam onSuccess")
                    appendLog("performAction [FlightMissionKey.KeyExitFastMission]", true, showToast = true)
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "exitNightSlam onFailure($msg, $error)")
                    appendLog(
                        "performAction [FlightMissionKey.KeyExitFastMission]: $error $msg",
                        false,
                        showToast = true
                    )
                }
            })
    }

    private fun enterFollowMission() {
        keyManager?.performAction(
            KeyTools.createKey(FlightMissionKey.KeyEnterFollowRCMission), null,
            object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                override fun onSuccess(t: Void?) {
                    SDKLog.i(TAG, "enterFollowMission onSuccess")
                    appendLog("performAction [FlightMissionKey.KeyEnterFollowRCMission]", true, showToast = true)
                    isInFollowMission = true
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "enterFollowMission onFailure($msg, $error)")
                    appendLog(
                        "performAction [FlightMissionKey.KeyEnterFollowRCMission]: $error $msg",
                        false,
                        showToast = true
                    )
                }
            })
    }

    private fun exitFollowMission() {
        isInFollowMission = false
        keyManager?.performAction(
            KeyTools.createKey(FlightMissionKey.KeyExitFollowRCMission), null,
            object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                override fun onSuccess(t: Void?) {
                    SDKLog.i(TAG, "enterFollowMission onSuccess")
                    appendLog("performAction [FlightMissionKey.KeyExitFollowRCMission]", true, showToast = true)
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "enterFollowMission onFailure($msg, $error)")
                    appendLog(
                        "performAction [FlightMissionKey.KeyExitFollowRCMission]: $error $msg",
                        false,
                        showToast = true
                    )
                }
            })
    }

    private fun initFollowHeightSetting() {
        keyManager?.getValue(
            keyFollowHeight,
            object : CommonCallbacks.CompletionCallbackWithParam<Int> {
                @SuppressLint("DefaultLocale")
                override fun onSuccess(t: Int?) {
                    SDKLog.i(TAG, "get follow height onSuccess($t)")
                    appendLog("get [FlightMissionKey.KeyFollowRCHeight]: $t", true)
                    activity?.runOnUiThread {
                        binding.inputFollowHeight.setText(String.format("%d", t ?: 0f))
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "get follow height onFailure($msg, $error)")
                    appendLog("get [FlightMissionKey.KeyFollowRCHeight]: $error $msg", false)
                    activity?.runOnUiThread {
                        binding.inputFollowHeight.setText("100")
                    }
                }
            }
        )

        binding.btnFollowHeight.setOnClickListener {
            val height = binding.inputFollowHeight.text?.toString()?.toIntOrNull() ?: 0
            SDKLog.i(TAG, "set follow height $height")
            if (height < 10 || height > 800) {
                showToast(getString(R.string.follow_height_exceed))
                return@setOnClickListener
            }

            keyManager?.setValue(
                keyFollowHeight,
                height,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        SDKLog.i(TAG, "set follow height success($height)")
                        appendLog("set [FlightMissionKey.KeyFollowRCHeight]: $height", true, showToast = true)
                    }

                    override fun onFailure(code: IAutelCode, msg: String?) {
                        SDKLog.e(TAG, "set follow height onFailure($msg, $code)")
                        appendLog("set [FlightMissionKey.KeyFollowRCHeight]: $code $msg", false, showToast = true)
                    }
                })
        }
    }
}