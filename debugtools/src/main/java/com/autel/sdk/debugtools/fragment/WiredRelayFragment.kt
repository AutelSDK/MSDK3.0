package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RemoteControllerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.bean.HardwareButtonInfoBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.enums.RCButtonTypeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.enums.RcButtonStateEnum
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.drone.sdk.vmodelx.module.networking.EthernetController
import com.autel.drone.sdk.vmodelx.module.networking.IRelayListener
import com.autel.drone.sdk.vmodelx.module.networking.RelayDeviceType
import com.autel.drone.sdk.vmodelx.module.networking.WiredRelayManager
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.sdk.debugtools.databinding.FragmentWiredRelayBinding
import com.autel.sdk.debugtools.dialog.RemoterRoleSettingDialog
import java.util.concurrent.atomic.AtomicBoolean

class WiredRelayFragment : AutelFragment() {

    companion object {
        private const val TAG = "WiredRelayFragment"
    }

    private lateinit var binding: FragmentWiredRelayBinding
    private var dialog: RemoterRoleSettingDialog? = null
    private var codecView: AutelPlayerView? = null
    private var autelPlayer: AutelPlayer? = null
    private var isShowDialog = AtomicBoolean(false)
    private var deviceId: String? = null
    private var lensId: Int = 0

    private val wiredRelayManager = WiredRelayManager.get()
    private var rcHardWareInfoKey = KeyTools.createKey(RemoteControllerKey.KeyRCHardwareInfo)
    private val rcHardWareCallback = object : CommonCallbacks.KeyListener<HardwareButtonInfoBean> {
        override fun onValueChange(
            oldValue: HardwareButtonInfoBean?,
            newValue: HardwareButtonInfoBean
        ) {
            hardwareInfoBean(newValue)
        }
    }

    private val listener = object : IRelayListener {
        override fun onRelayModeConnect(connect: Boolean, isRelayDevice: Boolean) {
            SDKLog.i(TAG, "onRelayModeConnect: connect=$connect, isRelay=$isRelayDevice")
            // If disconnected while the dialog is showing, close it
            if (!connect && isShowDialog.get()) {
                dialog?.dismiss()
                isShowDialog.set(false)
            }
            updateStatusBar()
            updateRelaySwitchButton()
        }

        override fun onRelayType(connect: Boolean, type: RelayDeviceType) {
            SDKLog.i(TAG, "onRelayType: connect=$connect, type=$type")
            updateStatusBar()
            updateRelayRoleView()
        }

        override fun onNeedChooseRole(show: Boolean) {
            SDKLog.i(TAG, "onNeedChooseRole: show=$show")

            if (!show) {
                if (isShowDialog.get()) {
                    dialog?.dismiss()
                    isShowDialog.set(false)
                }
                return
            }

            if (isShowDialog.get()) {
                return
            }

            isShowDialog.set(true)
            dialog = RemoterRoleSettingDialog()
            dialog?.let { dlg ->
                dlg.setOnConfirmListener { isMaster ->
                    // isMaster=true -> enableRelayMasterMode(true) set as master RC
                    // isMaster=false -> enableRelayMasterMode(false) set as relay RC
                    wiredRelayManager.enableRelayMasterMode(isMaster)
                }
                dlg.dialog?.setOnDismissListener {
                    isShowDialog.set(false)
                }

                SDKLog.i(
                    TAG,
                    "RoleDialog -> Both parties need to choose role, popup confirmation dialog"
                )
                activity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.add(dlg, "RemoterRoleSettingDialog")
                    ?.commitAllowingStateLoss()
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWiredRelayBinding.inflate(inflater)
        wiredRelayManager.addListener(listener)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //init player
        initPlayer()

        // Initialize relay mode role switch
        initRelayRoleButton()
        updateRelayRoleView()
        updateRelaySwitchButton()
        initNetRelaySwitchButton()

        updateStatusBar()

        DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
            .listen(rcHardWareInfoKey, callback = rcHardWareCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wiredRelayManager.removeListener(listener)
        releasePlayer()

        DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager()
            .cancelListen(rcHardWareInfoKey, callback = rcHardWareCallback)
    }

    private fun initPlayer() {
        val droneDevice = DeviceManager.getDeviceManager().getFirstDroneDevice()
        deviceId = droneDevice?.deviceNumber()?.toString()
        val devId = deviceId
        if (devId == null) {
            SDKLog.w(TAG, "initPlayer: droneDevice is null, skip")
            return
        }

        val gimbalType = droneDevice?.getGimbalDeviceType()
        val cameraManager = droneDevice?.getCameraAbilitySetManger()
        val lensList = cameraManager?.getLensList(gimbalType) ?: emptyList()
        val zoomLens = lensList.firstOrNull {
            it == LensTypeEnum.TeleZoom || it == LensTypeEnum.Zoom
        } ?: LensTypeEnum.Zoom
        lensId = cameraManager?.getLenId(zoomLens, gimbalType) ?: 0

        SDKLog.i(TAG, "initPlayer: devId=$devId, lensId=$lensId, zoomLens=$zoomLens")

        releasePlayer()

        codecView = createAutelCodecView()
        binding.videoView.addView(codecView)

        autelPlayer = AutelPlayer(devId, lensId)
        autelPlayer?.addVideoView(codecView)
        autelPlayer?.startPlayer()
    }

    private fun releasePlayer() {
        autelPlayer?.let { player ->
            SDKLog.i(TAG, "releasePlayer: stopping and releasing player")
            player.removeVideoView()
            player.stopPlayer()
            player.releasePlayer()
        }
        autelPlayer = null
        codecView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        codecView = null
    }

    private fun createAutelCodecView(): AutelPlayerView {
        val codecView = AutelPlayerView(activity)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        codecView.setStretch(true)
        codecView.layoutParams = params
        return codecView
    }

    private fun updateStatusBar(){
        val isConnect = wiredRelayManager.isConnected() ?: false
        binding.tvStatus.text = if (isConnect) "Connected" else "Disconnected"

        val isMaster = wiredRelayManager.isRelayMasterMode()
        binding.tvRole.text = if (!isMaster) "Relay" else "Master"

        //local is definitely the RC
        binding.tvLocalType.text = "RC"

        val remoteType = wiredRelayManager.getRelayType()
        binding.tvRemoteType.text = remoteType?.value ?: "UNKNOWN"
    }

    /**
     * Initialize relay switch views
     */
    private fun initRelayRoleButton() {
        // Relay master mode button
        binding.btnRelayRoleSwitch.setOnClickListener {
            val isRelayMaster = wiredRelayManager.isRelayMasterMode()
            if (isRelayMaster) {
                Toast.makeText(context, "Relay master change to Relay", Toast.LENGTH_SHORT).show()
                wiredRelayManager.enableRelayMasterMode(false)
            } else {
                wiredRelayManager.enableRelayMasterMode(true)
                Toast.makeText(context, "Relay change to Relay master", Toast.LENGTH_SHORT).show()
            }
            updateRelayRoleView()
            updateStatusBar()
            updateRelaySwitchButton()
        }
    }

    private fun updateRelayRoleView(){
        val isRelayMaster = wiredRelayManager.isRelayMasterMode()
        if(isRelayMaster){
            binding.tvRelayRoleState.text = "YES"
        }else{
            binding.tvRelayRoleState.text = "No"
        }
    }



    /**
     * Initialize the relay switch button (one-tap enable Ethernet and Ethernet tethering)
     */
    private fun initNetRelaySwitchButton() {
        binding.btnRelaySwitch.setOnClickListener {
            SDKLog.i(TAG, "======== Relay switch button clicked ========")
            val isEthernetShareOpen = EthernetController.isEthernetShareOpen()
            if (!isEthernetShareOpen) {
                EthernetController.ethernetSharSwitch(true)
                Toast.makeText(context, "start open ethernet and share", Toast.LENGTH_SHORT).show()
            } else {
                EthernetController.ethernetSharSwitch(false)
                Toast.makeText(context, "start close share", Toast.LENGTH_SHORT).show()
            }
             updateRelaySwitchButton()
        }
       updateRelaySwitchButton()
    }

    private fun updateRelaySwitchButton() {
        binding.tvRelayOpenState.postDelayed({
            val isEthernetOpen = EthernetController.isEthernetOpen()
            val isEthernetShareOpen = EthernetController.isEthernetShareOpen()
            if (isEthernetShareOpen) {
                binding.tvRelayOpenState.text =
                    "Opened(Ethernet=$isEthernetOpen,EthernetShare=$isEthernetShareOpen)"
            } else {
                binding.tvRelayOpenState.text =
                    "Closed(Ethernet=$isEthernetOpen,EthernetShare=$isEthernetShareOpen)"
            }
        }, 3000)
    }


    private fun hardwareInfoBean(infoBean: HardwareButtonInfoBean) {
        SDKLog.i(TAG, "receive11 remote upload, infoBean = $infoBean")
        if (infoBean.clickType != RcButtonStateEnum.ROLL) return

        val drone = DeviceManager.getDeviceManager().getFirstDroneDevice() ?: return
        SDKLog.i(TAG, "receive remote upload, lensId=$lensId")

        when (infoBean.buttonType) {
            RCButtonTypeEnum.ZOOM_IN -> {
                setSmoothZoom(drone.getKeyManager(), lensId, true, infoBean.thumbWheelValue)
            }
            RCButtonTypeEnum.ZOOM_OUT -> {
                setSmoothZoom(drone.getKeyManager(), lensId, false, infoBean.thumbWheelValue)
            }
            else -> {}
        }
    }

    private fun setSmoothZoom(
        keyManager: IKeyManager,
        cameraId: Int,
        zoomIn: Boolean,
        thumbValue: Int
    ) {
        val key = KeyTools.createKey(CameraKey.KeySmoothZoom, cameraId)
        val wheel = if (zoomIn) {
            thumbValue / 25.5 * 100
        } else {
            -thumbValue / 25.5 * 100
        }.toInt()

        keyManager.setValue(key, wheel, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                SDKLog.i(TAG, "set smooth zoom success, the wheel = $wheel")
            }

            override fun onFailure(code: IAutelCode, msg: String?) {
                SDKLog.e(TAG, "set smooth zoom failed, the wheel = $wheel, code = $code")
            }
        })
    }
}
