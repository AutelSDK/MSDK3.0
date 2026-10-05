package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autel.drone.sdk.libbase.error.AutelStatusCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AMeshKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentMeshTopologyBinding
import com.robotics.sdk.ros.data.MeshTopNetBean

class MeshTopologyFragment : AutelFragment() {

    private lateinit var binding: FragmentMeshTopologyBinding
    private val deviceList = mutableListOf<Int>()
    private val topologyDataMap = mutableMapOf<Int, MeshTopNetBean>()
    private lateinit var adapter: MeshTopologyAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMeshTopologyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        adapter = MeshTopologyAdapter(topologyDataMap)
        binding.rvMeshTopology.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMeshTopology.adapter = adapter

        // Setup refresh button
        binding.btnRefresh.setOnClickListener {
            refreshMeshTopology()
        }

        // Initial fetch
        refreshMeshTopology()
    }

    private fun refreshMeshTopology() {
        binding.btnRefresh.isEnabled = false
        binding.tvStatus.text = "Fetching mesh topology..."
        deviceList.clear()
        topologyDataMap.clear()

        val droneIds = mutableListOf<Int>()
        DeviceManager.getDeviceManager().getDroneDevices().forEach { drone ->
            val dId = drone.getDeviceNumber()
            if (dId != 0 && drone.isConnected()) {
                droneIds.add(dId)
            }
        }

        val rcIds = mutableListOf<Int>()
        DeviceManager.getDeviceManager().getFirstRemoteDevice()?.let {
            rcIds.add(it.getDeviceNumber())
        }

        val targetIds = (droneIds + rcIds).distinct()
        deviceList.addAll(targetIds)

        if (deviceList.isEmpty()) {
            binding.tvStatus.text = "No devices found in mesh"
            binding.btnRefresh.isEnabled = true
            return
        }

        SDKLog.i(TAG, "Fetching topology for ${deviceList.size} devices: $deviceList")
        binding.tvStatus.text = "Fetching topology for ${deviceList.size} devices..."

        var fetchedCount = 0

        deviceList.forEach { deviceId ->
            val isDrone = DeviceManager.getDeviceManager().getDroneDeviceById(deviceId) != null
            fetchTopologyForDevice(deviceId, isDrone) {
                fetchedCount++
                if (fetchedCount == deviceList.size) {
                    safePost {
                        binding.tvStatus.text = "Completed: fetched ${fetchedCount} devices"
                        binding.btnRefresh.isEnabled = true
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun fetchTopologyForDevice(
        deviceId: Int,
        isDrone: Boolean,
        callback: () -> Unit
    ) {
        SDKLog.i(TAG, "Fetching topology for device $deviceId (${if (isDrone) "drone" else "rc"})")

        when {
            isDrone -> {
                val drone = DeviceManager.getDeviceManager().getDroneDeviceById(deviceId)
                if (drone == null) {
                    SDKLog.e(TAG, "Drone $deviceId not found in device manager")
                    callback()
                    return
                }

                val keyManager = drone.getKeyManager()
                @Suppress("UNCHECKED_CAST")
                val key = KeyTools.createKey(AMeshKey.KeyDroneNetTopNetGet) as AutelKey<MeshTopNetBean>

                keyManager.getValue(
                    key,
                    object : CommonCallbacks.CompletionCallbackWithParam<MeshTopNetBean> {
                        override fun onSuccess(result: MeshTopNetBean?) {
                            if (result != null) {
                                SDKLog.i(TAG, "Drone $deviceId topology: ${result.selDeviceId}, neighbors=${result.neighborCount}")
                                topologyDataMap[deviceId] = result
                            }
                            callback()
                        }

                        override fun onFailure(error: com.autel.drone.sdk.libbase.error.IAutelCode, msg: String?) {
                            SDKLog.e(TAG, "Failed to fetch drone $deviceId topology: $msg")
                            callback()
                        }
                    }
                )
            }

            else -> {
                val rc = DeviceManager.getDeviceManager().getFirstRemoteDevice()
                if (rc == null) {
                    SDKLog.e(TAG, "RC not found in device manager")
                    callback()
                    return
                }

                val keyManager = rc.getKeyManager()
                @Suppress("UNCHECKED_CAST")
                val key = KeyTools.createKey(AMeshKey.KeyNetTopNetGet) as AutelKey<MeshTopNetBean>

                keyManager.getValue(
                    key,
                    object : CommonCallbacks.CompletionCallbackWithParam<MeshTopNetBean> {
                        override fun onSuccess(result: MeshTopNetBean?) {
                            if (result != null) {
                                SDKLog.i(TAG, "RC topology: ${result.selDeviceId}, neighbors=${result.neighborCount}")
                                topologyDataMap[deviceId] = result
                            }
                            callback()
                        }

                        override fun onFailure(error: com.autel.drone.sdk.libbase.error.IAutelCode, msg: String?) {
                            SDKLog.e(TAG, "Failed to fetch RC topology: $msg")
                            callback()
                        }
                    }
                )
            }
        }
    }

    /**
     * RecyclerView Adapter for displaying mesh topology data
     */
    private class MeshTopologyAdapter(
        private val dataMap: Map<Int, MeshTopNetBean>
    ) : RecyclerView.Adapter<MeshTopologyAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvDeviceInfo: TextView = itemView.findViewById(R.id.tv_device_info)
            private val tvNeighbors: TextView = itemView.findViewById(R.id.tv_neighbors)

            fun bind(deviceId: Int) {
                val data = dataMap[deviceId]
                if (data == null) {
                    tvDeviceInfo.text = "Device $deviceId: No data"
                    tvNeighbors.text = ""
                    return
                }

                val deviceInfo = "Device $deviceId: self=${data.selDeviceId}, father=${data.fatherId}, level=${data.drawCircleLevel}"
                tvDeviceInfo.text = deviceInfo

                val neighborTexts = mutableListOf<String>()
                data.neighbors.forEach { neighbor ->
                    val snr = neighbor.snr
                    val rsrp = neighbor.rsrp
                    val throughput = String.format("%.2f", neighbor.throughput)
                    neighborTexts.add(
                        "  └─ Neighbor ${neighbor.deviceId}: SNR=$snr, RSRP=$rsrp, Throughput=$throughput Mbps"
                    )
                }

                tvNeighbors.text = if (neighborTexts.isEmpty()) {
                    "  (No neighbors)"
                } else {
                    neighborTexts.joinToString("\n")
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_mesh_topology, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sortedDeviceIds = dataMap.keys.sorted()
            if (position < sortedDeviceIds.size) {
                holder.bind(sortedDeviceIds[position])
            }
        }

        override fun getItemCount(): Int = dataMap.size

        fun updateData(newData: Map<Int, MeshTopNetBean>) {
            notifyDataSetChanged()
        }
    }
}
