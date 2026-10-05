package com.autel.sdk.debugtools.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.autel.drone.sdk.SDKConstants
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.SDKManager
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.player.codec.OnRenderFrameInfoListener
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.publisher.IPublishListener
import com.autel.publisher.PublishErrorCode
import com.autel.publisher.PublishParam
import com.autel.publisher.rtmp.RTMPPublisherNew
import com.autel.sdk.debugtools.helper.PlayerWeightHelper.applySplitScreenWeight
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentLivestreamBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.launch
import org.autelrtc.VideoFrame
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey.KeyCameraTransferSeiEnable
import com.autel.player.player.render.TextureManager
import java.io.IOException

/**
 * live streaming of video on media player(custom exoplayer)
 * @author Autel Robotics on 2022/12/17.
 */

class LiveStreamFragment : AutelFragment(), OnRenderFrameInfoListener, IAutelDroneListener {

    var contentView: View? = null
    lateinit var edit_url: EditText
    lateinit var btn_start: Button
    lateinit var btn_switch:Button
    lateinit var btn_refersh:Button

    lateinit var transmit_info_view_left: TextView
    lateinit var transmit_info_view_right: TextView

    var right_view: LinearLayout? = null
    private var mAutelPlayer: AutelPlayer? = null
    var codecView: AutelPlayerView? = null

    var left_view: LinearLayout? = null
    private var mAutelPlayer2: AutelPlayer? = null
    var codecView2: AutelPlayerView? = null

    private var deviceId: String? = null
    private var zoomLensId: Int = 0
    private var thermalLensId: Int = 0

    var rtmpPort:Int = SDKConstants.STREAM_CHANNEL_16110;

    private lateinit var uiBinding: FragmentLivestreamBinding

    private var mPublishFlag: Boolean = false

    private var iCurrentPort:Int = SDKConstants.STREAM_CHANNEL_16110
    private var rtmpUrl:String  = "rtmp://YOUR_RTMP_SERVER/live/stream"

    private val publisherJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + publisherJob)
    private var connectStatus = -1;

    private var mPublisher = RTMPPublisherNew();

    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == 1) {
            getVideoFps()
        }
        return true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        uiBinding = FragmentLivestreamBinding.inflate(inflater, container, false)
        return uiBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceManager.getDeviceManager().addDroneListener(this)

        edit_url = uiBinding.root.findViewById(R.id.edit_url)
        btn_start = uiBinding.root.findViewById(R.id.btn_start)
        btn_switch= uiBinding.root.findViewById(R.id.btn_switch)
        btn_refersh = uiBinding.root.findViewById(R.id.btn_refersh)

        transmit_info_view_left = uiBinding.root.findViewById(R.id.transmit_info_view_left)
        transmit_info_view_right = uiBinding.root.findViewById(R.id.transmit_info_view_right)

        left_view = uiBinding.root.findViewById(R.id.layout_left_view)
        codecView = createAutelCodecView()
        with(left_view) { this?.addView(codecView) }

        right_view = uiBinding.root.findViewById(R.id.layout_right_view)
        codecView2 = createAutelCodecView2()
        with(right_view) { this?.addView(codecView2) }

        val droneDevice = DeviceManager.getDeviceManager().getFirstDroneDevice()
        if (droneDevice != null) {
            initPlayers(droneDevice)
        }

        SDKLog.i(
            TAG,
            "preview: AutelPlayer(deviceId,lensId)+AutelPlayerView+startPlayer() dual decode; " +
                "same MSDK3 Player construction as OnDemandStreamFragment; this page plays a fixed dual stream on entry (no on-demand tab start/stop)"
        )
        initListener()
        initView()
    }

    /** Extracted device-dependent init; reentrant from the device connection callback */
    private fun initPlayers(droneDevice: IAutelDroneDevice) {
        if (mAutelPlayer != null) return  // avoid duplicate creation

        val gimbalType = droneDevice.getGimbalDeviceType()
        val cameraManager = droneDevice.getCameraAbilitySetManger()
        val lensList = cameraManager?.getLensList(gimbalType) ?: emptyList()

        val zoomLens = lensList.firstOrNull { it == LensTypeEnum.TeleZoom || it == LensTypeEnum.Zoom }
        val thermalLens = lensList.firstOrNull { it == LensTypeEnum.TeleThermal || it == LensTypeEnum.Thermal }
        zoomLensId = zoomLens?.let { cameraManager?.getLenId(it, gimbalType) } ?: 0
        thermalLensId = thermalLens?.let { cameraManager?.getLenId(it, gimbalType) } ?: 0
        deviceId = droneDevice.deviceNumber().toString()

        SDKLog.i(TAG, "initPlayers: deviceId=$deviceId, zoomLens=$zoomLens(id=$zoomLensId), thermalLens=$thermalLens(id=$thermalLensId)")

        val devId = deviceId ?: return
        mAutelPlayer = AutelPlayer(devId, zoomLensId)
        mAutelPlayer!!.addVideoView(codecView)
        mAutelPlayer!!.startPlayer()
        mAutelPlayer.applySplitScreenWeight()

        mAutelPlayer2 = AutelPlayer(devId, thermalLensId)
        mAutelPlayer2!!.addVideoView(codecView2)
        mAutelPlayer2!!.startPlayer()
        mAutelPlayer2.applySplitScreenWeight()
    }

    /** Produce a readable PublishErrorCode message to help xlog diagnose failures */
    private fun publishErrorDetail(code: PublishErrorCode): String =
        "name=${code.name}, ordinal=${code.ordinal}, toString=$code"

    private fun configCameraTransferSeiEnable(enable: Boolean) {
        if (DeviceManager.getDeviceManager().getFirstDroneDevice() == null) {
            return
        }
        val keyManager = DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()
        val key = KeyTools.createKey(KeyCameraTransferSeiEnable)
        SDKLog.i(SDKManager.TAG, "configCameraTransferSeiEnable : $enable")
        keyManager?.setValue(key, enable, object : CommonCallbacks.CompletionCallback {
            override fun onFailure(code: IAutelCode, msg: String?) {
                SDKLog.i(SDKManager.TAG, "configCameraTransferSeiEnable faild:" + msg + " code:" + code.code)
            }

            override fun onSuccess() {
                SDKLog.i(SDKManager.TAG, "configCameraTransferSeiEnable succ value=$enable")
            }
        }, 3)
    }

    /**
     * create code view for media player 2
     */
    private fun createAutelCodecView2(): AutelPlayerView? {
        val codecView = AutelPlayerView(activity)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        codecView.layoutParams = params
        return codecView
    }


    private fun createAutelCodecView(): AutelPlayerView? {
        val codecView = AutelPlayerView(activity)
        val params = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        codecView.layoutParams = params
        return codecView
    }

    private fun initView() {

        edit_url.setText(rtmpUrl)

        btn_start.setOnClickListener {
                if (mPublishFlag) {
                    mPublishFlag = false;
                    stopPublish()
                } else {
                    mPublishFlag = true
                    startPublish()
                }
        }

        btn_switch.setOnClickListener {
            // Log on the main thread first so a tap is visible even if only the Default coroutine logs
            SDKLog.i(
                TAG,
                "[RTMP] btn_switch clicked: mPublishFlag=$mPublishFlag, iCurrentPort=$iCurrentPort " +
                    "(16110→${SDKConstants.STREAM_CHANNEL_16110}, 16115→${SDKConstants.STREAM_CHANNEL_16115})"
            )
            coroutineScope.launch {
                if (iCurrentPort == SDKConstants.STREAM_CHANNEL_16110) {
                    if (mPublishFlag) {
                        ToastUtils.showToast(getString(R.string.debug_livestream_push_infrared))
                    } else {
                        ToastUtils.showToast(getString(R.string.debug_livestream_switch_infrared_not_pushing))
                    }
                    iCurrentPort = SDKConstants.STREAM_CHANNEL_16115
                    SDKLog.i(TAG, "[RTMP] switchStreamSource: switched to IR channel=$iCurrentPort, needRestart=$mPublishFlag")
                    mPublisher.switchStreamSource(iCurrentPort, true)
                } else {
                    if (mPublishFlag) {
                        ToastUtils.showToast(getString(R.string.debug_livestream_push_wideangle))
                    } else {
                        ToastUtils.showToast(getString(R.string.debug_livestream_switch_wideangle_not_pushing))
                    }
                    iCurrentPort = SDKConstants.STREAM_CHANNEL_16110
                    SDKLog.i(TAG, "[RTMP] switchStreamSource: switched to wide-angle channel=$iCurrentPort, needRestart=$mPublishFlag")
                    mPublisher.switchStreamSource(iCurrentPort, true)
                }
                SDKLog.i(TAG, "[RTMP] switchStreamSource: native call issued")
            }
        }

        btn_refersh.setOnClickListener {
            SDKLog.i(TAG, "[RTMP] btn_refresh clicked: mPublishFlag=$mPublishFlag")
            if (!mPublishFlag) {
                SDKLog.w(TAG, "[RTMP] btn_refresh ignored: not publishing, no refresh action (original logic returned directly)")
                return@setOnClickListener
            }
            coroutineScope.launch {
                SDKLog.w(
                    TAG,
                    "[RTMP] btn_refresh: debug placeholder, no reconnect/reconfigure logic; add stop→configure→start or the publisher refresh API here if needed"
                )
            }
        }

        coroutineScope.launch {
            var param = PublishParam.Builder().setUrl(rtmpUrl).setStreamSource(iCurrentPort).build()
            mPublisher.configure(param)
            SDKLog.i(TAG, "configure RTMP: url=$rtmpUrl streamSource=$iCurrentPort")
        }

        handler.sendEmptyMessageDelayed(1, 1000)
    }

    @SuppressLint("SetTextI18n")
    private fun initListener() {
        mPublisher.setOnPublishListener(object :
            IPublishListener{
            override fun onConnecting() {
                SDKLog.i(TAG, "[RTMP] onConnecting")
                ToastUtils.showToast(getString(R.string.debug_livestream_connecting))
            }

            override fun onConnected() {
                SDKLog.i(TAG, "[RTMP] onConnected: signaling/connect phase OK")
                ToastUtils.showToast(getString(R.string.debug_livestream_connected))
            }

            override fun onConnectedFailed(code: PublishErrorCode) {
                SDKLog.e(TAG, "[RTMP] onConnectedFailed: ${publishErrorDetail(code)}")
                ToastUtils.showToast(getString(R.string.debug_livestream_connect_failed))
            }

            override fun onStartPublish() {
                SDKLog.i(TAG, "[RTMP] onStartPublish: pushing stream to server, activeStreamChannel=$iCurrentPort")
                if (iCurrentPort == SDKConstants.STREAM_CHANNEL_16110) {
                    ToastUtils.showToast(getString(R.string.debug_livestream_push_wideangle))
                } else if (iCurrentPort == SDKConstants.STREAM_CHANNEL_16115) {
                    ToastUtils.showToast(getString(R.string.debug_livestream_push_infrared))
                }
            }

            override fun onStopPublish() {
                SDKLog.i(TAG, "[RTMP] onStopPublish")
                ToastUtils.showToast(getString(R.string.debug_livestream_stop_push))
            }

            override fun onFpsStatistic(fps: Int, channelName: String) {
                SDKLog.d(TAG, "[RTMP] onFpsStatistic fps=$fps channel=$channelName")
                lifecycleScope.launch(Dispatchers.Main) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        if (iCurrentPort == SDKConstants.STREAM_CHANNEL_16110) {
                            uiBinding.leftPushFps.text = getString(R.string.debug_livestream_push_fps, fps)
                            uiBinding.rightPushFps.text = getString(R.string.debug_livestream_push_fps, 0)
                        } else if (iCurrentPort == SDKConstants.STREAM_CHANNEL_16115) {
                            uiBinding.leftPushFps.text = getString(R.string.debug_livestream_push_fps, 0)
                            uiBinding.rightPushFps.text = getString(R.string.debug_livestream_push_fps, fps)
                        }
                    }
                }
            }

            override fun onVideoBitrate(value: Int, channelName: String) {
                SDKLog.d(TAG, "[RTMP] onVideoBitrate bps=$value channel=$channelName")
            }

            override fun onAudioBitrate(value: Int) {
                SDKLog.d(TAG, "[RTMP] onAudioBitrate bps=$value")
            }

            override fun onPublishSuccess() {
                SDKLog.i(TAG, "[RTMP] onPublishSuccess: publish succeeded (business-layer confirmation), streamChannel=$iCurrentPort")
            }

            override fun onPublishFailed(errorCode: PublishErrorCode) {
                SDKLog.e(TAG, "[RTMP] onPublishFailed: ${publishErrorDetail(errorCode)} streamChannel=$iCurrentPort")
                ToastUtils.showToast(getString(R.string.debug_livestream_push_failed))
            }

            override fun onPublishFailed(channelName: String, errorCode: PublishErrorCode) {
                SDKLog.e(TAG, "[RTMP] onPublishFailed channel=$channelName ${publishErrorDetail(errorCode)}")
            }

            override fun onReconnect() {
                SDKLog.w(TAG, "[RTMP] onReconnect")
                ToastUtils.showToast(getString(R.string.debug_livestream_reconnecting))
            }

        });

//        mAutelPlayer?.setTransmitStatisticsListener { info ->
//            if (info.msgId == SDKConstants.STREAM_CHANNEL_16110) {
//                transmit_info_view_left.post {
//                    transmit_info_view_left.visibility = View.VISIBLE
//                    transmit_info_view_left.text = """
//                        Lens: ${info.msgId}
//                        Timestamp: ${info.pts}
//                        Capture-encode delay: ${info.capEncInterval}
//                        Pacer delay: ${info.pacerInterval}
//                        Network send delay: ${info.sendJitterInterval}
//                        Decode delay: ${info.decodeInterval}
//                        Render buffer jitter delay: ${info.jitterInterval}
//                        Render delay: ${info.renderInterval}
//                        Total delay: ${info.sumInterval}
//                    """.trimIndent()
//                }
//            } else if (info.msgId == SDKConstants.STREAM_CHANNEL_16115) {
//                transmit_info_view_right.post {
//                    transmit_info_view_right.visibility = View.VISIBLE
//                    transmit_info_view_right.text = """
//                        Lens: ${info.msgId}
//                        Timestamp: ${info.pts}
//                        Capture-encode delay: ${info.capEncInterval}
//                        Pacer delay: ${info.pacerInterval}
//                        Network send delay: ${info.sendJitterInterval}
//                        Decode delay: ${info.decodeInterval}
//                        Render buffer jitter delay: ${info.jitterInterval}
//                        Render delay: ${info.renderInterval}
//                        Total delay: ${info.sumInterval}
//                    """.trimIndent()
//                }
//            }
//        }
    }

    private fun stopPublish() {
        mPublishFlag = false;
        SDKLog.i(TAG, "stopPublish: RTMP url=$rtmpUrl")
        coroutineScope.launch {
            mPublisher.stop()
        }

        lifecycleScope.launch(Dispatchers.Main) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                btn_start.text = getString(R.string.debug_start)
                uiBinding.leftPushFps.text = getString(R.string.debug_livestream_push_fps, 0)
                uiBinding.rightPushFps.text = getString(R.string.debug_livestream_push_fps, 0)
            }
        }
    }

    private fun startPublish() {
        SDKLog.i(TAG, "startPublish: RTMP url=$rtmpUrl streamSource=$iCurrentPort")
        coroutineScope.launch {
            mPublisher.start();
        }

        lifecycleScope.launch(Dispatchers.Main) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                btn_start.text = getString(R.string.debug_stop)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mPublishFlag = false

        // 1. Cancel all in-flight coroutines (configure/switchStreamSource/start/stop, etc.)
        publisherJob.cancel()

        // 2. In a separate coroutine: join and wait until all business coroutines have left native calls, then release resources
        //    cancelAndJoin blocks until all children finish (including native methods returning),
        //    more reliable than delay(200) and fully prevents concurrent native access SIGSEGVs
        CoroutineScope(Dispatchers.Default).launch {
            try {
                publisherJob.join()
            } catch (_: Exception) { }

            try {
                mPublisher.stop()
            } catch (e: Exception) {
                SDKLog.e(TAG, "stop publisher error: ${e.message}")
            }
            try {
                mPublisher.release()
            } catch (e: Exception) {
                SDKLog.e(TAG, "release publisher error: ${e.message}")
            }
        }

        mAutelPlayer?.stopPlayer()
        mAutelPlayer?.removeVideoView()
        mAutelPlayer?.releasePlayer()
        mAutelPlayer = null

        mAutelPlayer2?.stopPlayer()
        mAutelPlayer2?.removeVideoView()
        mAutelPlayer2?.releasePlayer()
        mAutelPlayer2 = null
        deviceId = null
        DeviceManager.getDeviceManager().removeDroneListener(this)
    }

    override fun onRenderFrameTimestamp(pts: Long) {

    }

    override fun onRenderFrameSizeChanged(width: Int, height: Int) {

    }

    override fun onFrameStream(videoBuffer: ByteArray?, isIFrame: Boolean, size: Int, pts: Long, videoType: Int) {
       //todo: H.264 stream callback lands here
    }

    override fun onFrameStream(frame: VideoFrame?, mAutelPlayerID: Int) {
        SDKLog.d(TAG, "onFrameStream: playerID=$mAutelPlayerID")
    }

    private fun getVideoFps() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                uiBinding.leftPlayFps.text = getString(R.string.debug_livestream_play_fps, mAutelPlayer?.videoFps ?: 0)
                uiBinding.rightPlayFps.text = getString(R.string.debug_livestream_play_fps, mAutelPlayer2?.videoFps ?: 0)
            }
        }
        handler.sendEmptyMessageDelayed(1, 1000)
    }

    private fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap? {
        return try {
            val inputStream = context.assets.open(fileName)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            SDKLog.printSafeStackTrace(e)
            null
        }
    }

    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        SDKLog.i(TAG, "onDroneChangedListener: connected=$connected")
        if (connected) {
            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                if (mAutelPlayer == null) {
                    SDKLog.i(TAG, "onDroneChangedListener: player not created, retry initPlayers")
                    initPlayers(drone)
                }
            }
        } else {
            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                mAutelPlayer?.stopPlayer(); mAutelPlayer?.removeVideoView(); mAutelPlayer?.releasePlayer(); mAutelPlayer = null
                mAutelPlayer2?.stopPlayer(); mAutelPlayer2?.removeVideoView(); mAutelPlayer2?.releasePlayer(); mAutelPlayer2 = null
                deviceId = null
            }
        }
    }
}