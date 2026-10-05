package com.autel.sdk.debugtools.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.bean.MissionRecordWaypointBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.camera.bean.PhotoFileInfoBean
import com.autel.drone.sdk.vmodelx.module.fileservice.FileConstants
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.sdk.debugtools.databinding.FragmentMissionRecordBinding
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mission record Sample
 *
 */
@SuppressLint("SetTextI18n")
class MissionRecordFragment : AutelFragment() {
    companion object {
        private const val TAG = "MissionRecordFragment"
        private const val MSG_DOWNLOAD = 1000
    }

    private var binding: FragmentMissionRecordBinding? = null

    override fun handleMessage(msg: Message): Boolean {
        if(msg.what == MSG_DOWNLOAD) {
            if(recordBeanList.isNotEmpty()) {
                downloadPhoto(recordBeanList.removeAt(0))
            }
        }
        return true
    }

    private val recordBeanList = mutableListOf<MissionRecordWaypointBean>()
    private val isDownloading = AtomicBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMissionRecordBinding.inflate(inflater)
        initView()
        startListenerPhoto()
        return binding?.root
    }

    private fun switchRecordMode(open: Boolean) {
        val keyManager = getCurrentDroneDevice()?.getKeyManager()
        val key = KeyTools.createKey(CameraKey.KeyCameraMissionRecordEnable)
        keyManager?.setValue(key, open, object : CommonCallbacks.CompletionCallback {
            override fun onFailure(code: IAutelCode, msg: String?) {
                SDKLog.e(TAG, "switchRecordMode open=$open failed, code: $code, msg: $msg")
                updateInfo("switchRecordMode fail, open=$open code: $code, msg: $msg ")
            }

            override fun onSuccess() {
                SDKLog.i(TAG, "switchRecordMode open=$open success ")
                updateInfo("switchRecordMode success, open=$open  ")
            }
        })
    }

    private fun takePhoto() {
        val keyManager = getCurrentDroneDevice()?.getKeyManager()
        val key = KeyTools.createKey(CameraKey.KeyStartTakePhoto)
        binding?.textInfo?.text = ""
        keyManager?.performAction(key, null, object : CommonCallbacks.CompletionCallbackWithParam<Void> {
            override fun onFailure(error: IAutelCode, msg: String?) {
                SDKLog.e(TAG, "takePhoto failed, error: $error, msg: $msg")
                updateInfo("takePhoto fail, code: $error, msg: $msg ")
            }

            override fun onSuccess(t: Void?) {
                SDKLog.i(TAG, "takePhoto success ")
                updateInfo("takePhoto success")
            }
        })
    }

    /**
     * single controlled drone or first controlled drone
     * also can upload /control one by one for all drones
     */
    private fun getCurrentDroneDevice(): IAutelDroneDevice? {
        return DeviceManager.getMultiDeviceOperator().getControlledDroneList().firstOrNull()
    }

    private fun isConnected(): Boolean {
        return getCurrentDroneDevice()?.isConnected() == true
    }

    private fun initView() {
        binding?.enterRecordMode?.setOnClickListener {
            updateInfo("")
            updateStateInfo("")
            if (!checkDeviceReady()) {
                return@setOnClickListener
            }
            switchRecordMode(true)
        }

        binding?.btnTakePhoto?.setOnClickListener {
            updateInfo("")
            if (!checkDeviceReady()) {
                return@setOnClickListener
            }
            takePhoto()
        }

        binding?.exitRecordMode?.setOnClickListener {
            updateInfo("")
            if (!checkDeviceReady()) {
                return@setOnClickListener
            }
            switchRecordMode(false)
        }

    }

    private fun checkDeviceReady(): Boolean {
        if (!isConnected()) {
            ToastUtils.showToast("Drone disconnected!")
            return false
        }
        return true
    }

    private fun startListenerPhoto() {
        DeviceManager.getDeviceManager().addDroneDevicesListener(recordInfoKey, recordInfoObserver)
        DeviceManager.getDeviceManager().addDroneDevicesListener(takePhoto, cameraPhotoListener)
    }

    private fun stopListenerPhoto() {
        DeviceManager.getDeviceManager().removeDroneDevicesListener(recordInfoKey, recordInfoObserver)
        DeviceManager.getDeviceManager().removeDroneDevicesListener(takePhoto, cameraPhotoListener)
    }

    /**
     * Mission-record capture reporting
     */
    private val recordInfoKey = KeyTools.createKey(CameraKey.KeyMissionRecordWaypoint)
    private val recordInfoObserver = object : DeviceManager.KeyManagerListenerCallBack {
        override fun onListenerValueChanged(value: DeviceManager.DeviceListenerResult<*>) {
            (value.result as? MissionRecordWaypointBean)?.let {
                val droneDevice = value.drone
                val recordBean = value.result as MissionRecordWaypointBean
                SDKLog.i(TAG, "droneDevice= ${droneDevice.toSampleString()}, recordBean: $recordBean")
                updateStateInfo("droneDevice= ${droneDevice.toSampleString()}, recordBean: $recordBean")
                if(recordBean.cameraId == 0){
                    recordBeanList.add(recordBean)
                    handler.sendEmptyMessage(MSG_DOWNLOAD)
                }
            }
        }
    }

    /**
     * Capture reporting
     */
    private val takePhoto = KeyTools.createKey(CameraKey.KeyPhotoFileInfo)
    private val cameraPhotoListener = object : DeviceManager.KeyManagerListenerCallBack {
        override fun onListenerValueChanged(value: DeviceManager.DeviceListenerResult<*>) {
            (value.result as? PhotoFileInfoBean)?.let {
                val droneDevice = value.drone
                val photoBean = value.result
                SDKLog.i(TAG, "droneDevice= ${droneDevice.toSampleString()}, photoBean: $photoBean")
                updateStateInfo("droneDevice= ${droneDevice.toSampleString()}, photoBean: $photoBean")
            }
        }
    }

    private fun downloadPhoto(photoBean: MissionRecordWaypointBean) {
        if(isDownloading.get()){
            return
        }
        isDownloading.set(true)
        val sourcePath = getCurrentDroneDevice()?.getAlbumBaseUrl() + FileConstants.PREVIEW_PATH + photoBean.filePath
        val photoName = photoBean.filePath?.substring((photoBean.filePath?.lastIndexOf("/") ?: 0 ) + 1)
        val targetPath = context?.getExternalFilesDir(null)?.absolutePath + "/" + photoName
        SDKLog.i(TAG,"downloadPhoto sourcePath: $sourcePath, targetPath: $targetPath")
        getCurrentDroneDevice()?.getAlbumManager()?.downloadMediaFile(sourcePath, targetPath, object : CommonCallbacks.DownLoadCallbackWithProgress<Double> {
            override fun onFailure(error: IAutelCode) {
                SDKLog.i(TAG,"downloadPhoto onFailure")
                isDownloading.set(false)
                handler.sendEmptyMessage(MSG_DOWNLOAD)
            }

            override fun onSuccess(file: File?) {
                SDKLog.i(TAG,"downloadPhoto onSuccess")
                isDownloading.set(false)
                handler.sendEmptyMessage(MSG_DOWNLOAD)
            }

            override fun onProgressUpdate(progress: Double, speed: Double) {
                SDKLog.i(TAG,"downloadPhoto progress$progress")
            }
        })
    }

    private fun updateInfo(info: String) {
        handler.post { binding?.resultText?.text =  info }
    }

    private fun updateStateInfo(info: String) {
        handler.post { binding?.textInfo?.text = binding?.textInfo?.text.toString() +"\n"+ info }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListenerPhoto()
        switchRecordMode(false)
    }

    private fun enableOrDisableBtn(btn: Button?, enable: Boolean) {
        handler.post { btn?.isEnabled = enable }
    }
}

