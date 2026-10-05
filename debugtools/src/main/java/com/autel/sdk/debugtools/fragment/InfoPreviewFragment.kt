package com.autel.sdk.debugtools.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autel.drone.sdk.LogCfg
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.statistics.SDKRunTimeInfoManager
import com.autel.drone.sdk.vmodelx.SDKManager
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.KeyManager
import com.autel.drone.sdk.vmodelx.manager.RCLocationManager
import com.autel.drone.sdk.vmodelx.manager.RCLocationManager.OnGpsChangeListener
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.bean.RCGpsInfoMsgBean
import com.autel.drone.sdk.vmodelx.utils.ThreadStackPrinter
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentPreviewInfoBinding
import com.robotics.sdk.ros.RosBridgeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SDK runtime message display
 */
@SuppressLint("SetTextI18n")
class InfoPreviewFragment : AutelFragment() {
    companion object {
        private const val TAG = "InfoPreviewFragment"
        private const val DELAY_GAP = 200L
        const val MSG_REFRESH = 1000
        private const val LOG_MAX_LENGTH = 10000
    }

    private lateinit var binding: FragmentPreviewInfoBinding
    private val runtimeBuffer = StringBuilder()
    private val cacheBuffer = StringBuilder()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == MSG_REFRESH) {
            initDroneView()
        }
        return true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPreviewInfoBinding.inflate(inflater)
        LogCfg.openRunTimeInfoCollect(true)
        initView()
        intFirst()
        initDroneView()
        initCollectListener()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        uiHandler.removeCallbacksAndMessages(null)
        LogCfg.openRunTimeInfoCollect(false)
        SDKRunTimeInfoManager.removeListener()
    }

    private fun initCollectListener() {
        SDKRunTimeInfoManager.setListener(object : SDKRunTimeInfoManager.RuntimeInfoListener {
            override fun onRunInfoUpdate() {
                appendRuntimeLine(SDKRunTimeInfoManager.droneReceiveMsg)
            }
        })
    }

    /**
     * Append one runtime line to the bottom-left panel and scroll to bottom.
     */
    private fun appendRuntimeLine(line: String) {
        val ts = timeFormat.format(Date())
        runtimeBuffer.append("$ts  $line\n")
        trimBuffer(runtimeBuffer)
        uiHandler.post {
            if (!::binding.isInitialized) {
                return@post
            }
            binding.textInfo.text = runtimeBuffer.toString()
            binding.scrollRuntime.post {
                binding.scrollRuntime.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    /**
     * SDK runtime snapshot shown after Reset ROS, for tests to verify the panel refreshes.
     */
    private fun buildSdkRuntimeSnapshot(): String {
        val remote = DeviceManager.getDeviceManager().getLocalRemoteDevice()
        val drones = DeviceManager.getDeviceManager().getDroneDevices()
        val lastMsg = SDKRunTimeInfoManager.droneReceiveMsg.ifBlank { "none" }
        return "Reset ROS done | SDK=${SDKManager.get().getSDKVersion()}" +
            " RC=${remote.getVersion()}" +
            " SN=${remote.getSnImmediately() ?: "unknown"}" +
            " drones=${drones.size}" +
            " lastRecv=$lastMsg"
    }

    private fun trimBuffer(buf: StringBuilder) {
        if (buf.length > LOG_MAX_LENGTH) {
            val trimmed = buf.substring(buf.length - LOG_MAX_LENGTH)
            buf.clear()
            buf.append(trimmed)
        }
    }

    private fun refreshCacheInfo() {
        cacheBuffer.clear()
        val devices = DeviceManager.getDeviceManager().getDroneDevices()
        if (devices.isEmpty()) {
            cacheBuffer.append("No drone connected\n")
        } else {
            devices.forEach { device ->
                val sn = device.getDroneSn() ?: "unknown"
                val id = device.getDeviceNumber()
                cacheBuffer.append("══ Drone[$id] SN:$sn ══\n")
                val entries = (device.getKeyManager() as? KeyManager)?.printKeyValue()
                if (entries.isNullOrEmpty()) {
                    cacheBuffer.append("  (empty)\n")
                } else {
                    entries.forEach { (key, value) ->
                        cacheBuffer.append("  [$key] => $value\n")
                    }
                }
            }
        }
        uiHandler.post {
            binding.droneCacheInfo.text = cacheBuffer.toString()
            binding.scrollCache.post {
                binding.scrollCache.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun intFirst() {
        binding.apply {
            remoterVersion.text =
                "RCVersion :${DeviceManager.getDeviceManager().getLocalRemoteDevice().getVersion()}"
            sdkVersion.text = "SDKVersion :${SDKManager.get().getSDKVersion()}"
            matchMode.text = "MatchMode :${DeviceManager.getDeviceManager().getModemMode()}"
            rSn.text = "RCSn :${
                DeviceManager.getDeviceManager().getLocalRemoteDevice()
                    .getSnImmediately() ?: "unknown"
            }"
            rType.text =
                "RCType :${DeviceManager.getDeviceManager().getLocalRemoteDevice().getRcType()}"
        }
    }

    var adapter: DroneAdapter? = null

    @SuppressLint("NotifyDataSetChanged")
    fun initDroneView() {
        val drones = DeviceManager.getDeviceManager().getDroneDevices().toMutableList()
        if (adapter == null) {
            adapter = DroneAdapter(drones.toMutableList())
            binding.droneList.adapter = adapter
            binding.droneList.setLayoutManager(
                LinearLayoutManager(
                    requireContext(),
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
            )
        } else {
            adapter?.setData(drones.toList())
        }

        adapter?.notifyDataSetChanged()
        handler.sendEmptyMessageDelayed(MSG_REFRESH, DELAY_GAP)
    }

    private fun initView() {
        binding.commonLogEnableView.isChecked = LogCfg.DEBUG
        binding.commonLogEnableView.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) LogCfg.openDebug() else LogCfg.closeDebug()
        }

        binding.print.setOnClickListener {
            refreshCacheInfo()
        }

        binding.rest.setOnClickListener {
            SDKLog.i(TAG, "resetROS click")
            appendRuntimeLine("Reset ROS start")
            showToast(getString(R.string.debug_preview_reset_ros_start))
            RosBridgeManager.get().resetROS {
                uiHandler.post {
                    if (!isAdded) {
                        SDKLog.w(TAG, "resetROS finish but fragment detached")
                        return@post
                    }
                    SDKLog.i(TAG, "resetROS finish")
                    intFirst()
                    refreshCacheInfo()
                    appendRuntimeLine(buildSdkRuntimeSnapshot())
                    showToast(getString(R.string.debug_preview_reset_ros_done))
                }
            }
        }

        binding.receiveLogEnableView.isChecked = LogCfg.DEBUG_RECEIVE
        binding.receiveLogEnableView.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                ThreadStackPrinter.printAllThreadStacks()
                LogCfg.openReceiveDebug()
            } else {
                LogCfg.closeReceiveDebug()
            }
        }

        binding.btnClearRuntime.setOnClickListener {
            runtimeBuffer.clear()
            binding.textInfo.text = ""
        }
        binding.btnClearCache.setOnClickListener {
            cacheBuffer.clear()
            binding.droneCacheInfo.text = ""
        }

        binding.btnRcPgs.setOnClickListener {
            cacheBuffer.clear()
            binding.droneCacheInfo.text = ""
            cacheBuffer.append(RCLocationManager.get().getLocationStatus())
            binding.droneCacheInfo.text = cacheBuffer.toString()
            bindGpsListener()
        }

        refreshGpsCount()
    }


    private fun refreshGpsCount(){
        binding.rcGpsText.text = "satellite=${RCLocationManager.get().satelliteCount}, useFix=${RCLocationManager.get().satelliteUsedInFix}"
        handler.postDelayed({refreshGpsCount()}, 2000)
    }

    private var listenGps = false
    private fun bindGpsListener() {
        listenGps = !listenGps
        if(listenGps){
            cacheBuffer.append("\n bindGpsListener")
            binding.droneCacheInfo.text = cacheBuffer.toString()
            RCLocationManager.get().addLocationChangeListener(gpsListener)
        } else {
            cacheBuffer.append("\n unbindGpsListener")
            binding.droneCacheInfo.text = cacheBuffer.toString()
            RCLocationManager.get().removeLocationChangeListener(gpsListener)
        }
    }

    private val gpsListener =object:OnGpsChangeListener{
        override fun onChange(gpsInfo: RCGpsInfoMsgBean) {
            cacheBuffer.clear()
            cacheBuffer.append(RCLocationManager.get().getLocationStatus())
            cacheBuffer.append(gpsInfo.toString())
            uiHandler.post {
                binding.droneCacheInfo.text = cacheBuffer.toString()
            }
        }
    }
}


@SuppressLint("DefaultLocale", "SetTextI18n")
class DroneAdapter(private val droneList: MutableList<IAutelDroneDevice>) :
    RecyclerView.Adapter<DroneAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.preview_drone_list_item, parent, false)
        return ViewHolder(view)
    }

    fun setData(list: List<IAutelDroneDevice>) {
        this.droneList.clear()
        this.droneList.addAll(list)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val droneDevice = droneList[position]
        holder.id.text = "id :  ${droneDevice.getDeviceNumber()}"
        holder.name.text = "name : ${droneDevice.getName()}"
        holder.sn.text = "sn : ${droneDevice.getDroneSn()}"
        holder.version.text = "ver : ${droneDevice.getDroneVersion()}"
        holder.connect.text = "connect : ${droneDevice.isConnected()}"
        holder.type.text = "type : ${droneDevice.getDroneType().value}"
        holder.active.text = "ctrlRight: ${droneDevice.hasControlRight()}"
        holder.gimbal.text = "gimbal: ${droneDevice.getGimbalDeviceType().value}"
        holder.lens.text = "lens: ${droneDevice.getCameraAbilitySetManger().getLensList()}"
    }

    override fun getItemCount(): Int {
        return droneList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var id: TextView = itemView.findViewById(R.id.textViewId)
        var name: TextView = itemView.findViewById(R.id.textViewName)
        var sn: TextView = itemView.findViewById(R.id.textViewSn)
        var version: TextView = itemView.findViewById(R.id.textViewVersion)
        var type: TextView = itemView.findViewById(R.id.textViewType)
        var connect: TextView = itemView.findViewById(R.id.textViewConnect)
        var active: TextView = itemView.findViewById(R.id.textViewActive)
        var gimbal: TextView = itemView.findViewById(R.id.textViewGimbal)
        var lens: TextView = itemView.findViewById(R.id.textViewLens)
    }


}