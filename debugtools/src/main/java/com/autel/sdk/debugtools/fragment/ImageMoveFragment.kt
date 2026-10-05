package com.autel.sdk.debugtools.fragment


import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightMissionKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.alink.enums.VideoTransMissionModeEnum
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.UAVImageControlMessageBean
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.sdk.debugtools.databinding.FragmentImageMoveBinding
import com.autel.sdk.debugtools.dialog.UAVControlDialog
import com.autel.sdk.debugtools.listener.OnItemSelectedListener
import com.google.gson.Gson

/**
 * Image settings demo (on-demand streaming)
 */
class ImageMoveFragment : AutelFragment() {

    private var autelPlayer: AutelPlayer? = null
    private lateinit var autelPlayerView: AutelPlayerView
    private var binding: FragmentImageMoveBinding? = null

    private var lensType = LensTypeEnum.Zoom

    private var deviceId: String? = null
    private var lensIdMap: Map<LensTypeEnum, Int> = emptyMap()
    private var mPlayingLensId: Int? = null

    private val gson = Gson()
    private var sequence: Int = 1
    private var keyManager: IKeyManager? = null
    private val keyUAVImageControl = KeyTools.createKey(FlightMissionKey.KeyUAVImageControl)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentImageMoveBinding.inflate(inflater)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keyManager = DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        initVideo()
        binding?.btnImageSet?.setOnClickListener { setDroneImageControl() }
    }

    override fun onDestroy() {
        super.onDestroy()
        autelPlayer?.let { player ->
            player.removeVideoView()
            player.stopPlayer()
            player.releasePlayer()
        }
        autelPlayer = null
        mPlayingLensId = null
        deviceId = null
        lensIdMap = emptyMap()
        binding = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initVideo() {
        val droneDevice = DeviceManager.getDeviceManager().getFirstDroneDevice() ?: return
        deviceId = droneDevice.deviceNumber().toString()

        droneDevice.getKeyManager().setValue(
            KeyTools.createKey(AirLinkKey.KeyALinkTransmissionMode),
            VideoTransMissionModeEnum.HIGH_QUALITY, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    SDKLog.i(TAG, "KeyALinkTransmissionMode->onSuccess")
                }

                override fun onFailure(code: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "KeyALinkTransmissionMode->onFailure: code=$code, msg=$msg")
                }
            }
        )

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

        autelPlayerView = createAutelCodecView()
        binding?.videoLayout?.addView(autelPlayerView)

        startPlayer(lensType)

        val lensName = lens?.map { it.value } ?: listOf()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lensName)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding?.spinnerLens?.adapter = adapter
        var index = lens?.indexOf(lensType) ?: -1
        if (index == -1) index = 0
        binding?.spinnerLens?.setSelection(index)
        binding?.spinnerLens?.onItemSelectedListener = object : OnItemSelectedListener() {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val posType = lens?.get(position) ?: LensTypeEnum.Zoom
                if (lensType != posType) {
                    lensType = posType
                    startPlayer(lensType)
                }
            }
        }
    }

    private fun startPlayer(lens: LensTypeEnum) {
        val devId = deviceId ?: run {
            SDKLog.w(TAG, "startPlayer: deviceId is null")
            return
        }
        val lensId = lensIdMap[lens] ?: run {
            SDKLog.w(TAG, "startPlayer: no lensId for lens=$lens")
            return
        }

        if (lensId == mPlayingLensId && autelPlayer != null) {
            SDKLog.i(TAG, "startPlayer: same lensId=$lensId, skip")
            return
        }

        SDKLog.i(TAG, "startPlayer: lens=$lens, lensId=$lensId, devId=$devId")

        autelPlayer?.let { player ->
            SDKLog.i(TAG, "startPlayer: releasing old player, old lensId=$mPlayingLensId")
            player.removeVideoView()
            player.stopPlayer()
            player.releasePlayer()
            autelPlayer = null
        }

        mPlayingLensId = lensId
        val player = AutelPlayer(devId, lensId)
        player.addVideoView(autelPlayerView)
        player.startPlayer()
        autelPlayer = player

        SDKLog.i(TAG, "startPlayer: player started for lens=$lens, lensId=$lensId")
    }

    private fun setDroneImageControl() {
        val drone = DeviceManager.getDeviceManager().getFirstDroneDevice() ?: return
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
            message.cameraId = drone.getCameraAbilitySetManger().getLenId(lensType, drone.getGimbalDeviceType()) ?: 0
            SDKLog.i(TAG, "setDroneImageControl $message")
            keyManager?.setFrequencyReport(keyUAVImageControl, message)
        }
        dialog.show()
    }

    private fun createAutelCodecView(): AutelPlayerView {
        val codecView = AutelPlayerView(requireContext())
        codecView.setStretch(true)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        codecView.layoutParams = params
        return codecView
    }
}
