package com.autel.sdk.debugtools.fragment


import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.autel.drone.sdk.SDKConstants
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.interfaces.IAutelRemoteDevice
import com.autel.drone.sdk.vmodelx.interfaces.IControlDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IMeshDeviceChangedListener
import com.autel.drone.sdk.vmodelx.interfaces.IMultiDeviceOperator
import com.autel.drone.sdk.vmodelx.interfaces.INetMeshManager
import com.autel.drone.sdk.vmodelx.interfaces.IWatchDroneListener
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.data.ControlMode
import com.autel.drone.sdk.vmodelx.manager.data.MeshModeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.nest.enums.ModemModeEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.netmesh.CreateDeviceNetworkReq
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.netmesh.CreateDeviceNetworkResp
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.netmesh.DeviceNetworkInfo
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.netmesh.DeviceNetworkStatusType
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.adapter.NetMeshDemoAdapter
import com.autel.sdk.debugtools.databinding.FragmentNetMeshDemoBinding

/**
 * Demonstrates mesh networking
 */
@SuppressLint("SetTextI18n")
class NetMeshDemoFragment : AutelFragment(){
    companion object{

        private const val TAG = "NetMeshDemoFragment"
    }
    private lateinit var binding: FragmentNetMeshDemoBinding

    private var dataList: MutableList<Any> = mutableListOf()

    private lateinit var adapter: NetMeshDemoAdapter

    private var netMeshResponse: CreateDeviceNetworkResp? = null

    private var watchChangeListener: IWatchDroneListener? = null
    private var controlChangeListener: IControlDroneListener? = null
    private var meshDeviceChangedListener: IMeshDeviceChangedListener? = null

    private fun getMultiDeviceOperator():IMultiDeviceOperator{ return  DeviceManager.getMultiDeviceOperator() }

    //networking APIs
    private fun getNetMeshManger():INetMeshManager{ return getMultiDeviceOperator().getNetMeshManager() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNetMeshDemoBinding.inflate(layoutInflater)
        setAdapter()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewListener()
        initNetMeshListener()
        initInfoContent()
        updateData()
    }

    override fun onDestroyView() {
        val operator = getMultiDeviceOperator()
        watchChangeListener?.let { operator.removeWatchChangeListener(it) }
        controlChangeListener?.let { operator.removeControlChangeListener(it) }
        meshDeviceChangedListener?.let { operator.removeNetMeshChangeListener(it) }
        watchChangeListener = null
        controlChangeListener = null
        meshDeviceChangedListener = null
        super.onDestroyView()
    }

    private fun initViewListener(){
        binding.btnNetMeshStart.setOnClickListener{ startNetMesh(false) }
        binding.btnNetMeshComplete.setOnClickListener{ finishNetMesh() }
        binding.btnNetMeshDisband.setOnClickListener{ disbandNetMesh{} }
        binding.btnControlMode.setOnClickListener { setControlMode(ControlMode.ALL, 0) }
        binding.btnChangeWatch.setOnClickListener {}
        binding.btnChangeCenterDrone.setOnClickListener {}

        binding.btnNetSingleStart.setOnClickListener { startSingleMatch() }
    }


    private fun initInfoContent() {
        if (!isAdded) return
        binding.controlText.text = "Control Model: ${DeviceManager.getMultiDeviceOperator().getControlMode()}"

        val controlDrones = DeviceManager.getMultiDeviceOperator().getControlledDroneList()
        var msg = ""
        controlDrones.forEach { msg +="(${it.deviceNumber()}, ${it.getName()}}" }
        binding.controlDrone.text = "Control Drones: $msg"

        val watchDrone = DeviceManager.getMultiDeviceOperator().getDroneDevices().firstOrNull()
        binding.watchDrone.text = "Watched Drones: (${watchDrone?.deviceNumber()}, ${watchDrone?.getName()})"

        if(DeviceManager.getDeviceManager().getModemMode()== ModemModeEnum.MESH_MODE) {
            binding.modernMode.text = "Modem Mode: A-Mesh Link"
        } else {
            binding.modernMode.text = "Modem Mode: Single Link"
        }
    }

    /**
     * Networking listener
     */
    private fun initNetMeshListener(){
        //Watch drone change report, Autel player will change channel ID to watch video stream
        watchChangeListener = object : IWatchDroneListener {
            override fun onWatchChange(droneList: List<IAutelDroneDevice>) {
                runOnUiThreadIfAttached {
                    val droneDevice = droneList.firstOrNull()
                    //Now only support watch one drone
                    if(droneList.size == 1 && droneDevice != null){
                        val lenList = droneDevice.getCameraAbilitySetManger().getLensList()
                        SDKLog.i(TAG, "lenList=$lenList")
                        val channelId = if(droneDevice.isCenter()){
                             SDKConstants.getZoomChancelId()
                        } else {
                             SDKConstants.getLeafZoomChancelId()
                        }
                        ToastUtils.showToast("Current watch drone=${droneDevice.toSampleString()}, channelId=$channelId")
                    }
                }
            }
        }
        DeviceManager.getMultiDeviceOperator().addWatchChangeListener(watchChangeListener!!)

        //Control mode change report
        controlChangeListener = object : IControlDroneListener {
            override fun onControlChange(mode: ControlMode, droneList: List<IAutelDroneDevice>) {
                runOnUiThreadIfAttached {
                    ToastUtils.showToast("Control mode change:${mode}： ${droneList.map { it.deviceNumber()}}")
                    updateData()
                }
            }
        }
        DeviceManager.getMultiDeviceOperator().addControlChangeListener(controlChangeListener!!)

        //Network status reporting
        meshDeviceChangedListener = object : IMeshDeviceChangedListener {
            override fun onDroneDeviceChanged(newDroneList: MutableList<IAutelDroneDevice>?, oldDroneList: MutableList<IAutelDroneDevice>?) {
                runOnUiThreadIfAttached {
                    updateData() //drone update

                    //whether a relay drone exists
                    if(newDroneList?.none { it.isCenter() } == true){
                        newDroneList.firstOrNull()?.let { setCenterDrone(it.deviceNumber()) }
                    }

                    //whether a watch drone exists
                    if(newDroneList?.none { it.isWatched() } == true){
                        newDroneList.firstOrNull()?.let { setWatchDrone(it.deviceNumber()) }
                    }
                }
            }

            override fun onRemoteDeviceChanged(newRCList: MutableList<IAutelRemoteDevice>?, oldRCList: MutableList<IAutelRemoteDevice>?) {
                runOnUiThreadIfAttached {
                    updateData() //RC update
                }
            }

            override fun onMeshStateChanged(status: DeviceNetworkStatusType) {
                runOnUiThreadIfAttached {
                    when(status){
                        DeviceNetworkStatusType.STARTED->{}
                        DeviceNetworkStatusType.SUCCESS->{}
                        DeviceNetworkStatusType.DISMISS->{
                            ToastUtils.showToast(getString(R.string.netmesh_disband))
                            updateData()
                        }
                        DeviceNetworkStatusType.TIMEOUT->{
                            ToastUtils.showToast(getString(R.string.netmesh_timeout))
                            startNetMesh(true)
                        }
                        else -> {}
                    }
                }
            }
        }
        getMultiDeviceOperator().addNetMeshChangeListener(meshDeviceChangedListener!!)
    }

    /**
     * Set the relay drone
     */
    private fun setCenterDrone(deviceId: Int){
        if (!isAdded) return
        getNetMeshManger().setCenterNode(deviceId, guardedCallbackWithParam(
            onSuccess = {
                ToastUtils.showToast(getString(R.string.center_set_success, deviceId))
            },
            onFailure = { _, msg ->
                ToastUtils.showToast(getString(R.string.center_set_failed, msg))
            },
        ))
    }

    /**
     * Set the drone whose stream to watch
     */
    private fun setWatchDrone(deviceId: Int){
        if (!isAdded) return
        getNetMeshManger().setWatchDevice(arrayListOf(deviceId), guardedCallbackWithParam(
            onSuccess = {
                ToastUtils.showToast(getString(R.string.stream_set_success, deviceId))
            },
            onFailure = { _, msg ->
                ToastUtils.showToast(getString(R.string.stream_set_failed, msg))
            },
        ))
    }

    /**
     * set control mode
     * @param mode ALL, GROUP, SINGLE
     * @param id ALL(ignore id), GROUP(groupId), SINGLE(device node id)
     */
    private fun setControlMode(mode: ControlMode, id:Int){
        if (!isAdded) return
        val log = when(mode) {
            ControlMode.ALL -> "ALL"
            ControlMode.GROUP -> "GROUP(groupId=$id)"
            ControlMode.SINGLE -> "SINGLE(nodeId=$id)"
            ControlMode.UNKNOWN -> "UNKNOWN"
        }

        getNetMeshManger().switchControlMode(mode,id, guardedCallbackWithParam(
            onSuccess = {
                ToastUtils.showToast("$log set control mode success")
            },
            onFailure = { error, msg ->
                ToastUtils.showToast("$log set control mode failed：$error, $msg")
            },
        ))
    }

    private fun startSingleMatch(){
        if(DeviceManager.getDeviceManager().getModemMode() == ModemModeEnum.MESH_MODE){
            disbandNetMesh { startAirLinkMatch() }
        } else {
            startAirLinkMatch()
        }
    }

    private fun startAirLinkMatch() {
        if (!isAdded) return
        val matchKey = KeyTools.createKey(AirLinkKey.KeyALinkStartMatching)
        DeviceManager.getDeviceManager().getLocalRemoteDevice().getKeyManager().performAction(matchKey, null,
            guardedCallbackWithParam(
                onSuccess = {
                    ToastUtils.showToast("startAirLinkMatch success")
                },
                onFailure = { error, msg ->
                    ToastUtils.showToast("startAirLinkMatch failed:$error $msg")
                },
            ))
    }

    /**
     * Start Net Mesh
     */
    private fun startNetMesh(continues: Boolean){
        if (!isAdded) return
        val timeOut = 5 * 60  //set timeout
        val continueFlag  = if(continues ) 1 else 0 //continues flag, if timeout
        val netWorkInfo = DeviceNetworkInfo(System.currentTimeMillis(), "demo")
        val bean = CreateDeviceNetworkReq(timeOut, continueFlag, netWorkInfo)
        getNetMeshManger().startNetMeshMatching(bean, MeshModeEnum.STANDARD, guardedCallbackWithParam(
            onSuccess = { t ->
                netMeshResponse = t
                ToastUtils.showToast("start net mesh success")
            },
            onFailure = { error, msg ->
                ToastUtils.showToast("start net mesh failed:$error $msg")
            },
        ))
    }

    /**
     * Finish Net Mesh
     */
    private fun finishNetMesh(){
        if (!isAdded) return
        getNetMeshManger().completeNetMeshMatching(guardedCallbackWithParam(
            onSuccess = {
                ToastUtils.showToast("finish net mesh success")
            },
            onFailure = { error, msg ->
                ToastUtils.showToast("finish net mesh failed:$error $msg")
            },
        ))
    }

    /**
     * Disband Net Mesh
     */
    private fun disbandNetMesh(callback : (Boolean)-> Unit){
        if (!isAdded) return
        val groupId: Long = netMeshResponse?.info?.groupId ?: 0
        getNetMeshManger().disbandNetMesh(groupId, guardedCallbackWithParam(
            onSuccess = {
                ToastUtils.showToast("disband net mesh success")
                callback.invoke(true)
            },
            onFailure = { error, msg ->
                ToastUtils.showToast("disband net mesh failed:$error $msg")
                callback.invoke(false)
            },
        ))
    }

    private fun setAdapter() {
        adapter = NetMeshDemoAdapter()
        adapter.setOnClickListener {
            val nodeId = getMultiDeviceOperator().getDroneDeviceById(it)?.getNodeId()
            nodeId?.let { nId -> setControlMode(ControlMode.SINGLE, nId) }
        }
        adapter.dataList = dataList
        binding.listView.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateData() {
        if (!isAdded) return
        dataList.clear()

        //drone list
        val drones = getMultiDeviceOperator().getDroneDevices()
        //local RC
        val localRemoteDevice = getMultiDeviceOperator().getLocalRemoteDevice()
        //non-local RCs
        val remotes =  getMultiDeviceOperator().getRemoteInfoList()

        dataList.addAll(drones)
        //dataList.add(localRemoteDevice)
        dataList.addAll(remotes)

        adapter.dataList = dataList
        adapter.notifyDataSetChanged()

        initInfoContent()
    }

}
