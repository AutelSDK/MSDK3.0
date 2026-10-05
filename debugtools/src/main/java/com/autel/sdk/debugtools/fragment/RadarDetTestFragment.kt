package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RadarPropertyKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.radar.bean.RadarDetListBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.radar.enums.RadarDetReportDirFlag
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.sdk.debugtools.databinding.FragmentRadarDetTestBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * MDE radar detection data report test Fragment
 *
 * Features:
 * 1. Set radar direction switches per model (bitmask bit0-bit5: front/rear/left/right/up/down)
 * 2. Listen to the /radars/detlist topic for RadarDetsList data
 */
class RadarDetTestFragment : AutelFragment() {

    private lateinit var binding: FragmentRadarDetTestBinding
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logBuilder = StringBuilder()
    private var frameCount = 0
    private var isListening = false

    private data class DroneModel(
        val name: String,
        val supportedDirs: Set<RadarDetReportDirFlag>,
        val defaultDirs: Set<RadarDetReportDirFlag>
    )

    private val models = listOf(
        DroneModel(
            "MDE (Front only)",
            setOf(RadarDetReportDirFlag.FRONT),
            setOf(RadarDetReportDirFlag.FRONT)
        ),
        DroneModel(
            "EVO Max (Front/Rear/Left/Right/Down)",
            setOf(RadarDetReportDirFlag.FRONT, RadarDetReportDirFlag.REAR,
                RadarDetReportDirFlag.LEFT, RadarDetReportDirFlag.RIGHT, RadarDetReportDirFlag.DOWN),
            setOf(RadarDetReportDirFlag.FRONT)
        ),
        DroneModel(
            "Custom (All directions)",
            RadarDetReportDirFlag.values().filter { it != RadarDetReportDirFlag.NONE }.toSet(),
            setOf(RadarDetReportDirFlag.FRONT)
        )
    )

    private lateinit var dirCheckboxes: Map<RadarDetReportDirFlag, CheckBox>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentRadarDetTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dirCheckboxes = mapOf(
            RadarDetReportDirFlag.FRONT to binding.cbFront,
            RadarDetReportDirFlag.REAR to binding.cbRear,
            RadarDetReportDirFlag.LEFT to binding.cbLeft,
            RadarDetReportDirFlag.RIGHT to binding.cbRight,
            RadarDetReportDirFlag.UP to binding.cbUp,
            RadarDetReportDirFlag.DOWN to binding.cbDown
        )
        setupModelSpinner()
        setupButtons()
    }

    private fun setupModelSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            models.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerModel.adapter = adapter
        binding.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                applyModelConfig(models[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        applyModelConfig(models[0])
    }

    private fun applyModelConfig(model: DroneModel) {
        for ((flag, cb) in dirCheckboxes) {
            val supported = flag in model.supportedDirs
            cb.isEnabled = supported
            cb.isChecked = supported && flag in model.defaultDirs
            cb.alpha = if (supported) 1.0f else 0.4f
        }
    }

    private fun setupButtons() {
        binding.btnSetSwitch.setOnClickListener { doSetSwitch() }
        binding.btnGetSwitch.setOnClickListener { doGetSwitch() }
        binding.btnStartListen.setOnClickListener { doStartListen() }
        binding.btnStopListen.setOnClickListener { doStopListen() }
        binding.tvClearLog.setOnClickListener { clearLog() }
    }

    private fun getDevice(): IAutelDroneDevice? {
        val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
        if (device == null) appendLog("!! No device connected")
        return device
    }

    private fun buildBitmask(): Int {
        var mask = 0
        for ((flag, cb) in dirCheckboxes) {
            if (cb.isChecked && cb.isEnabled) mask = mask or flag.bit
        }
        return mask
    }

    private fun doSetSwitch() {
        val device = getDevice() ?: return
        val mask = buildBitmask()
        val dirs = RadarDetReportDirFlag.parse(mask).joinToString("+") { it.name }
        appendLog(">> Set switch: 0x${Integer.toHexString(mask)} ($dirs)")

        val key = KeyTools.createKey(RadarPropertyKey.RADAR_DET_REPORT_SWITCH)
        device.getKeyManager().setValue(key, mask,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    appendLog("<< Set switch SUCCESS")
                }
                override fun onFailure(error: IAutelCode, msg: String?) {
                    appendLog("<< Set switch FAILED: code=${error.code}, msg=$msg")
                }
            })
    }

    private fun doGetSwitch() {
        val device = getDevice() ?: return
        appendLog(">> Get switch ...")

        val key = KeyTools.createKey(RadarPropertyKey.RADAR_DET_REPORT_SWITCH)
        device.getKeyManager().getValue(key,
            object : CommonCallbacks.CompletionCallbackWithParam<Int> {
                override fun onSuccess(t: Int?) {
                    val v = t ?: 0
                    val dirs = RadarDetReportDirFlag.parse(v).joinToString("+") { it.name }
                    appendLog("<< Switch = 0x${Integer.toHexString(v)} ($dirs)")
                    safePost {
                        binding.tvCurrentSwitch.text = "Current: 0x${Integer.toHexString(v)} ($dirs)"
                    }
                }
                override fun onFailure(error: IAutelCode, msg: String?) {
                    appendLog("<< Get switch FAILED: code=${error.code}, msg=$msg")
                }
            })
    }

    private fun doStartListen() {
        val device = getDevice() ?: return
        appendLog(">> Start listening RadarDetList ...")
        frameCount = 0

        val key = KeyTools.createKey(RadarPropertyKey.KeyRadarDetListInfo)
        device.getKeyManager().listen(key,
            object : CommonCallbacks.KeyListener<RadarDetListBean> {
                override fun onValueChange(oldValue: RadarDetListBean?, newValue: RadarDetListBean) {
                    frameCount++
                    val dirName = radarIdToName(newValue.radarId)
                    appendLog(
                        "[#$frameCount] radar=$dirName(${newValue.radarId}) " +
                        "dets=${newValue.numDets} frame=${newValue.frameCounter} " +
                        "ver=${newValue.version}"
                    )
                    if (newValue.dets.isNotEmpty()) {
                        val first = newValue.dets[0]
                        appendLog(
                            "  det[0]: range=${first.range}m az=${first.azimuth}° " +
                            "el=${first.elevation}° doppler=${first.doppler}m/s rcs=${first.rcs}"
                        )
                    }
                    safePost {
                        binding.tvFrameCount.text = "Frames: $frameCount"
                    }
                }
            })

        isListening = true
        binding.btnStartListen.isEnabled = false
        binding.btnStopListen.isEnabled = true
    }

    private fun doStopListen() {
        val device = getDevice() ?: return
        appendLog(">> Stop listening")

        val key = KeyTools.createKey(RadarPropertyKey.KeyRadarDetListInfo)
        device.getKeyManager().cancelListen(key)

        isListening = false
        binding.btnStartListen.isEnabled = true
        binding.btnStopListen.isEnabled = false
    }

    override fun onDestroyView() {
        if (isListening) {
            try {
                val device = DeviceManager.getDeviceManager().getFirstDroneDevice()
                val key = KeyTools.createKey(RadarPropertyKey.KeyRadarDetListInfo)
                device?.getKeyManager()?.cancelListen(key)
            } catch (_: Exception) {}
        }
        super.onDestroyView()
    }

    private fun radarIdToName(id: Int): String = when (id) {
        0 -> "Front"
        1 -> "Rear"
        2 -> "Left"
        3 -> "Right"
        4 -> "Up"
        5 -> "Down"
        else -> "Unknown"
    }

    private fun appendLog(msg: String) {
        val ts = timeFormat.format(System.currentTimeMillis())
        logBuilder.append("[$ts] $msg\n")
        safePost {
            binding.tvLog.text = logBuilder.toString()
            binding.scrollLog.post { binding.scrollLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun clearLog() {
        logBuilder.clear()
        frameCount = 0
        binding.tvLog.text = ""
        binding.tvFrameCount.text = "Frames: 0"
    }

}
