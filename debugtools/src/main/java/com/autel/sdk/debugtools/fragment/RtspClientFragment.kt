package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.autel.drone.sdk.SDKConstants
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.publisher.IPublishListener
import com.autel.publisher.PublishErrorCode
import com.autel.publisher.PublishParam
import com.autel.publisher.rtsp.RTSPPublisher

import com.autel.sdk.debugtools.helper.PlayerWeightHelper.applySplitScreenWeight
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentRtspclientBinding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RtspClientFragment : AutelFragment(), IAutelDroneListener {
    var contentView: View? = null
    lateinit var edit_url: EditText
    lateinit var btn_start: Button

    var right_view: LinearLayout? = null
    private var mAutelPlayer: AutelPlayer? = null
    var codecView: AutelPlayerView? = null

    var left_view: LinearLayout? = null
    private var mAutelPlayer2: AutelPlayer? = null
    var codecView2: AutelPlayerView? = null

    private var deviceId: String? = null
    private var zoomLensId: Int = 0
    private var thermalLensId: Int = 0

    private lateinit var uiBinding: FragmentRtspclientBinding

    private var mPublishFlag: Boolean = false

    private var iCurrentPort:Int = SDKConstants.STREAM_CHANNEL_16110 // wide-angle
    private var url:String = "rtsp://YOUR_RTSP_SERVER/live/stream1"

    private var url2:String = "rtsp://YOUR_RTSP_SERVER/live/stream2"

    private val leftPublisher get() = getString(R.string.rtsp_publisher_wide)
    private val rightPublisher get() = getString(R.string.rtsp_publisher_thermal)

    private var mPublisher = RTSPPublisher();

    private var mPublisher2 = RTSPPublisher();

    private val publisherJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + publisherJob)

    /** Serialize stop/start so a start during an unfinished stop cannot leave unclear state */
    private val rtspPublishMutex = Mutex()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        uiBinding = FragmentRtspclientBinding.inflate(inflater, container, false)

        return uiBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceManager.getDeviceManager().addDroneListener(this)

        edit_url = uiBinding.root.findViewById(R.id.edit_url)
        btn_start = uiBinding.root.findViewById(R.id.btn_start)

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
        initView();
        initListener()
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

    /** Produce a readable PublishErrorCode message to help xlog diagnose failures (connect / publish, etc.) */
    private fun publishErrorDetail(code: PublishErrorCode): String =
        "name=${code.name}, ordinal=${code.ordinal}, toString=$code"

    /**
     * After RTSPPublisher.stop() the native transport module is released; you must re-configure before starting again,
     * otherwise the usual symptom: startPublish logs but no onConnecting / onPublishSuccess for a long time.
     */
    private fun reconfigureRtspPublishers(reason: String) {
        val u1 = edit_url.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: url
        url = u1
        val param = PublishParam.Builder()
            .setUrl(u1)
            .setStreamSource(iCurrentPort)
            .setWidth(1920)
            .setHeight(1440)
            .setFps(30)
            .build()
        mPublisher.configure(param)
        SDKLog.i(TAG, "configure RTSP[wide] ($reason): url=$u1 streamSource=$iCurrentPort 1920x1440@30")

        val param2 = PublishParam.Builder()
            .setUrl(url2)
            .setStreamSource(SDKConstants.STREAM_CHANNEL_16115)
            .setWidth(640)
            .setHeight(512)
            .setFps(30)
            .build()
        mPublisher2.configure(param2)
        SDKLog.i(
            TAG,
            "configure RTSP[IR] ($reason): url=$url2 streamSource=${SDKConstants.STREAM_CHANNEL_16115} 640x512@30"
        )
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

        //mPublisher = RTSPPublisherNew()
        //CameraKey.KeyCameraTransferPayLoadType.create().set(VideoCompressStandardEnum.H265)
        edit_url.setText(url)
        reconfigureRtspPublishers("initView")
        btn_start.isEnabled = true

        btn_start.setOnClickListener {

            if (mPublishFlag) {
                mPublishFlag = false;
                stopPublish()
            } else {
                mPublishFlag = true
                startPublish()
            }
        }
    }

    private fun initListener() {
        mPublisher.setOnPublishListener(object :
            IPublishListener{
            override fun onConnecting() {
                SDKLog.i(TAG, "[RTSP-wide] onConnecting")
                ToastUtils.showToast(getString(R.string.rtsp_connecting, leftPublisher))
            }

            override fun onConnected() {
                SDKLog.i(TAG, "[RTSP-wide] onConnected: signaling/connect phase OK")
                ToastUtils.showToast(getString(R.string.rtsp_connected, leftPublisher))
            }

            override fun onConnectedFailed(code: PublishErrorCode) {
                SDKLog.e(TAG, "[RTSP-wide] onConnectedFailed: ${publishErrorDetail(code)}")
                ToastUtils.showToast(getString(R.string.rtsp_connect_failed, leftPublisher))
            }

            override fun onStartPublish() {
                SDKLog.i(TAG, "[RTSP-wide] onStartPublish: pushing stream to server")
                ToastUtils.showToast(getString(R.string.rtsp_stream_start, leftPublisher))
            }

            override fun onStopPublish() {
                SDKLog.i(TAG, "[RTSP-wide] onStopPublish")
                ToastUtils.showToast(getString(R.string.rtsp_stream_stop, leftPublisher))
            }

            override fun onFpsStatistic(fps: Int, channelName: String) {
                SDKLog.d(TAG, "[RTSP-wide] onFpsStatistic fps=$fps channel=$channelName")
            }

            override fun onVideoBitrate(value: Int, channelName: String) {
                SDKLog.d(TAG, "[RTSP-wide] onVideoBitrate bps=$value channel=$channelName")
            }

            override fun onAudioBitrate(value: Int) {
                SDKLog.d(TAG, "[RTSP-wide] onAudioBitrate bps=$value")
            }

            override fun onPublishSuccess() {
                SDKLog.i(TAG, "[RTSP-wide] onPublishSuccess: publish succeeded (business-layer confirmation)")
            }

            override fun onPublishFailed(errorCode: PublishErrorCode) {
                SDKLog.e(TAG, "[RTSP-wide] onPublishFailed: ${publishErrorDetail(errorCode)}")
                ToastUtils.showToast(getString(R.string.rtsp_stream_failed, leftPublisher))
            }

            override fun onPublishFailed(channelName: String, errorCode: PublishErrorCode) {
                SDKLog.e(TAG, "[RTSP-wide] onPublishFailed channel=$channelName ${publishErrorDetail(errorCode)}")
            }

            override fun onReconnect() {
                SDKLog.w(TAG, "[RTSP-wide] onReconnect")
                ToastUtils.showToast(getString(R.string.rtsp_reconnecting))
            }
        });

        mPublisher2.setOnPublishListener(object :
            IPublishListener{
            override fun onConnecting() {
                SDKLog.i(TAG, "[RTSP-IR] onConnecting")
                ToastUtils.showToast(getString(R.string.rtsp_connecting, rightPublisher))
            }

            override fun onConnected() {
                SDKLog.i(TAG, "[RTSP-IR] onConnected: signaling/connect phase OK")
                ToastUtils.showToast(getString(R.string.rtsp_connected, rightPublisher))
            }

            override fun onConnectedFailed(code: PublishErrorCode) {
                SDKLog.e(TAG, "[RTSP-IR] onConnectedFailed: ${publishErrorDetail(code)}")
                ToastUtils.showToast(getString(R.string.rtsp_connect_failed, rightPublisher))
            }

            override fun onStartPublish() {
                SDKLog.i(TAG, "[RTSP-IR] onStartPublish: pushing stream to server")
                ToastUtils.showToast(getString(R.string.rtsp_stream_start, rightPublisher))
            }

            override fun onStopPublish() {
                SDKLog.i(TAG, "[RTSP-IR] onStopPublish")
                ToastUtils.showToast(getString(R.string.rtsp_stream_stop, rightPublisher))
            }

            override fun onFpsStatistic(fps: Int, channelName: String) {
                SDKLog.d(TAG, "[RTSP-IR] onFpsStatistic fps=$fps channel=$channelName")
            }

            override fun onVideoBitrate(value: Int, channelName: String) {
                SDKLog.d(TAG, "[RTSP-IR] onVideoBitrate bps=$value channel=$channelName")
            }

            override fun onAudioBitrate(value: Int) {
                SDKLog.d(TAG, "[RTSP-IR] onAudioBitrate bps=$value")
            }

            override fun onPublishSuccess() {
                SDKLog.i(TAG, "[RTSP-IR] onPublishSuccess: publish succeeded (business-layer confirmation)")
            }

            override fun onPublishFailed(errorCode: PublishErrorCode) {
                SDKLog.e(TAG, "[RTSP-IR] onPublishFailed: ${publishErrorDetail(errorCode)}")
            }

            override fun onPublishFailed(channelName: String, errorCode: PublishErrorCode) {
                SDKLog.e(TAG, "[RTSP-IR] onPublishFailed channel=$channelName ${publishErrorDetail(errorCode)}")
            }

            override fun onReconnect() {
                SDKLog.w(TAG, "[RTSP-IR] onReconnect")
                ToastUtils.showToast(getString(R.string.rtsp_reconnecting))
            }

        });


    }

    private fun stopPublish() {
        SDKLog.i(TAG, "stopPublish: stopping dual RTSP")
        coroutineScope.launch {
            rtspPublishMutex.withLock {
                mPublisher.stop()
                mPublisher2.stop()
                SDKLog.i(TAG, "stopPublish: both stop() calls returned (native may already be released; re-configure before next start)")
            }
        }

        lifecycleScope.launch(Dispatchers.Main) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                btn_start.text = getString(R.string.debug_start)
            }
        }
    }

    private fun stopPublish(rtspPublisher: RTSPPublisher) {
        coroutineScope.launch {
            rtspPublisher.stop()
        }
        mPublishFlag = false

        lifecycleScope.launch(Dispatchers.Main) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                btn_start.text = getString(R.string.debug_start)
            }
        }
    }

    private fun startPublish() {
        SDKLog.i(TAG, "startPublish: dual RTSP url1=$url url2=$url2 (will re-configure then start in a coroutine)")
        coroutineScope.launch {
            rtspPublishMutex.withLock {
                reconfigureRtspPublishers("beforeStart")
                mPublisher.start()
                mPublisher2.start()
                SDKLog.i(TAG, "startPublish: both start() calls issued; waiting for onConnecting / onPublishSuccess")
            }
        }
        lifecycleScope.launch(Dispatchers.Main) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                btn_start.text = getString(R.string.debug_stop)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mPublishFlag = false

        // 1. Cancel all in-flight coroutines (stop/start/configure, etc.)
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
                SDKLog.e(TAG, "stop publisher1 error: ${e.message}")
            }
            try {
                mPublisher.release()
            } catch (e: Exception) {
                SDKLog.e(TAG, "release publisher1 error: ${e.message}")
            }

            try {
                mPublisher2.stop()
            } catch (e: Exception) {
                SDKLog.e(TAG, "stop publisher2 error: ${e.message}")
            }
            try {
                mPublisher2.release()
            } catch (e: Exception) {
                SDKLog.e(TAG, "release publisher2 error: ${e.message}")
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