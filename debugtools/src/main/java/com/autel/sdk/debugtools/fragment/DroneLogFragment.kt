package com.autel.sdk.debugtools.fragment

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.vmodelx.manager.DeviceLogManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.log.SDKLog.i
import com.autel.drone.sdk.vmodelx.SDKManager
import com.autel.drone.sdk.vmodelx.module.droneLog.bean.*
import com.autel.drone.sdk.vmodelx.module.droneLog.data.*
import com.autel.drone.sdk.vmodelx.module.droneLog.db.DeviceLogDatabaseHelper
import com.autel.drone.sdk.vmodelx.module.droneLog.enums.DeviceLogEnum
import com.autel.drone.sdk.vmodelx.module.droneLog.enums.DeviceLogTypeEnum
import com.autel.sdk.debugtools.databinding.FragmentDroneLogBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DroneLogFragment : AutelFragment()  {

    private lateinit var binding: FragmentDroneLogBinding
    private var queryResponseBen: DeviceLogQueryResponseBen? = null
    private var packResponseBean: DeviceLogPackResponseBean? = null
    private lateinit var helper: DeviceLogDatabaseHelper

    private var droneLog: DeviceSysLog? = null

    private var taskNum: Int = 0

    private val logBuffer = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentDroneLogBinding.inflate(layoutInflater)
        initView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentDroneLogBinding.inflate(inflater)
        initView()
        helper = DeviceLogDatabaseHelper(SDKManager.get().sContext!!)
        var drone = helper.getDeviceLog("12345678901")
        var lsit = helper.getPowerLog("12345678901", 395)
        var module = helper.getModuleLog("12345678901", 395,DeviceLogEnum.RCTRANSMISSION)

        var moduleLsit = helper.getAllModuleLogs("12345678901")
        return binding.root
    }

    companion object {
        private const val TAG = "DroneLog Test"
        private const val LOG_MAX_LENGTH = 10000
    }

    private fun initView() {
        var filePath = SDKManager.get().sContext?.getExternalFilesDir(null)?.absolutePath
        filePath += "/deviceLogTest22"
        if (filePath != null) {
            DeviceLogManager.getInstance().configLogLocalPath(filePath)
        }
        binding.queryLogList.setOnClickListener {
            val type = getDeviceLogType()
            showLog(">> queryLogInfoList($type) start")
            DeviceLogManager.getInstance().queryLogInfoList("123456789012", type, object : CommonCallbacks.CompletionCallbackWithParam<DeviceSysLog?> {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onSuccess(t: DeviceSysLog?) {

                    t?.let {
                        val dataDeviceLog = DeviceLog(it.type, it.sn, emptyList())
                        val powerLogs = mutableListOf<DeviceOncePowerLog>()
                        for (sysPower in it.powerLogList) {
                            val moduleLogs = mutableListOf<DeviceLogModule>()
                            val powerLog = DeviceOncePowerLog(sysPower.type,sysPower.sn,sysPower.index,sysPower.time, emptyList())
                            for (sysmodule in sysPower.moduleList) {
                                val  moduleLog = DeviceLogModule(sysmodule.mode, sysmodule.sn, sysmodule.index, sysmodule.type, sysmodule.time, sysmodule.size)
                                helper.updateModuleLog(moduleLog)
                                moduleLogs.add(moduleLog)
                            }
                            powerLog.moduleList = moduleLogs
                            powerLogs.add(powerLog)

                        }
                        dataDeviceLog.powerLogList = powerLogs
                    }
                    droneLog = t

                    i(TAG, "queryLogInfoList success")
                    val powerList = t?.powerLogList
                    if (powerList.isNullOrEmpty()) {
                        showLog("queryLogInfoList($type) success, but powerLogList is empty")
                        return
                    }
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val sb = StringBuilder("queryLogInfoList($type) success, count=${powerList.size}\n")
                    powerList.forEachIndexed { index, power ->
                        val formattedString = sdf.format(Date(power.time))
                        val modules = power.moduleList.joinToString(",") { it.type.value }
                        sb.append("[$index] seq=${power.index} time=$formattedString modules=[$modules]\n")
                    }
                    i(TAG, "formattedString ${sdf.format(Date(powerList.first().time))}")
                    showLog(sb.toString())
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    i(TAG, "queryLogInfo fair  error" + error.toString())
                    showLog("[FAIL] queryLogInfoList error=$error, msg=$msg")
                }

            })
        }
        binding.logDownLoad.setOnClickListener {
            val type = getDeviceLogType()
            val it = droneLog
            if (it == null || it.powerLogList.isEmpty()) {
                showLog("[FAIL] downloadModuleLog: query the log list first")
                return@setOnClickListener
            }
            showLog(">> downloadModuleLog start")
            val moduleList = arrayListOf<DeviceSysLogModule>()
            it.powerLogList.last().moduleList.firstOrNull()?.let { m -> moduleList.add(m) }
            it.powerLogList.first().moduleList.firstOrNull()?.let { m -> moduleList.add(m) }
            if (moduleList.isEmpty()) {
                showLog("[FAIL] downloadModuleLog: no module found")
                return@setOnClickListener
            }
            DeviceLogManager.getInstance().downloadModuleLog(moduleList,
                object  : CommonCallbacks.DownLoadCallbackCompletionWithProgressAndFile<Double, DeviceSysLogModule> {
                    override fun onProgressUpdate(progress: Double, speed: Double) {
                        i(TAG, "onProgressUpdate $progress  $speed")
                        showLog("downloadModuleLog progress=${(progress * 100).toInt()}%, speed=$speed")
                    }
                    override fun onProgressFileUpdate(file: File?, mode: DeviceSysLogModule?) {
                        i(TAG, " logDownLoad onSuccess  ${file?.name}" )
                        showLog("downloadModuleLog file=${file?.name}")
                    }

                    override fun onSuccess() {
                        i(TAG, " logDownLoad onSuccess ")
                        showLog("downloadModuleLog onSuccess")
                    }

                    override fun onFailure(error: IAutelCode) {
                        i(TAG, " logDownLoad onFailure ")
                        showLog("[FAIL] downloadModuleLog $error")
                    }

                }, type)
        }

        binding.cancel.setOnClickListener {
            showLog(">> cancelDownload")
            DeviceLogManager.getInstance().canceldownload()
        }

        binding.realTime.setOnClickListener {
            showLog(">> queryLogRealTimeInfoList start")
            DeviceLogManager.getInstance().queryLogRealTimeInfoList(object :CommonCallbacks.CompletionCallbackWithParam<DeviceSysLog?>{
                override fun onSuccess(t: DeviceSysLog?) {
                    i(TAG, " queryLogRealTimeInfoList onSuccess ")
                    droneLog = t
                    showLog("queryLogRealTimeInfoList onSuccess, count=${t?.powerLogList?.size ?: 0}")
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    showLog("[FAIL] queryLogRealTimeInfoList error=$error, msg=$msg")
                }
            })
        }



        binding.logDownLoad2.setOnClickListener {
            val type = getDeviceLogType()
            val it = droneLog
            if (it == null || it.powerLogList.isEmpty()) {
                showLog("[FAIL] downloadPowerLog: query the log list first")
                return@setOnClickListener
            }
            showLog(">> downloadPowerLog start")
            val powerLogList = mutableListOf<DeviceSysOncePowerLog>()
            if (it.powerLogList.isNotEmpty()) powerLogList.add(it.powerLogList[0])
            if (it.powerLogList.size > 1) powerLogList.add(it.powerLogList[1])
            if (it.powerLogList.size > 2) powerLogList.add(it.powerLogList.last())
            DeviceLogManager.getInstance().downloadPowerLog(
                powerLogList,
                object  : CommonCallbacks.DownLoadCallbackCompletionWithProgressAndFile<Double, DeviceSysLogModule> {
                    override fun onProgressUpdate(progress: Double, speed: Double) {
                        i(TAG, "onProgressUpdate $progress  $speed")
                        showLog("downloadPowerLog progress=${(progress * 100).toInt()}%, speed=$speed")
                    }
                    override fun onProgressFileUpdate(file: File?, mode: DeviceSysLogModule?) {
                        i(TAG, " logDownLoad onSuccess  ${file?.name}" )
                        showLog("downloadPowerLog file=${file?.name}")
                    }

                    override fun onSuccess() {
                        i(TAG, " logDownLoad onSuccess ")
                        showLog("downloadPowerLog onSuccess")
                    }

                    override fun onFailure(error: IAutelCode) {
                        i(TAG, " logDownLoad onFailure ")
                        showLog("[FAIL] downloadPowerLog $error")
                    }

                }, type)
        }

        binding.btnClearlog.setOnClickListener {
            logBuffer.clear()
            binding.tvResult.text = ""
        }
    }

    /**
     * Read the device log type selected in the spinner (RC / DRONE / NEST / APP),
     * fixing the old hard-coded RC so switching to drone no longer still requests RC.
     */
    private fun getDeviceLogType(): DeviceLogTypeEnum {
        return when (binding.spinner.selectedItem as? String) {
            "RC" -> DeviceLogTypeEnum.RC
            "DRONE" -> DeviceLogTypeEnum.DRONE
            "NEST" -> DeviceLogTypeEnum.NEST
            "APP" -> DeviceLogTypeEnum.APP
            else -> DeviceLogTypeEnum.RC
        }
    }

    /**
     * Echo results to the on-screen tv_result (the old version only logged to logcat).
     */
    @Synchronized
    @SuppressLint("SetTextI18n")
    private fun showLog(msg: String) {
        val ts = timeFormat.format(Date())
        logBuffer.append("$ts $msg\n")
        if (logBuffer.length > LOG_MAX_LENGTH) {
            val trimmed = logBuffer.substring(logBuffer.length - LOG_MAX_LENGTH)
            logBuffer.clear()
            logBuffer.append(trimmed)
        }
        runOnUiThread {
            if (!isAdded) return@runOnUiThread
            binding.tvResult.text = logBuffer.toString()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnUiThread(action: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
    }
}