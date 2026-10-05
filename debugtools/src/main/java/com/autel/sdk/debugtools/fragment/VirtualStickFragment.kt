package com.autel.sdk.debugtools.fragment


import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.isVisible
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.NestModelManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.converter.SingleValueConverter
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightControlKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightMissionKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightPropertyKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RemoteControllerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.bean.HardwareButtonInfoBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.enums.RCButtonTypeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.enums.RcButtonStateEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKeyInfo
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.alink.enums.VideoTransMissionModeEnum
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateHFNtfyBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.enums.GearLevelEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneAutoBackBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.enums.AutoBackEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.VirtualControlMessageBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.enums.RcOperateModeEnum
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.sdk.debugtools.helper.PlayerWeightHelper.applyFullScreenWeight
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentVirtualStickBinding
import com.autel.sdk.debugtools.listener.OnItemSelectedListener
import com.autel.sdk.debugtools.listener.OnSeekBarChangeListener
import com.autel.sdk.debugtools.view.virtualstick.JoystickView
import com.autel.sdk.debugtools.view.virtualstick.OnJoystickListener
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Virtual stick sample
 */
class VirtualStickFragment : AutelFragment(), IAutelDroneListener {

    private var autelPlayer: AutelPlayer? = null
    private lateinit var autelPlayerView: AutelPlayerView

    /** Device ID (for the on-demand streaming API) */
    private var deviceId: String? = null

    /** Lens ID map <LensTypeEnum, lensId> */
    private var lensIdMap: Map<LensTypeEnum, Int> = emptyMap()

    /** Currently playing lensId (to decide whether switching is needed) */
    private var mPlayingLensId: Int? = null

    private val deviation: Double = 0.02
    private var currentLevel: GearLevelEnum = GearLevelEnum.UNKNOWN
    private var stickMode = RcOperateModeEnum.AMERICA_HAND
    private var binding: FragmentVirtualStickBinding? = null

    /** Currently selected lens; set dynamically at runtime from available lenses */
    private var lensType: LensTypeEnum? = null

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            1 -> {
                updateLocationInfo()
            }
        }
        return true
    }

    /**
     * Comfort: full stick (horizontal) fwd/back 10, left/right 10; (vertical) up 5, down 4. Yaw 90°
     * Standard: full stick (horizontal) fwd/back 15, left/right 10; (vertical) up 6, down 6. Yaw 90°
     * Sport: full stick (horizontal) fwd 23 / back 18, left/right 20; (vertical) up 6, down 6. Yaw 120°
     * Slow: full stick (horizontal) fwd/back 3, left/right 3; (vertical) up 3, down 3. Yaw 90°
     */
    private val speedLevel = arrayOf(
        arrayOf(10, 10, 10, 10, 5, 4, 90),
        arrayOf(15, 15, 10, 10, 6, 6, 90),
        arrayOf(23, 18, 20, 20, 6, 6, 120),
        arrayOf(3, 3, 3, 3, 3, 3, 90)
    )

    private val stickValue = arrayOf(0f, 0f, 0f, 0f)

    private val joystickUpdate: MutableStateFlow<Long> = MutableStateFlow(0)

    private var fullLevelInfo: Array<String>? = null

    private val scope: CoroutineScope by lazy { CoroutineScope(Dispatchers.IO) }
    private val ioDispatcher = Dispatchers.IO + SupervisorJob()

    private var sequence: Int = 1
    private var keyManager: IKeyManager? = null
    private val keyVirtualRCControl = KeyTools.createKey(FlightMissionKey.KeyUAVVirtualRCControl)
    companion object {
        private const val TAG = "VirtualStickFragment"

        /** Virtual stick command interval (ms); must keep ≥10Hz to satisfy the FC failsafe threshold */
        private const val STICK_SEND_INTERVAL_MS = 50L

        /** Frames of zero-stick commands sent after release so the FC receives a clear stop command */
        private const val STICK_ZERO_FRAME_TAIL = 5
    }

    private var isNewVersion = true
    private val defaultTakeoffHeightMm = 1200

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentVirtualStickBinding.inflate(inflater)
        isNewVersion = arguments?.getBoolean("isNewVersion",true) ?: true
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceManager.getDeviceManager().addDroneListener(this)
        initView()
    }

    private fun initView() {
        SDKLog.i(TAG, "initView...")
//        setFloatValue(FightParamKey.SIM_LON, -122.3321f) //113.99777f
//        setFloatValue(FightParamKey.SIM_LAT, 47.6062f) //22.595598f
        initVideo()

        fullLevelInfo = resources.getStringArray(R.array.debug_stick_level_speed)

        getGearLevel()
        getStickMode()

        binding?.btnStartMotor?.setOnClickListener {
            performStartMotor()
        }

        binding?.layoutGimbal?.isVisible = isNewVersion
        binding?.layoutButtons?.isVisible = isNewVersion
        if (isNewVersion) {
            binding?.levelFullInfo?.visibility = View.INVISIBLE
            binding?.tvGearLevel?.visibility = View.INVISIBLE
            binding?.spinner?.visibility = View.INVISIBLE

            binding?.sbGimbalPitch?.progress = 100
            binding?.sbGimbalPitch?.setOnSeekBarChangeListener(object: OnSeekBarChangeListener() {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        if (sequence > 1000000) sequence = 1 else sequence++
                        val bean = VirtualControlMessageBean(
                            timestamp = System.currentTimeMillis(),
                            sequence = sequence,
                            gimbalPitch = progress - 100
                        )

                        SDKLog.d(TAG, "sendVirtualJoystickData2: $bean")
                        keyManager =  keyManager ?: DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
                        keyManager?.setFrequencyReport(keyVirtualRCControl, bean)
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress = 100
                }
            })
            
            initFlightControls()
        }

        //joystick
        binding?.leftStickView?.setJoystickListener(object : OnJoystickListener {
            override fun onTouch(joystick: JoystickView?, pX: Float, pY: Float) {
                stickValue[0] = 0F
                stickValue[1] = 0F
                if (abs(pX) >= deviation) {
                    stickValue[0] = pX
                }
                if (abs(pY) >= deviation) {
                    stickValue[1] = pY
                }
                joystickUpdate.tryEmit(System.currentTimeMillis())
                SDKLog.d(TAG, "leftStickView: ${stickValue[0]}, ${stickValue[1]}")
            }
        })

        binding?.rightStickView?.setJoystickListener(object : OnJoystickListener {
            override fun onTouch(joystick: JoystickView?, pX: Float, pY: Float) {
                stickValue[2] = 0F
                stickValue[3] = 0F
                if (abs(pX) >= deviation) {
                    stickValue[2] = pX
                }
                if (abs(pY) >= deviation) {
                    stickValue[3] = pY
                }
                joystickUpdate.tryEmit(System.currentTimeMillis())
                SDKLog.i(TAG, "rightStickView: ${stickValue[2]}, ${stickValue[3]}")
            }
        })

        // Send virtual stick commands at a fixed rate (50ms / 20Hz) so that holding the stick still
        // (no more MotionEvents) does not starve the stream and trip FC failsafe
        scope.launch(ioDispatcher + CoroutineExceptionHandler { _, e ->
            SDKLog.printSafeStackTrace(e)
        }) {
            var zeroFrameTail = 0
            while (isActive) {
                val active = stickValue.any { it != 0f }
                if (active) {
                    zeroFrameTail = STICK_ZERO_FRAME_TAIL
                    if (isNewVersion) {
                        sendVirtualJoystickData2(stickValue[0], stickValue[1], stickValue[2], stickValue[3])
                    } else {
                        sendVirtualJoystickData(stickValue[0], stickValue[1], stickValue[2], stickValue[3])
                    }
                } else if (zeroFrameTail > 0) {
                    // Send a few extra zero frames after release so the FC gets a clear stop command
                    zeroFrameTail--
                    if (isNewVersion) {
                        sendVirtualJoystickData2(0f, 0f, 0f, 0f)
                    } else {
                        sendVirtualJoystickData(0f, 0f, 0f, 0f)
                    }
                }
                delay(STICK_SEND_INTERVAL_MS)
            }
        }
        handler.sendEmptyMessageDelayed(1, 2000L)
    }

    /**
     * Initialize flight control buttons (takeoff / RTH) + physical key listener
     */
    private fun initFlightControls() {
        binding?.btnTakeoff?.setOnClickListener { performTakeoff() }
        binding?.btnReturnHome?.setOnClickListener { performReturnHome() }
        listenPhysicalGoHomeButton()
    }

    private val goHomeButtonListener = object : CommonCallbacks.KeyListener<HardwareButtonInfoBean> {
        override fun onValueChange(oldValue: HardwareButtonInfoBean?, newValue: HardwareButtonInfoBean) {
            if (newValue.buttonType == RCButtonTypeEnum.GO_HOME && newValue.clickType == RcButtonStateEnum.LONG_PRESS) {
                SDKLog.i(TAG, "Physical GO_HOME long press detected")
                val device = DeviceManager.getDeviceManager().getFirstDroneDevice() ?: return
                val heartbeat = device.getKeyManager().getCacheValue(KeyTools.createKey(CommonKey.KeyDroneSystemStatusHFNtfy))
                val isFlying = (heartbeat?.altitude ?: 0f) > 0.5f
                SDKLog.i(TAG, "Physical GO_HOME: isFlying=$isFlying, altitude=${heartbeat?.altitude}")
                if (isFlying) {
                    performReturnHome()
                } else {
                    performTakeoff()
                }
            }
        }
    }

    private fun listenPhysicalGoHomeButton() {
        val remoteKeyManager = DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
        remoteKeyManager.listen(
            KeyTools.createKey(RemoteControllerKey.KeyRCHardwareInfo),
            this,
            goHomeButtonListener
        )
    }

    private fun removePhysicalGoHomeListener() {
        try {
            val remoteKeyManager = DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
            remoteKeyManager.cancelListen(this)
        } catch (e: Exception) {
            SDKLog.e(TAG, "removePhysicalGoHomeListener error: ${e.message}")
        }
    }

    private fun performTakeoff() {
        val km = keyManager ?: DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        if (km == null) {
            showToast(getString(R.string.debug_vs_drone_not_connected))
            return
        }
        keyManager = km

        val key = KeyTools.createKey(FlightControlKey.KeyTakeOffAirCraftWithAttitude)
        SDKLog.i(TAG, "performTakeoff: height=${defaultTakeoffHeightMm}mm")
        km.performAction(key, defaultTakeoffHeightMm, object : CommonCallbacks.CompletionCallbackWithParam<Void> {
            override fun onSuccess(t: Void?) {
                SDKLog.i(TAG, "performTakeoff onSuccess")
                showToast(getString(R.string.debug_vs_takeoff_success))
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                SDKLog.e(TAG, "performTakeoff onFailure: $error $msg")
                showToast(getString(R.string.debug_vs_takeoff_fail, "$error $msg"))
            }
        })
    }

    private fun performReturnHome() {
        val km = keyManager ?: DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        if (km == null) {
            showToast(getString(R.string.debug_vs_drone_not_connected))
            return
        }
        keyManager = km

        val key = KeyTools.createKey(FlightControlKey.KeyStartStopAutoBack)
        SDKLog.i(TAG, "performReturnHome")
        km.performAction(key, DroneAutoBackBean(true, AutoBackEnum.HOMEPOINT),
            object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                override fun onSuccess(t: Void?) {
                    SDKLog.i(TAG, "performReturnHome onSuccess")
                    showToast(getString(R.string.debug_vs_return_home_success))
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "performReturnHome onFailure: $error $msg")
                    showToast(getString(R.string.debug_vs_return_home_fail, "$error $msg"))
                }
            })
    }

    
    private fun remarkStickMode() {
        // Check the Fragment is still attached to a Context
        if (!isAdded || context == null) {
            return
        }

        val leftStick = getString(R.string.debug_text_controller_model_left_remote)
        val rightStick = getString(R.string.debug_text_controller_model_right_remote)
        val ascend = getString(R.string.debug_text_controller_model_up_title)
        val descend = getString(R.string.debug_text_controller_model_down_title)
        val turnRight = getString(R.string.debug_text_controller_model_turn_right_title)
        val turnLeft = getString(R.string.debug_text_controller_model_turn_left_title)
        val forward = getString(R.string.debug_text_controller_model_forward_title)
        val backward = getString(R.string.debug_text_controller_model_back_title)
        val goRight = getString(R.string.debug_text_controller_model_right_title)
        val goLeft = getString(R.string.debug_text_controller_model_left_title)

        val remark = when(stickMode) {
            RcOperateModeEnum.AMERICA_HAND -> {
                "$leftStick X: $turnLeft/$turnRight ; Y: $ascend/$descend; $rightStick X: $goLeft/$goRight Y: $forward/$backward"
            }
            RcOperateModeEnum.JAPANESE_HAND -> {
                "$leftStick X: $turnLeft/$turnRight ; Y: $forward/$backward; $rightStick X: $goLeft/$goRight Y: $ascend/$descend"
            }
            RcOperateModeEnum.CHINESE_HAND -> {
                "$leftStick X: $goLeft/$goRight ; Y: $forward/$backward; $rightStick X: $turnLeft/$turnRight Y: $ascend/$descend"
            }
        }
        binding?.tvStickModeRemark?.text = remark
    }

    private fun getGearLevel() {
        keyManager =  keyManager ?: DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        keyManager?.getValue(
            KeyTools.createKey(FlightPropertyKey.KeyGearLever),
            object: CommonCallbacks.CompletionCallbackWithParam<GearLevelEnum> {
                override fun onSuccess(t: GearLevelEnum?) {
                    SDKLog.i(TAG, "get KeyGearLever onSuccess: $t")
                    currentLevel = if (t == null || t == GearLevelEnum.UNKNOWN) GearLevelEnum.NORMAL else t
                    handler.post { 
                        if (isAdded && context != null) {
                            initGearLevel() 
                        }
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.i(TAG, "get KeyGearLever onFailure: $error $msg")
                    currentLevel = GearLevelEnum.NORMAL
                    handler.post { 
                        if (isAdded && context != null) {
                            initGearLevel() 
                        }
                    }
                }
            }
        )
    }

    private fun getStickMode() {
        val remoteKeyManager = DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
        remoteKeyManager.getValue(
            KeyTools.createKey(RemoteControllerKey.KeyRCRockerControlMode),
            object: CommonCallbacks.CompletionCallbackWithParam<RcOperateModeEnum> {
                override fun onSuccess(t: RcOperateModeEnum?) {
                    SDKLog.i(TAG, "get KeyRCRockerControlMode onSuccess: $t")
                    stickMode = t ?: RcOperateModeEnum.AMERICA_HAND
                    handler.post { 
                        if (isAdded && context != null) {
                            initStickMode() 
                        }
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.i(TAG, "get KeyRCRockerControlMode onFailure: $error $msg")
                    stickMode = RcOperateModeEnum.AMERICA_HAND
                    handler.post { 
                        if (isAdded && context != null) {
                            initStickMode() 
                        }
                    }
                }
            }
        )
    }

    private fun initGearLevel() {
        // Check the Fragment is still attached to a Context
        if (!isAdded || context == null) {
            return
        }
        
        binding?.spinner?.setSelection(currentLevel.value - 1)
        binding?.levelFullInfo?.text = fullLevelInfo?.get(currentLevel.value - 1) ?: ""
        binding?.spinner?.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isAdded && context != null) {
                    if (position != currentLevel.value - 1) {
                        setGearLevel(position)
                    }
                }
            }
        }
    }

    private fun initStickMode() {
        // Check the Fragment is still attached to a Context
        if (!isAdded || context == null) {
            return
        }
        
        remarkStickMode()
        binding?.spinnerMode?.setSelection(stickMode.value)
        binding?.spinnerMode?.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isAdded && context != null) {
                    SDKLog.i(TAG, "onItemSelected: $position  $stickMode")
                    if (stickMode.value != position) {
                        setStickMode(position)
                    }
                }
            }
        }
    }

    private fun setStickMode(position: Int) {
        val value =  RcOperateModeEnum.values()[position]
        val remoteKeyManager = DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
        remoteKeyManager.setValue(
            KeyTools.createKey(RemoteControllerKey.KeyRCRockerControlMode),
            value,
            object: CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    stickMode = value
                    handler.post { 
                        if (isAdded && context != null) {
                            remarkStickMode() 
                        }
                    }
                    SDKLog.i(TAG, "set KeyRCRockerControlMode onSuccess: $stickMode")
                }

                override fun onFailure(code: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "set KeyRCRockerControlMode onFailure: $code $msg")
                    handler.post {
                        if (isAdded && context != null) {
                            binding?.spinnerMode?.setSelection(stickMode.value)
                            try {
                                Toast.makeText(context ?: throw IllegalStateException("Fragment not attached to context"), "onFailure: $code $msg", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                SDKLog.e(TAG, "Failed to show toast: ${e.message}")
                            }
                        }
                    }
                }
            }
        )
    }

    private fun setGearLevel(position: Int) {
        val level = when(position) {
            3 -> GearLevelEnum.LOW_SPEED
            0 -> GearLevelEnum.SMOOTH
            1 -> GearLevelEnum.NORMAL
            2 -> GearLevelEnum.SPORT
            else -> GearLevelEnum.UNKNOWN
        }
        if (level == GearLevelEnum.UNKNOWN) return

        keyManager =  keyManager ?: DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        keyManager?.setValue(
            KeyTools.createKey(FlightPropertyKey.KeyGearLever),
            level,
            object: CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    SDKLog.i(TAG, "set KeyGearLever onSuccess: $level")
                    currentLevel = level
                    handler.post {
                        if (isAdded && context != null) {
                            binding?.levelFullInfo?.text = fullLevelInfo?.get(currentLevel.value - 1) ?: ""
                        }
                    }
                }

                override fun onFailure(code: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "set KeyGearLever onFailure: $code $msg")
                    handler.post {
                        if (isAdded && context != null) {
                            binding?.spinner?.setSelection(currentLevel.value - 1)
                            try {
                                Toast.makeText(context ?: throw IllegalStateException("Fragment not attached to context"), "onFailure: $code $msg", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                SDKLog.e(TAG, "Failed to show toast: ${e.message}")
                            }
                        }
                    }
                }
        })
    }


    @SuppressLint("DefaultLocale")
    private fun sendVirtualJoystickData2(leftX: Float, leftY: Float, rightX: Float, rightY: Float) {

        // left/right yaw, up/down, left/right, fwd/back
        val values = dealWithControlMode(leftX, leftY, rightX, rightY)
        if (sequence > 1000000) sequence = 1 else sequence++
        val bean = VirtualControlMessageBean(
            timestamp = System.currentTimeMillis(),
            sequence = sequence,
            turnYaw = (values[0] * 100f).toInt(),
            forwardOrBackward = (values[3] * 100f).toInt(),
            raiseOrFall = (values[1] * 100f).toInt(),
            leftOrRight = (values[2] * 100f).toInt(),
            gimbalPitch = 0
        )

        handler.post {
            if (isAdded && context != null) {
                binding?.dataSend?.text = String.format("upOrDown: %d\nturnLeftOrRight: %d\nforwardOrBackward: %d\ngoLeftOrRight: %d",
                    bean.raiseOrFall, bean.turnYaw, bean.forwardOrBackward, bean.leftOrRight)
            }
        }

        SDKLog.i(TAG, "sendVirtualJoystickData2: $bean")
        keyManager =  keyManager ?: DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        keyManager?.setFrequencyReport(keyVirtualRCControl, bean)
    }

    @SuppressLint("DefaultLocale")
    private fun sendVirtualJoystickData(leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
        if (currentLevel == GearLevelEnum.UNKNOWN) return
        // left/right yaw, up/down, left/right, fwd/back
        val index = currentLevel.value - 1
        val values = dealWithControlMode(leftX, leftY, rightX, rightY)
        val raiseOrDownValue = if (values[1] > 0) values[1] * speedLevel[index][4] else values[1] * speedLevel[index][5]
        val turnYawValue = values[0] * speedLevel[index][6]
        val forwardOrBackwardValue = if (values[3] > 0) values[3] * speedLevel[index][0] else values[3] * speedLevel[index][1]
        val leftOrRightValue = if (values[2] > 0) values[2] * speedLevel[index][2] else values[2] * speedLevel[index][3]

        SDKLog.i(TAG, "sendVirtualJoystickData()~")

        handler.post {
            if (isAdded && context != null) {
                binding?.dataSend?.text = String.format("upOrDown: %.4f\nturnLeftOrRight: %.2f\nforwardOrBackward: %.4f\ngoLeftOrRight: %.4f",
                    raiseOrDownValue, turnYawValue, forwardOrBackwardValue, leftOrRightValue)
            }
        }

        //TODO nest
        /*NestModelManager.getInstance().updateVirtualJoystickByAtService(
            raiseOrDownValue.toInt(),
            turnYawValue.toInt(),
            forwardOrBackwardValue.toInt(),
            leftOrRightValue.toInt(),
            0
        )*/
    }

    /**
     * Handle by control mode: left/right (yaw), up/down (pitch), left/right (roll), fwd/back (thrust)
     */
    private fun dealWithControlMode(leftX: Float, leftY: Float, rightX: Float, rightY: Float): List<Float> {
        return when(stickMode) {
            RcOperateModeEnum.AMERICA_HAND -> listOf(leftX, leftY, rightX, rightY)
            RcOperateModeEnum.CHINESE_HAND -> listOf(rightX, rightY, leftX, leftY)
            RcOperateModeEnum.JAPANESE_HAND -> listOf(leftX, rightY, rightX, leftY)
        }
    }

    val keyHfDroneData = KeyTools.createKey(CommonKey.KeyDroneSystemStatusHFNtfy)
    private val keyHomeInfo = KeyTools.createKey(CommonKey.KeyHomeInfoReport)
    private val keyHeartbeat = KeyTools.createKey(CommonKey.KeyMissionHeartbeatStatusReport)

    private fun getDroneStateData(): DroneSystemStateHFNtfyBean? {
        return DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()?.getCacheValue(keyHfDroneData)
    }

    private fun performStartMotor() {
        val km = keyManager ?: DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        if (km == null) {
            showStickToast("Drone not connected")
            return
        }
        keyManager = km
        getArmBlockReason(km)?.let { reason ->
            SDKLog.w(TAG, "start motor blocked: $reason")
            showStickToast(reason)
            return
        }
        km.performAction(
            KeyTools.createKey(FlightControlKey.KeyStartStopMotor),
            true,
            object : CommonCallbacks.CompletionCallbackWithParam<Void> {
                override fun onSuccess(t: Void?) {
                    SDKLog.i(TAG, "start motor success")
                    showStickToast("Motor start command sent")
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "start motor fail: $error $msg")
                    showStickToast("Motor start failed: ${error.code} ${msg.orEmpty()}")
                }
            })
    }

    private fun getArmBlockReason(keyManager: IKeyManager): String? {
        val heartbeat = keyManager.getCacheValue(keyHeartbeat) ?: return null
        if (!heartbeat.isDroneActivated) {
            return "Drone not activated. Activate it before starting motors"
        }
        if (heartbeat.notAllowedArmReasons.isNotEmpty()) {
            val reasons = heartbeat.notAllowedArmReasons.joinToString(", ") { it.name }
            return "Motor start forbidden: $reasons"
        }
        return null
    }

    private fun showStickToast(message: String) {
        if (!isAdded || context == null) return
        try {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            SDKLog.e(TAG, "Failed to show toast: ${e.message}")
        }
    }

    private fun formatPositionValue(value: Double, gpsValid: Boolean): String {
        if (!gpsValid || value == 0.0) return "N/A (GPS invalid)"
        return String.format("%.08f", value)
    }

    private fun formatMetricValue(value: Float, gpsValid: Boolean): String {
        if (!gpsValid) return "N/A"
        return String.format("%.02f", value)
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun updateLocationInfo() {
        // Check the Fragment is still attached to a Context
        if (!isAdded || context == null) {
            return
        }
        
        val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
        if (device == null) {
                    handler.sendEmptyMessageDelayed(1, 5000L)
            return
        }

        val stateData = getDroneStateData()
        val flightData = device.getDeviceStateData().flightControlData
        val homeInfo = device.getKeyManager()?.getCacheValue(keyHomeInfo)
        val heartbeat = device.getKeyManager()?.getCacheValue(keyHeartbeat)
        val gpsValid = homeInfo?.isLatLonValid == true

        val altitude = if (gpsValid) (stateData?.altitude ?: flightData.altitude) else 0f
        val distance = if (gpsValid) (stateData?.distance ?: flightData.distance) else 0f
        val xSpeed = stateData?.velocityX ?: flightData.velocityX
        val ySpeed = stateData?.velocityY ?: flightData.velocityY
        val zSpeed = stateData?.velocityZ ?: flightData.velocityZ
        val speed = sqrt((xSpeed * xSpeed + ySpeed * ySpeed)) * 10 / 10f

        val droneAttitude = stateData?.droneAttitude
        val dronePitch = droneAttitude?.getPitchDegree() ?: flightData.droneAttitudePitch
        val droneRoll = droneAttitude?.getRollDegree() ?: flightData.droneAttitudeRoll
        val droneYam = droneAttitude?.getYawDegree() ?: flightData.droneAttitudeYaw

        val gimbalAttitude = stateData?.gimbalAttitude
        val gimbalPitch = gimbalAttitude?.getPitchDegree() ?: flightData.gimbalAttitudePitch
        val gimbalRoll = gimbalAttitude?.getRollDegree() ?: flightData.gimbalAttitudeRoll
        val gimbalYaw = gimbalAttitude?.getYawDegree() ?: flightData.gimbalAttitudeYaw

        val latitude = stateData?.droneLatitude ?: flightData.droneLatitude.toDouble()
        val longitude = stateData?.droneLongitude ?: flightData.droneLongitude.toDouble()

        val latText = formatPositionValue(latitude, gpsValid)
        val lonText = formatPositionValue(longitude, gpsValid)
        val altText = formatMetricValue(altitude, gpsValid)
        val distText = formatMetricValue(distance, gpsValid)

        val armStatus = buildString {
            append("GPS: ").append(if (gpsValid) "valid" else "invalid")
            append(" | Activated: ").append(if (heartbeat?.isDroneActivated == true) "yes" else "no")
            if (!heartbeat?.notAllowedArmReasons.isNullOrEmpty()) {
                append(" | Motors forbidden: ")
                append(heartbeat?.notAllowedArmReasons?.joinToString(",") { it.name })
            }
        }

        val info = String.format(
            "%s\nlatitude: %s, longitude: %s, altitude: %s\n" +
                "distance: %s, hSpeed: %.02f, vSpeed: %.02f\n" +
                "dronePitch: %.04f, droneRoll: %.04f, droneYam: %.04f\n" +
                "gimbalPitch: %.02f, gimbalRoll: %.02f, gimbalYaw: %.02f",
            armStatus, latText, lonText, altText, distText, speed, zSpeed,
            dronePitch, droneRoll, droneYam, gimbalPitch, gimbalRoll, gimbalYaw
        )

        binding?.locationInfo?.text = info
        handler.sendEmptyMessageDelayed(1, 1000)
    }
    private fun setFloatValue(keyName: String, value: Float) {
        val keyInfo: AutelKeyInfo<Float> = AutelKeyInfo(0, keyName, SingleValueConverter.FloatConverter).canGet(true).canSet(true)
        val autelKey = KeyTools.createKey(keyInfo)
        DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()?.setValue(autelKey,
            value, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                }
                override fun onFailure(code: IAutelCode, msg: String?) {
                    if (isAdded && context != null) {
                        try {
                            ToastUtils.showToast("${code.code}$msg")
                        } catch (e: Exception) {
                            SDKLog.e(TAG, "Failed to show toast: ${e.message}")
                        }
                    }
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        removePhysicalGoHomeListener()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()

        // releasePlayer() in mediasdk 2.5.2 is a no-op; call stopPlayer first to stop native streaming,
        // then tear down the render surface; otherwise main_loop writes to freed memory and SIGSEGVs.
        autelPlayer?.let { player ->
            player.stopPlayer()
            player.removeVideoView()
            player.releasePlayer()
        }
        autelPlayer = null
        mPlayingLensId = null
        deviceId = null
        lensIdMap = emptyMap()
        DeviceManager.getDeviceManager().removeDroneListener(this)
        // Clear the binding reference to avoid leaks
        binding = null
    }

    private fun initVideo() {
        if (!isAdded || context == null) return

        val droneDevice = DeviceManager.getDeviceManager().getFirstDroneDevice() ?: return
        deviceId = droneDevice.deviceNumber().toString()

        droneDevice.getKeyManager().setValue(
            KeyTools.createKey(AirLinkKey.KeyALinkTransmissionMode),
            VideoTransMissionModeEnum.HIGH_QUALITY, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    SDKLog.i(TAG, "set VideoTransMissionMode HIGH_QUALITY success")
                }
                override fun onFailure(code: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "set VideoTransMissionMode onFailure: code=$code, msg=$msg")
                }
            }
        )

        // 1. Build the lens ID map
        val gimbalType = droneDevice.getGimbalDeviceType()
        val cameraManager = droneDevice.getCameraAbilitySetManger()
        val lens = cameraManager.getLensList(gimbalType)

        val idMap = mutableMapOf<LensTypeEnum, Int>()
        lens?.forEach { lensEnum ->
            val lensId = cameraManager.getLenId(lensEnum, gimbalType)
            if (lensId != null) {
                idMap[lensEnum] = lensId
                SDKLog.i(TAG, "initVideo: lens=$lensEnum, lensId=$lensId")
            }
        }
        lensIdMap = idMap

        if (lensIdMap.isEmpty()) {
            SDKLog.e(TAG, "initVideo: no available lens, skip player init")
            return
        }

        // 2. Pick the initial lens: keep the previous choice if still present, else the first item
        val initialLens = if (lensType != null && lensIdMap.containsKey(lensType)) {
            lensType!!
        } else {
            val first = lens?.firstOrNull { lensIdMap.containsKey(it) } ?: lensIdMap.keys.first()
            SDKLog.w(TAG, "initVideo: previous lens=$lensType not on aircraft, use $first")
            first
        }
        lensType = initialLens

        // 3. Set up the Spinner before streaming so its state matches playback
        val lensName = lens?.map { it.value } ?: listOf()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lensName)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding?.spinnerLens?.adapter = adapter

        // Set the listener before setSelection so selection always fires;
        // startPlayer guards with mPlayingLensId so it will not stream twice
        binding?.spinnerLens?.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isAdded || context == null) return
                val selected = lens?.getOrNull(position) ?: return
                if (lensType != selected) {
                    lensType = selected
                    startPlayer(selected)
                }
            }
        }
        val index = lens?.indexOf(initialLens)?.takeIf { it >= 0 } ?: 0
        binding?.spinnerLens?.setSelection(index)

        // 4. Create the PlayerView and start streaming immediately (not via the Spinner callback)
        autelPlayerView = createAutelCodecView()
        binding?.videoLayout?.addView(autelPlayerView)
        startPlayer(initialLens)
    }

    /**
     * Start/switch the player (on-demand streaming API)
     *
     * Follows the OnDemandStreamFragment.startSinglePlayer() pattern:
     * release old player → reuse PlayerView → create new player
     *
     * @param lens target lens type
     */
    private fun startPlayer(lens: LensTypeEnum) {
        val devId = deviceId ?: run {
            SDKLog.w(TAG, "startPlayer: deviceId is null")
            return
        }
        val lensId = lensIdMap[lens] ?: run {
            SDKLog.w(TAG, "startPlayer: no lensId for lens=$lens")
            return
        }

        // No-op if already playing this lens
        if (lensId == mPlayingLensId && autelPlayer != null) {
            SDKLog.i(TAG, "startPlayer: same lensId=$lensId, skip")
            return
        }

        SDKLog.i(TAG, "startPlayer: lens=$lens, lensId=$lensId, devId=$devId")

        // 1. Release the old player (keep the PlayerView container)
        autelPlayer?.let { player ->
            SDKLog.i(TAG, "startPlayer: releasing old player, old lensId=$mPlayingLensId")
            player.stopPlayer()
            player.removeVideoView()
            player.releasePlayer()
            autelPlayer = null
        }

        // 2. Create the new player (on-demand API: AutelPlayer(devId, lensId))
        mPlayingLensId = lensId
        val player = AutelPlayer(devId, lensId)
        player.addVideoView(autelPlayerView)
        player.startPlayer()
        player.applyFullScreenWeight()
        autelPlayer = player

        SDKLog.i(TAG, "startPlayer: player started for lens=$lens, lensId=$lensId")
    }

    private fun createAutelCodecView(): AutelPlayerView {
        val codecView = AutelPlayerView(requireContext())
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        codecView.setStretch(true)
        codecView.layoutParams = params
        return codecView
    }

    // IAutelDroneListener: retry initVideo on connect; release players on disconnect
    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        SDKLog.i(TAG, "onDroneChangedListener: connected=$connected, device=${drone.deviceNumber()}")
        if (connected) {
            activity?.runOnUiThread {
                if (autelPlayer == null) {
                    SDKLog.i(TAG, "onDroneChangedListener: player not created yet, retry initVideo")
                    initVideo()
                }
            }
        } else {
            activity?.runOnUiThread {
                SDKLog.i(TAG, "onDroneChangedListener: disconnected, releasing player")
                autelPlayer?.let { player ->
                    player.stopPlayer()
                    player.removeVideoView()
                    player.releasePlayer()
                }
                autelPlayer = null
                mPlayingLensId = null
                deviceId = null
                lensIdMap = emptyMap()
            }
        }
    }
}
