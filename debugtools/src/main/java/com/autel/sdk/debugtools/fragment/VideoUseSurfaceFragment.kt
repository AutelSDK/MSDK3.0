package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.alink.enums.VideoTransMissionModeEnum
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.player.MediaInfo
import com.autel.player.codec.StreamData
import com.autel.player.player.IVideoStreamListener
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.sdk.debugtools.helper.PlayerWeightHelper.applySplitScreenWeight
import com.autel.sdk.debugtools.databinding.FragmentWithSurfaceBinding
import java.nio.ByteBuffer

/**
 * multiple type of codec for video streaming
 * @author Autel Robotics on 2022/12/17.
 */
class VideoUseSurfaceFragment : AutelFragment(), IAutelDroneListener {

    private var mAutelPlayer: AutelPlayer? = null
    private var mAutelPlayer2: AutelPlayer? = null
    private lateinit var uiBinding: FragmentWithSurfaceBinding

    private var deviceId: String? = null
    private var zoomLensId: Int = 0
    private var thermalLensId: Int = 0

    /** Cached Surface for delayed device connection */
    private var leftSurface: android.view.Surface? = null
    private var rightSurface: android.view.Surface? = null

    private val videoListener = object: IVideoStreamListener{
        override fun onVideoSizeChanged(
            playerId: Int,
            width: Int,
            height: Int
        ) {
            SDKLog.i(TAG,"onVideoSizeChanged=>playerId:$playerId;")
        }

        override fun onVideoInfoCallback(
            playerId: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int
        ) {
            SDKLog.i(TAG,"onVideoInfoCallback=>playerId:$playerId;")
        }

        override fun onFrameYuv(yuv: ByteBuffer?, mediaInfo: MediaInfo?) {
            SDKLog.i(TAG,"onFrameYuv=>mediaInfo:$mediaInfo;")
        }

        override fun onVideoErrorCallback(
            playerId: Int,
            type: Int,
            errorContent: String?
        ) {
            SDKLog.i(TAG,"onVideoErrorCallback=>playerId:$playerId;type:$type;errorContent:$errorContent")
        }

        override fun onStreamSourceChanged(
            playerId: Int,
            newType: StreamData.StreamSourceType?
        ) {
            SDKLog.i(TAG,"onStreamSourceChanged=>playerId:$playerId;newType:$newType")
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        uiBinding = FragmentWithSurfaceBinding.inflate(inflater, container, false)
        return uiBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceManager.getDeviceManager().addDroneListener(this)

        // Register SurfaceHolder callbacks (record only; set on the Player after creation)
        uiBinding.leftView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                leftSurface = holder.surface
                mAutelPlayer?.setExternalSurface(holder.surface)
                maybeInitPlayers()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { leftSurface = null }
        })
        uiBinding.rightView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                rightSurface = holder.surface
                mAutelPlayer2?.setExternalSurface(holder.surface)
                maybeInitPlayers()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { rightSurface = null }
        })

        maybeInitPlayers()
    }

    /**
     * Create and start the Player only after all Surfaces are ready:
     * the compat layer redirects and calls startNewPlayer right after Player creation; starting without a Surface configures MediaCodec in
     * ByteBuffer mode, and a later setExternalSurface after setOutputSurface
     * always throws IllegalStateException (MediaDataInputThread crash).
     */
    private fun maybeInitPlayers() {
        if (mAutelPlayer != null) return  // avoid duplicate creation
        if (leftSurface == null || rightSurface == null) {
            SDKLog.i(TAG, "maybeInitPlayers: surface not ready, wait surfaceCreated")
            return
        }
        val droneDevice = DeviceManager.getDeviceManager().getFirstDroneDevice() ?: return
        initPlayers(droneDevice)
    }

    /** Extracted device-dependent init; reentrant from the device connection callback (Surfaces must be ready first) */
    private fun initPlayers(droneDevice: IAutelDroneDevice) {
        val gimbalType = droneDevice.getGimbalDeviceType()
        val cameraManager = droneDevice.getCameraAbilitySetManger()
        val lensList = cameraManager?.getLensList(gimbalType) ?: emptyList()

        val zoomLens = lensList.firstOrNull { it == LensTypeEnum.TeleZoom || it == LensTypeEnum.Zoom }
        val thermalLens = lensList.firstOrNull { it == LensTypeEnum.TeleThermal || it == LensTypeEnum.Thermal }
        zoomLensId = zoomLens?.let { cameraManager?.getLenId(it, gimbalType) } ?: 0
        thermalLensId = thermalLens?.let { cameraManager?.getLenId(it, gimbalType) } ?: 0
        deviceId = droneDevice.deviceNumber().toString()

        SDKLog.i(TAG, "initPlayers: deviceId=$deviceId, zoomLens=$zoomLens(id=$zoomLensId), thermalLens=$thermalLens(id=$thermalLensId)")

        droneDevice.getKeyManager()?.setValue(
            KeyTools.createKey(AirLinkKey.KeyALinkTransmissionMode),
            VideoTransMissionModeEnum.HIGH_QUALITY, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {}
                override fun onFailure(error: IAutelCode, msg: String?) {}
            }
        )

        val devId = deviceId ?: return
        mAutelPlayer = AutelPlayer(devId, zoomLensId)
        mAutelPlayer!!.addVideoInfoListener(videoListener)
        leftSurface?.let { mAutelPlayer?.setExternalSurface(it) }
        mAutelPlayer?.startPlayer()
        mAutelPlayer.applySplitScreenWeight()

        mAutelPlayer2 = AutelPlayer(devId, thermalLensId)
        mAutelPlayer2?.addVideoInfoListener(videoListener)
        rightSurface?.let { mAutelPlayer2?.setExternalSurface(it) }
        mAutelPlayer2?.startPlayer()
        mAutelPlayer2.applySplitScreenWeight()
    }

    override fun onDestroy() {
        super.onDestroy()
        DeviceManager.getDeviceManager().removeDroneListener(this)

        // Stop before releasing; do not call setExternalSurface(null): it marks the running decode thread dirty,
        // triggering a setOutputSurface crash during stop (Surface cleanup is handled by releasePlayer)
        mAutelPlayer?.removeVideoInfoListener(videoListener)
        mAutelPlayer?.stopPlayer()
        mAutelPlayer?.releasePlayer()
        mAutelPlayer = null

        mAutelPlayer2?.removeVideoInfoListener(videoListener)
        mAutelPlayer2?.stopPlayer()
        mAutelPlayer2?.releasePlayer()
        mAutelPlayer2 = null
        deviceId = null
        leftSurface = null
        rightSurface = null
    }

    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        SDKLog.i(TAG, "onDroneChangedListener: connected=$connected")
        if (connected) {
            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                if (mAutelPlayer == null) {
                    SDKLog.i(TAG, "onDroneChangedListener: player not created, retry initPlayers")
                    maybeInitPlayers()
                }
            }
        } else {
            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                mAutelPlayer?.stopPlayer()
                mAutelPlayer?.releasePlayer()
                mAutelPlayer = null
                mAutelPlayer2?.stopPlayer()
                mAutelPlayer2?.releasePlayer()
                mAutelPlayer2 = null
                deviceId = null
            }
        }
    }
}
