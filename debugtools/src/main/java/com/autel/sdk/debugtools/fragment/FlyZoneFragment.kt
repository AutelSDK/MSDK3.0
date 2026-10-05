package com.autel.sdk.debugtools.fragment

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.pbprotocol.constants.FightParamKey
import com.autel.drone.sdk.vmodelx.device.statenew.data.LocationCoordinate2D
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.FlyZoneManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.converter.SingleValueConverter
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKeyInfo
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.module.flyzone.bean.FlyZoneAuthInformation
import com.autel.drone.sdk.vmodelx.module.flyzone.bean.FlyZoneInformation
import com.autel.drone.sdk.vmodelx.module.flyzone.bean.FlyableZoneInfo
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.sdk.debugtools.KeyValueDialogUtil
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.UriConvert
import com.autel.sdk.debugtools.databinding.FragmentFlyZoneBinding
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class FlyZoneFragment : AutelFragment() {

    private lateinit var binding: FragmentFlyZoneBinding
    private val logMessage = StringBuilder()
    companion object {
        const val TAG = "FlyZoneFragment"
        private const val LISTEN_RECORD_MAX_LENGTH = 6000
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it == null || it == Uri.EMPTY) {
            binding.tvResult.text = appendLogMessageRecord("File is null")
            return@registerForActivityResult
        }

        val path = UriConvert.getPhotoPathFromContentUri(this.requireContext(), it)
        binding.tvResult.text = appendLogMessageRecord("File Path: $path")
        if (TextUtils.isEmpty(path) || File(path!!).exists().not()) return@registerForActivityResult
        FlyZoneManager.get().importFlySafeDynamicDatabaseToMSDK(path, object: CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneInformation>> {
            override fun onSuccess(t: List<FlyZoneInformation>?) {
                val result = Gson().toJson(t)
                Log.d(TAG, "onSuccess: $result")
                binding.tvResult.text = appendLogMessageRecord(result)
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                Log.d(TAG, "onFailure: $error $msg")
                binding.tvResult.text = appendLogMessageRecord("onFailure: $error $msg")
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentFlyZoneBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        FlyZoneManager.get().getFlySafeDynamicDataVersion().let {
            binding.tvResult.text = appendLogMessageRecord("FlySafeDynamicDataVersion: $it")
        }

        FlyZoneManager.get().pushFlySafeDynamicDatabaseToAircraft(object: CommonCallbacks.UpLoadFileCallbackWithProgress<Int>{
            override fun onStartUploading() {
                binding.tvResult.text = appendLogMessageRecord("onStartUploading")
            }

            override fun onSuccess() {
                binding.tvResult.text = appendLogMessageRecord("onSuccess")
            }

            override fun onFailure(error: IAutelCode) {
                binding.tvResult.text = appendLogMessageRecord("onFailure $error")
            }

            override fun onProgressUpdate(progress: Int) {
                binding.tvResult.text = appendLogMessageRecord("onProgressUpdate $progress")
            }
        })

        binding.tvResult.movementMethod = ScrollingMovementMethod.getInstance()

        binding.querNfz.setOnClickListener {
            val location = Gson().toJson(LocationCoordinate2D(22.5955975, 113.9977696))
            KeyValueDialogUtil.showInputDialog(
                requireActivity(), getString(R.string.debug_set_request_parameters), location, "", false
            ) {_, it ->
                binding.tvResult.text = appendLogMessageRecord(it)
                FlyZoneManager.get().getFlyZonesInSurroundingArea(Gson().fromJson(it, LocationCoordinate2D::class.java), object: CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneInformation>?>{
                    override fun onSuccess(t: List<FlyZoneInformation>?) {
                        val result = Gson().toJson(t)
                        Log.d(TAG, "onSuccess: $result")
                        binding.tvResult.text = appendLogMessageRecord(result)
                    }

                    override fun onFailure(error: IAutelCode, msg: String?) {
                        Log.d(TAG, "onFailure: $error $msg")
                        binding.tvResult.text = appendLogMessageRecord("onFailure: $error $msg")
                    }
                })
            }
        }

        binding.downloadAuthZone.setOnClickListener {
            val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
            if (device == null || device.getDroneSn()?.isEmpty() == true) {
                return@setOnClickListener
            }
            FlyZoneManager.get().downloadAuthFlyZoneFromServer(
                device.getDroneSn()!!,
                LocationCoordinate2D(0.0, 0.0),
                object: CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneAuthInformation>?>{
                override fun onSuccess(t: List<FlyZoneAuthInformation>?) {
                    val result = Gson().toJson(t)
                    Log.d(TAG, "onSuccess: $result")
                    binding.tvResult.text = appendLogMessageRecord(result)
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    Log.d(TAG, "onFailure: $error $msg")
                    binding.tvResult.text = appendLogMessageRecord("onFailure: $error $msg")
                }
            })
        }

        binding.uploadAuthZone.setOnClickListener {
            val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
            device ?: return@setOnClickListener
            FlyZoneManager.get().pushAuthFlyZoneToAircraft(device, object: CommonCallbacks.UpLoadFileCallbackWithProgress<Int>{
                override fun onStartUploading() {
                    binding.tvResult.text = appendLogMessageRecord("onStartUploading")
                }

                override fun onProgressUpdate(progress: Int) {
                    binding.tvResult.text = appendLogMessageRecord("onProgressUpdate $progress")
                }

                override fun onSuccess() {
                    Log.d(TAG, "onSuccess")
                    binding.tvResult.text = appendLogMessageRecord("onSuccess")
                }

                override fun onFailure(error: IAutelCode) {
                    Log.d(TAG, "onFailure: $error")
                    binding.tvResult.text = appendLogMessageRecord("onFailure: $error")
                }
            })
        }

        binding.deleteAuthZone.setOnClickListener {
            val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
            device ?: return@setOnClickListener
            FlyZoneManager.get().deleteAuthFlyZoneFromAircraft(device, object: CommonCallbacks.CompletionCallback{
                override fun onSuccess() {
                    Log.d(TAG, "onSuccess")
                    binding.tvResult.text = appendLogMessageRecord("onSuccess")
                }

                override fun onFailure(code: IAutelCode, msg: String?) {
                    Log.d(TAG, "onFailure: $code $msg")
                    binding.tvResult.text = appendLogMessageRecord("onFailure: $code $msg")
                }
            })
        }

        binding.getAuthZone.setOnClickListener {
            val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
            if (device == null || device.getDroneSn()?.isEmpty() == true) {
                return@setOnClickListener
            }
            FlyZoneManager.get().getAuthFlyZone(device.getDroneSn()!!, object: CommonCallbacks.CompletionCallbackWithParam<List<FlyZoneAuthInformation>?>{
                override fun onSuccess(t: List<FlyZoneAuthInformation>?) {
                    val result = Gson().toJson(t)
                    Log.d(TAG, "onSuccess: $result")
                    binding.tvResult.text = appendLogMessageRecord(result)
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    Log.d(TAG, "onFailure: $error $msg")
                    binding.tvResult.text = appendLogMessageRecord("onFailure: $error $msg")
                }
            })
        }

        binding.importGeoFile.setOnClickListener {
            launcher.launch(arrayOf("*/*"))
        }

        binding.pushGeoFile.setOnClickListener {
            FlyZoneManager.get().pushFlySafeDynamicDatabaseToAircraft(object: CommonCallbacks.UpLoadFileCallbackWithProgress<Int>{
                override fun onStartUploading() {
                    binding.tvResult.text = appendLogMessageRecord("onStartUploading")
                }

                override fun onSuccess() {
                    binding.tvResult.text = appendLogMessageRecord("onSuccess")
                }

                override fun onFailure(error: IAutelCode) {
                    binding.tvResult.text = appendLogMessageRecord("onFailure: $error")
                }

                override fun onProgressUpdate(progress: Int) {
                    binding.tvResult.text = appendLogMessageRecord("onProgressUpdate: $progress")
                }
            })
        }

        binding.getCountryCode.setOnClickListener {
            val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
            device ?: return@setOnClickListener
            if (!device.isConnected()) return@setOnClickListener
            FlyZoneManager.get().getAircraftFlyZoneCountryCode(device.getDroneSn()!!, object: CommonCallbacks.CompletionCallbackWithParam<List<FlyableZoneInfo>> {

                override fun onSuccess(t: List<FlyableZoneInfo>?) {
                    val result = Gson().toJson(t)
                    Log.d(TAG, "onSuccess: $result")
                    binding.tvResult.text = appendLogMessageRecord(result)
                }

                override fun onFailure(error: IAutelCode,msg: String?) {
                    Log.d(TAG, "onFailure: $error $msg")
                    binding.tvResult.text = appendLogMessageRecord("onFailure: $error $msg")
                }
            })
        }

        binding.setLocation.setOnClickListener {
            val locationStr = Gson().toJson(LocationCoordinate2D(48.85858856494826, 2.3425213304779504))
            KeyValueDialogUtil.showInputDialog(
                requireActivity(), getString(R.string.debug_set_request_parameters), locationStr, "", false
            ) { _, it->
                val location = try {
                    Gson().fromJson(it, LocationCoordinate2D::class.java)
                } catch (e: Exception) {
                    null
                }
                location?.let { loc ->
                    setFloatValue(FightParamKey.SIM_LAT, loc.latitude.toFloat())
                    setFloatValue(FightParamKey.SIM_LON, loc.longitude.toFloat())
                }
            }
        }

        binding.clearLog.setOnClickListener {
            logMessage.clear()
            binding.tvResult.text = ""
        }
    }

    private fun appendLogMessageRecord(appendStr: String?): String {
        val curTime = SimpleDateFormat("HH:mm:ss").format(Date())
        logMessage.append(curTime)
            .append(":")
            .append(appendStr)
            .append("\n")

        //length limit
        var result = logMessage.toString()
        if (result.length > LISTEN_RECORD_MAX_LENGTH) {
            result = result.substring(result.length - LISTEN_RECORD_MAX_LENGTH)
        }
        return result
    }

    private fun setFloatValue(keyName: String, value: Float) {
        val keyInfo: AutelKeyInfo<Float> = AutelKeyInfo(0, keyName, SingleValueConverter.FloatConverter).canGet(true).canSet(true)
        val autelKey = KeyTools.createKey(keyInfo)
        DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager()?.setValue(autelKey,
            value, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                }
                override fun onFailure(code: IAutelCode, msg: String?) {
                    ToastUtils.showToast("${code.code}$msg")
                }
            })
    }
}