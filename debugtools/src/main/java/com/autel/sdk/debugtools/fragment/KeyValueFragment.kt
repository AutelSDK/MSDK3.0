package com.autel.sdk.debugtools.fragment

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.autel.drone.sdk.pbprotocol.constants.MessageTypeConstant
import com.autel.drone.sdk.vmodelx.extensions.test
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.utils.ToastUtils.showToast
import com.autel.sdk.debugtools.*
import com.autel.sdk.debugtools.KeyItemHelper.processSubListLogic
import com.autel.sdk.debugtools.databinding.FragmentKeyListBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * key and value of key item fragment
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/12/17.
 */

class KeyValueFragment : AutelFragment(), View.OnClickListener {

    companion object {
        private const val TAG = "KeyValueFragment"
        private const val LISTEN_RECORD_MAX_LENGTH = 6000
        private const val HIGH_FREQUENCY_KEY_SP_NAME = "highfrequencykey"
    }

    private val keyValueVM: KeyValueVM by activityViewModels()
    private var currentChannelType: ChannelType? = ChannelType.CHANNEL_TYPE_CAMERA

    private val logMessage = StringBuilder()
    private var currentKeyItem: KeyItem<Any, Any>? = null
    private val currentKeyTypeList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val currentKeyItemList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val keyItemsWrapper: KeyItemsWrapper<Any, Any> = KeyItemsWrapper()
    private val data: MutableList<KeyItem<Any, Any>> = ArrayList()
    private var cameraParamsAdapter: KeyItemAdapter? = null
    private val batteryKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val wifiKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val bleList: List<KeyItem<Any, Any>> = ArrayList()
    private val gimbalKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val cameraKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val flightAssistantKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val flightParamsKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val flightControlKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val commonUploadList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val airlinkKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val aiServiceKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val trackKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val remoteIdKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val systemManagerKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val remoteControllerKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val productKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val ocuSyncKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val visionKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val rtkKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val radarKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val ntripKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val ltemoduleKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val mqttKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val appKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val mobileNetworkKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val mobileNetworkLinkRCKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val batteryBoxKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val onBoardKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val payloadKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val lidarKeyList: List<KeyItem<Any, Any>> = ArrayList()
    private val waypointKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val nestKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val meshKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val AccessoriesKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val cloudAPiKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val missionManagerKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val autonomyKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val accurateRetakeKeyList : MutableList<KeyItem<Any, Any>> = ArrayList()
    private val rtmpKeyList : MutableList<KeyItem<Any, Any>> = ArrayList()
    private val rtcKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val commandCenterKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val hardwareDataSecurityKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val payLoadKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val dragonFishFCKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val dragonFishCmdList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val dragonWifiStationCmdList: MutableList<KeyItem<Any, Any>> = ArrayList()
    private val aMeshKeyList: MutableList<KeyItem<Any, Any>> = ArrayList()

    private var keyValueSharedPreferences: SharedPreferences? = null
    private val selectMode = false
    private var isBatchTestSwitchOn = false
    private var isNowListenAll = false

    private lateinit var binding: FragmentKeyListBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentKeyListBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initLocalData()
        initView()
        initRemoteData()
        keyValueVM.languageLiveData.observe(viewLifecycleOwner) {
            cameraParamsAdapter?.notifyDataSetChanged()
//            updateOperateText()
        }
        keyValueVM.displayLiveData.postValue(true)
    }

    private fun initLocalData() {
        data.clear()
        KeyItemDataUtil.initCameraKeyList(cameraKeyList)
        currentKeyItemList.addAll(cameraKeyList)
        data.addAll(currentKeyItemList)
        keyItemsWrapper.keyItemList = currentKeyItemList
        keyItemsWrapper.keyItemWrapperListener = keyItemWrapperListener
        cameraParamsAdapter = KeyItemAdapter(activity, data, itemClickCallback)
        keyValueSharedPreferences =
            activity?.getSharedPreferences(HIGH_FREQUENCY_KEY_SP_NAME, Context.MODE_PRIVATE)
    }


    private fun initView() {
        initViewAndListener()
//        binding.layoutKey.tvResult.setOnLongClickListener {
//            val cmb = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//            cmb.setPrimaryClip(ClipData.newPlainText("label", binding.layoutKey.tvResult.text.toString()))
//            true
//        }
        binding.layoutKey.apply {
            btGet.setOnClickListener(this@KeyValueFragment)
            btSet.setOnClickListener(this@KeyValueFragment)
            btListen.setOnClickListener(this@KeyValueFragment)
            btAction.setOnClickListener(this@KeyValueFragment)
            btFrequencyReport.setOnClickListener(this@KeyValueFragment)
            btnClearlog.setOnClickListener(this@KeyValueFragment)
            btSetAll.setOnClickListener(this@KeyValueFragment)
            btGetAll.setOnClickListener(this@KeyValueFragment)
            btSetGetAll.setOnClickListener(this@KeyValueFragment)
            btListenAll.setOnClickListener(this@KeyValueFragment)
            paramTest.setOnClickListener(this@KeyValueFragment)
        }
        binding.swBatchTest.isChecked = isBatchTestSwitchOn
        binding.swBatchTest.setOnCheckedChangeListener { _, enable -> setBatchTestSwitch(enable) }
        setKeyCount(currentKeyItemList.size)
    }

    private fun initViewAndListener() {
        binding.tvOperateTitle.setOnClickListener {
            channelTypeFilterOperate()
        }
//        binding.ivActionArrow1.setOnClickListener {
//            channelTypeFilterOperate()
//        }
        binding.llFilterContainer.setOnClickListener {
            keyFilterOperate()
        }

        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                //Do Something
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                //Do Something
            }

            override fun afterTextChanged(s: Editable) {
                cameraParamsAdapter?.filter?.filter(s.toString())
            }
        })
    }

    private fun initRemoteData() {
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        binding.recyclerView.adapter = cameraParamsAdapter
    }

    /**
     * Key list item click callback
     */
    private val itemClickCallback: KeyItemActionListener<KeyItem<Any, Any>?> = object :
        KeyItemActionListener<KeyItem<Any, Any>?> {

        override fun actionChange(msgType: MsgType, keyItem: KeyItem<Any, Any>?) {
            if (keyItem == null) {
                return
            }
            initKeyInfo(keyItem)
            cameraParamsAdapter?.notifyDataSetChanged()
        }
    }

    private val keyItemWrapperListener: KeyItemWrapperListener<Any> =
        object : KeyItemWrapperListener<Any> {
            override fun actionChange(t: Any?) {
                binding.layoutKey.tvResult.text = appendLogMessageRecord(t.toString())
                scrollToBottom()
            }
        }

    /**
     * Key operation result callback
     */
    private val keyItemOperateCallBack: KeyItemActionListener<Any> =
        KeyItemActionListener<Any> { _, t -> //  processListenLogic();
            t?.let {
                binding.layoutKey.tvResult.text = appendLogMessageRecord(t.toString())
                scrollToBottom()
            }
        }


    private fun scrollToBottom() {
        with(binding.layoutKey.tvResult) {
            if (layout != null) {
                val scrollOffset = (this.layout.getLineTop(this.lineCount)
                        - this.height)
                if (scrollOffset > 0) {
                    this.scrollTo(0, scrollOffset)
                } else {
                    this.scrollTo(0, 0)
                }
            }
        }
    }

    private fun appendLogMessageRecord(appendStr: String?): String {
        val curTime = SimpleDateFormat("HH:mm:ss").format(Date())
        logMessage.append(curTime)
            .append(":")
            .append(appendStr)
            .append("\n")

        //Length limit
        var result = logMessage.toString()
        if (result.length > LISTEN_RECORD_MAX_LENGTH) {
            result = result.substring(result.length - LISTEN_RECORD_MAX_LENGTH)
        }
        return result
    }
//
    /**
     * Push result callback
     */
    private val pushCallback: KeyItemActionListener<String> =
        KeyItemActionListener<String> { _, t -> //  processListenLogic();
            Log.i("tv_result", " pushCallback")
            binding.layoutKey.tvResult.text = appendLogMessageRecord(t)
            scrollToBottom()
        }

    /**
     * Initialize key info
     *
     * @param keyItem
     */
    private fun initKeyInfo(keyItem: KeyItem<Any, Any>) {
        currentKeyItem = keyItem
        currentKeyItem!!.setKeyOperateCallBack(keyItemOperateCallBack)
        binding.layoutKey.apply {
            tvName.text = keyItem.name
            btAddCommand
            btAddCommand.visibility = if (selectMode) View.VISIBLE else View.GONE
            btGpscoord.visibility = View.GONE

            tvVideoRecordTime.visibility = View.GONE
            tvName.text = keyItem.name
            btAddCommand.visibility = if (selectMode) View.VISIBLE else View.GONE
            btSet.isEnabled = currentKeyItem!!.canSet()
            btGet.isEnabled = currentKeyItem!!.canGet()
            btListen.isEnabled = currentKeyItem!!.canListen()
            btAction.isEnabled = currentKeyItem!!.canAction()
            btFrequencyReport.isEnabled = currentKeyItem!!.canReport()
        }
        keyItem.count = System.currentTimeMillis()
        resetSelected()
        keyValueSharedPreferences?.edit()?.putLong(keyItem.toString(), keyItem.count)?.apply()
        keyItem.isItemSelected = true
        /*if (currentKeyItem!!.canListen() && currentKeyItem!!.isListening) {
            val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
            if (isEnglish) {
                binding.layoutKey.btListen.text = "Un-Listen"
            } else {
                binding.layoutKey.btListen.text = "Stop listening"
            }
        } else {
            val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
            if (isEnglish) {
                binding.layoutKey.btListen.text = "Listen"
            } else {
                binding.layoutKey.btListen.text = "Listen"
            }
        }*/
        if (currentKeyItem!!.canListen() && currentKeyItem!!.isListening) {
            binding.layoutKey.btListen.text = getString(R.string.debug_un_listen)
        } else {
            binding.layoutKey.btListen.text = getString(R.string.debug_listen)
        }
    }

    private fun resetSelected() {
        for (item in data) {
            if (item.isItemSelected) {
                item.isItemSelected = false
            }
        }
    }

    /**
     * Handle the Listen button display
     */
    private fun processListenLogic() {
        if (currentKeyItem == null) {
            /*val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
            if (isEnglish) {
                binding.layoutKey.btListen.text = "Listen"
            } else {
                binding.layoutKey.btListen.text = "Listen"
            }*/
            binding.layoutKey.btListen.text = getString(R.string.debug_listen)
            binding.layoutKey.tvName.text = ""
            return
        }

        /*if (currentKeyItem!!.listenHolder == null) {
            val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
            if (isEnglish) {
                binding.layoutKey.btListen.text = "Listen"
            } else {
                binding.layoutKey.btListen.text = "Listen"
            }
        } else {
            val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
            if (isEnglish) {
                binding.layoutKey.btListen.text = "Un-Listen"
            } else {
                binding.layoutKey.btListen.text = "Stop listening"
            }
        }*/
        if (currentKeyItem!!.listenHolder == null) {
            binding.layoutKey.btListen.text = getString(R.string.debug_listen)
        } else {
            binding.layoutKey.btListen.text = getString(R.string.debug_un_listen)
        }
    }

    /**
     * Initialize data per entry type
     */
    private fun processChannelInfo() {
        currentKeyTypeList.clear()
        currentKeyItemList.clear()
        var tips: String? = ""
        when (currentChannelType) {
//            ChannelType.CHANNEL_TYPE_BATTERY -> {
//                KeyItemDataUtil.initBatteryKeyList(batteryKeyList)
//                tips = getString(R.string.debug_battery)
//                currentKeyItemList.addAll(batteryKeyList)
//            }
            ChannelType.CHANNEL_TYPE_GIMBAL -> {
                KeyItemDataUtil.initGimbalKeyList(gimbalKeyList)
                tips = getString(R.string.debug_gimbal)
                currentKeyItemList.addAll(gimbalKeyList)
            }
            ChannelType.CHANNEL_TYPE_CAMERA -> {
                KeyItemDataUtil.initCameraKeyList(cameraKeyList)
                tips = getString(R.string.debug_camera)
                currentKeyItemList.addAll(cameraKeyList)
            }
            ChannelType.CHANNEL_TYPE_FLIGHT_PARAMS -> {
                tips = getString(R.string.debug_flight_params)
                KeyItemDataUtil.initFlightParamsKeyList(flightParamsKeyList)
                currentKeyItemList.addAll(flightParamsKeyList)
            }
            ChannelType.CHANNEL_TYPE_FLIGHT_CONTROL -> {
                tips = getString(R.string.debug_flight_control)
                KeyItemDataUtil.initFlightControllerKeyList(flightControlKeyList)
                currentKeyItemList.addAll(flightControlKeyList)
            }
            ChannelType.CHANNEL_TYPE_AIRLINK -> {
                tips = getString(R.string.debug_airlink)
                KeyItemDataUtil.initAirlinkKeyList(airlinkKeyList)
                currentKeyItemList.addAll(airlinkKeyList)
            }
            ChannelType.CHANNEL_TYPE_AISERVICE -> {
                tips = getString(R.string.debug_ai_service)
                KeyItemDataUtil.initAIServiceKeyList(aiServiceKeyList)
                currentKeyItemList.addAll(aiServiceKeyList)
            }
            ChannelType.CHANNEL_TYPE_REMOTE_CONTROLLER -> {
                tips = getString(R.string.debug_remote_controller)
                KeyItemDataUtil.initRemoteControllerKeyList(remoteControllerKeyList)
                currentKeyItemList.addAll(remoteControllerKeyList)
            }
            ChannelType.CHANNEL_TYPE_BLE -> {
                tips = getString(R.string.debug_ble)
                KeyItemDataUtil.initBleKeyList(bleList)
                currentKeyItemList.addAll(bleList)
            }
            ChannelType.CHANNEL_TYPE_PRODUCT -> {
                tips = getString(R.string.debug_product)
                KeyItemDataUtil.initProductKeyList(productKeyList)
                currentKeyItemList.addAll(productKeyList)
            }

            ChannelType.CHANNEL_TYPE_OCU_SYNC -> {
                tips = getString(R.string.debug_ocusync)
                KeyItemDataUtil.initOcuSyncKeyList(ocuSyncKeyList)
                currentKeyItemList.addAll(ocuSyncKeyList)
            }
            ChannelType.CHANNEL_TYPE_VISION -> {
                tips = getString(R.string.debug_vision)
                KeyItemDataUtil.initVisionKeyList(visionKeyList)
                currentKeyItemList.addAll(visionKeyList)
            }
            ChannelType.CHANNEL_TYPE_RTK -> {
                tips = getString(R.string.debug_rtk)
                KeyItemDataUtil.initRtkKeyList(rtkKeyList)
                currentKeyItemList.addAll(rtkKeyList)
            }
            ChannelType.CHANNEL_TYPE_RADAR -> {
                tips = getString(R.string.debug_radar)
                KeyItemDataUtil.initRadarKeyList(radarKeyList)
                currentKeyItemList.addAll(radarKeyList)
            }

            ChannelType.CHANNEL_TYPE_NTRIP -> {
                tips = getString(R.string.debug_ntrip)
                KeyItemDataUtil.initNtripKeyList(ntripKeyList)
                currentKeyItemList.addAll(ntripKeyList)
            }
            ChannelType.CHANNEL_TYPE_MQTT -> {
                tips = getString(R.string.debug_mqtt)
                KeyItemDataUtil.initMqttKeyList(mqttKeyList)
                currentKeyItemList.addAll(mqttKeyList)
            }

            ChannelType.CHANNEL_TYPE_LTEMODULE -> {
                tips = getString(R.string.debug_lte_module)
                KeyItemDataUtil.initLteModuleList(ltemoduleKeyList)
                currentKeyItemList.addAll(ltemoduleKeyList)
            }
            ChannelType.CHANNEL_TYPE_MOBILE_NETWORK -> {
                tips = getString(R.string.debug_mobile_network)
                KeyItemDataUtil.initMobileNetworkKeyList(mobileNetworkKeyList)
                KeyItemDataUtil.initMobileNetworkLinkRCKeyList(mobileNetworkLinkRCKeyList)
                currentKeyItemList.addAll(mobileNetworkKeyList)
                currentKeyItemList.addAll(mobileNetworkLinkRCKeyList)
            }
            ChannelType.CHANNEL_TYPE_ON_BOARD -> {
                tips = getString(R.string.debug_on_board)
                KeyItemDataUtil.initOnboardKeyList(onBoardKeyList)
                currentKeyItemList.addAll(onBoardKeyList)
            }
            ChannelType.CHANNEL_TYPE_ON_PAYLOAD -> {
                tips = getString(R.string.debug_payload)
                KeyItemDataUtil.initPayloadKeyList(payloadKeyList)
                currentKeyItemList.addAll(payloadKeyList)
            }
            ChannelType.CHANNEL_TYPE_LIDAR -> {
                tips = getString(R.string.debug_lidar)
                KeyItemDataUtil.initLidarKeyList(lidarKeyList)
                currentKeyItemList.addAll(lidarKeyList)
            }
            ChannelType.CHANNEL_TYPE_FLIGHT_MISSION -> {
                tips = getString(R.string.debug_channel_type_flightmission)
                KeyItemDataUtil.initMissionKeyList(waypointKeyList)
                currentKeyItemList.addAll(waypointKeyList)
            }
            ChannelType.COMMON_UPLOAD -> {
                tips = getString(R.string.debug_common)
                KeyItemDataUtil.initCommonUploadKeyList(commonUploadList)
                currentKeyItemList.addAll(commonUploadList)
            }
            ChannelType.CHANNEL_TYPE_AI_TRACK -> {
                tips = getString(R.string.debug_ai_track)
                KeyItemDataUtil.initTrackingKeyList(trackKeyList)
                currentKeyItemList.addAll(trackKeyList)
            }
            ChannelType.CHANNEL_TYPE_REMOTE_ID -> {
                tips = getString(R.string.debug_remote_id)
                KeyItemDataUtil.initRemoteIdKeyList(remoteIdKeyList)
                currentKeyItemList.addAll(remoteIdKeyList)
            }
            ChannelType.SYSTEM_MANAGER -> {
                tips = getString(R.string.debug_system_manager)
                KeyItemDataUtil.initSystemManagerKeyList(systemManagerKeyList)
                currentKeyItemList.addAll(systemManagerKeyList)
            }
            ChannelType.CHANNEL_TYPE_NEST -> {
                tips = getString(R.string.debug_nest)
                KeyItemDataUtil.initNestKeyList(nestKeyList)
                currentKeyItemList.addAll(nestKeyList)
            }

            ChannelType.CHANNEL_TYPE_MESH -> {
                tips = getString(R.string.debug_mesh)
                KeyItemDataUtil.initMeshKeyList(meshKeyList)
                currentKeyItemList.addAll(meshKeyList)
            }

            ChannelType.MISSION_MANAGER -> {
                tips = getString(R.string.debug_mission_manager)
                KeyItemDataUtil.initMissionManagerKeyList(missionManagerKeyList)
                currentKeyItemList.addAll(missionManagerKeyList)
            }
            ChannelType.ACCESSORIES_PROXY -> {
                tips = getString(R.string.debug_accessories_proxy)
                KeyItemDataUtil.initAccessoriesKeyListList(AccessoriesKeyList)
                currentKeyItemList.addAll(AccessoriesKeyList)
            }
            ChannelType.CLOUD_API -> {
                tips = getString(R.string.debug_cloud_api)
                KeyItemDataUtil.initCloudAPiKeyListList(cloudAPiKeyList)
                currentKeyItemList.addAll(cloudAPiKeyList)
            }
            ChannelType.CHANNEL_TYPE_AUTONOMY -> {
                tips = getString(R.string.debug_autonomy)
                KeyItemDataUtil.initAutonomyKeyList(autonomyKeyList)
                currentKeyItemList.addAll(autonomyKeyList)
            }
            ChannelType.CHANNEL_TYPE_ACCURATE_RETAKE -> {
                tips = getString(R.string.debug_accurate_retake)
                KeyItemDataUtil.initAccurateRetakeKeyList(accurateRetakeKeyList)
                currentKeyItemList.addAll(accurateRetakeKeyList)
            }
            ChannelType.CHANNEL_TYPE_RTMP -> {
                tips = getString(R.string.debug_rtmp)
                KeyItemDataUtil.initRtmpKeyList(rtmpKeyList)
                currentKeyItemList.addAll(rtmpKeyList)
            }
            ChannelType.CHANNEL_TYPE_COMMAND_CENTER -> {
                tips = getString(R.string.debug_command_center)
                KeyItemDataUtil.initCommandCenterKeyList(commandCenterKeyList)
                currentKeyItemList.addAll(commandCenterKeyList)
            }
            ChannelType.CHANNEL_TYPE_WIFI -> {
                tips = getString(R.string.debug_wifi)
                KeyItemDataUtil.initWifiKeyList(wifiKeyList)
                currentKeyItemList.addAll(wifiKeyList)
            }
            ChannelType.CHANNEL_TYPE_RTC -> {
                tips = getString(R.string.debug_rtc)
                KeyItemDataUtil.initRtcKeyList(rtcKeyList)
                currentKeyItemList.addAll(rtcKeyList)
            }
            ChannelType.HARDWARE_DATA_SECURITY -> {
                tips = getString(R.string.debug_data_security)
                KeyItemDataUtil.initHardwareDataSecurityListList(hardwareDataSecurityKeyList)
                currentKeyItemList.addAll(hardwareDataSecurityKeyList)
            }
            ChannelType.PAYLOAD_KEY -> {
                tips = getString(R.string.debug_payload)
                KeyItemDataUtil.initPayloadKeyKeyList(payLoadKeyList)
                currentKeyItemList.addAll(payLoadKeyList)
            }

            ChannelType.A_MESH -> {
                tips = getString(R.string.debug_mesh)
                KeyItemDataUtil.initAMeshKeyKeyList(aMeshKeyList)
                currentKeyItemList.addAll(aMeshKeyList)
            }

            ChannelType.DF_FLIGHT_CONTROL_KEY -> {
                tips = getString(R.string.debug_df_flight_control)
                KeyItemDataUtil.initDFFlightControlKeyList(dragonFishFCKeyList)
                currentKeyItemList.addAll(dragonFishFCKeyList)
            }
            ChannelType.DF_COMMON_CMD_KEY -> {
                tips = getString(R.string.debug_df_common_cmd)
                KeyItemDataUtil.initDFCommonCmdKeyList(dragonFishCmdList)
                currentKeyItemList.addAll(dragonFishCmdList)
            }

            ChannelType.DF_WIFI_STATION_CMD_KEY -> {
                tips = getString(R.string.debug_df_wifi_station_cmd)
                KeyItemDataUtil.initWifiStationCmdKeyList(dragonWifiStationCmdList)
                currentKeyItemList.addAll(dragonWifiStationCmdList)
            }
            else -> {}
        }
        for (item in currentKeyItemList) {
            item.isItemSelected = false
            val count = keyValueSharedPreferences?.getLong(item.toString(), 0L)
            if (count != null && count != 0L) {
                item.count = count
            }
        }
        binding.tvOperateTitle.text = tips
        setDataWithCapability(false)
    }


    private fun setDataWithCapability(enable: Boolean) {
        val showList: MutableList<KeyItem<Any, Any>> = ArrayList(currentKeyItemList)
        data.clear()
        data.addAll(showList)
        resetSearchFilter()
        setKeyCount(showList.size)
        resetSelected()
        cameraParamsAdapter?.notifyDataSetChanged()
    }

    private fun setBatchTestSwitch(enable: Boolean) {
        with(binding.layoutKey) {
            if (enable) {
                msgTestContainer.visibility = View.GONE
                msgBatchTestContainer.visibility = View.VISIBLE
                binding.tvBatchTest.text = getString(R.string.debug_batch_test)
                isBatchTestSwitchOn = true
//                val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
//                if (isEnglish) {
//                    binding.tvBatchTest.text = getString(R.string.batch_test)
//                    isBatchTestSwitchOn = true
//                    btGet.text = "GET"
//                    btSet.text = "SET"
//                    btListen.text = "LISTEN"
//                    btAction.text = "ACTION"
//                    btFrequencyReport.text = "REPORT"
//                } else {
//                    binding.tvBatchTest.text = getString(R.string.batch_test)
//                    isBatchTestSwitchOn = true
//                    btGet.text = "Get"
//                    btSet.text = "Set"
//                    btListen.text = "Listen"
//                    btAction.text = "Action"
//                    btFrequencyReport.text = "Freq report"
//                }
            } else {
                msgTestContainer.visibility = View.VISIBLE
                msgBatchTestContainer.visibility = View.GONE
                binding.tvBatchTest.text = getString(R.string.debug_single_test)
                isBatchTestSwitchOn = false
            }
        }
    }

    /*private fun updateOperateText() {
        val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
        with(binding.layoutKey) {
            if (isEnglish) {
                btGet.text = "GET"
                btSet.text = "SET"
                btListen.text = "LISTEN"
                btAction.text = "ACTION"
                btFrequencyReport.text = "REPORT"
                btSetAll.text = getString(R.string.debug_set_all)
                btGetAll.text = getString(R.string.debug_get_all)
                btActionAll.text = getString(R.string.debug_action_all)
                btSetGetAll.text = getString(R.string.debug_set_get_all)
                paramTest.text = "PARAM_TEST"
                btListenAll.text = getString(R.string.debug_listen_all)
            } else {
                btGet.text = "Get"
                btSet.text = "Set"
                btListen.text = "Listen"
                btAction.text = "Action"
                btFrequencyReport.text = "Freq report"
                btSetAll.text = "Set all"
                btGetAll.text = "Get all"
                btActionAll.text = "Action all"
                btSetGetAll.text = "Set & get all"
                paramTest.text = "Data parse check"
                btListenAll.text = "Listen all"
            }
        }
    }*/

    /**
     * Clear the search box
     */
    private fun resetSearchFilter() {
        binding.etFilter.setText("")
        cameraParamsAdapter?.filter?.filter("")
    }

    private fun setKeyCount(count: Int) {
        binding.tvCount.text = "(${count})"
    }

    override fun onClick(view: View) {
        if ((Util.isBlank(binding.layoutKey.tvName.text.toString()) || currentKeyItem == null)
            && view.id != R.id.bt_set_all
            && view.id != R.id.bt_get_all
            && view.id != R.id.bt_set_get_all
            && view.id != R.id.btn_clearlog
            && view.id != R.id.bt_listen_all
            && view.id != R.id.param_test
        ) {
            showToast(getString(R.string.debug_please_select_key_first))
            return
        }
        setKeyInfo()

        when (view.id) {
            R.id.bt_get -> {
                get()
            }
            R.id.bt_unlistenall -> {
                unListenAll()
            }
            R.id.bt_set -> {
                set()
            }
            R.id.bt_listen -> {
                listen()
            }
            R.id.bt_action -> {
                action()
            }
            R.id.bt_frequency_report -> {
                report()
            }
            R.id.btn_clearlog -> {
                binding.layoutKey.tvResult.text = ""
                logMessage.delete(0, logMessage.length)
            }
            R.id.bt_set_all -> {
                setAll()
            }
            R.id.bt_get_all -> {
                getAll()
            }
            R.id.bt_set_get_all -> {
                setGetAll()
            }
            R.id.bt_listen_all -> {
                listenAll()
            }
            R.id.param_test -> {
                paramTest()
            }
        }

    }

    private fun setAll() {
        currentKeyItemList.filter {
            it.canSet()
        }.run {
            setList(currentKeyItemList)
            binding.layoutKey.tvName.text = getString(R.string.debug_set_all)
        }
    }

    private fun getAll() {
        currentKeyItemList.filter {
            it.canGet()
        }.run {
            getList(currentKeyItemList)
            binding.layoutKey.tvName.text = getString(R.string.debug_get_all)
        }
    }

    private fun setGetAll() {
        // TODO::
        currentKeyItemList.filter {
            it.canSet() && it.canGet()
        }.forEach {
            val createKey = KeyTools.createKey(it.keyInfo)
            val param =
                it.keyInfo.typeConverter.fromJsonStr(it.keyInfo.typeConverter.getJsonStr() ?: "")

            val createKey1 = KeyTools.createKey(CameraKey.KeyCameraDeviceInfo)
            val param1 = CameraKey.KeyCameraDeviceInfo.typeConverter.fromJsonStr(
                CameraKey.KeyCameraDeviceInfo.typeConverter.getJsonStr() ?: ""
            )

//            instance.setValue(createKey, param, object : CommonCallbacks.CompletionCallback {
//                override fun onSuccess() {
//                    instance.getValue(createKey, object : CommonCallbacks.CompletionCallbackWithParam<*>() {
//
//                    })
//                }
//                override fun onFailure(code: IAutelCode, msg: String?) {
//                }
//            })
        }
    }

    private fun listenAll() {
        currentKeyItemList.filter {
            it.canListen()
        }.forEach {
            it.setKeyOperateCallBack(keyItemOperateCallBack)
            if (isNowListenAll) {
                it.cancelListen(it.keyInfo)
            } else {
                it.listen(it.keyInfo)
            }
        }

        binding.layoutKey.tvName.text = getString(R.string.debug_listen_all)
        if (isNowListenAll) {
            binding.layoutKey.btListenAll.text = getString(R.string.debug_un_listen_all)
        } else {
            binding.layoutKey.btListenAll.text = getString(R.string.debug_listen_all)
        }

        isNowListenAll = !isNowListenAll
    }

    private fun paramTest() {
        val falseList = mutableListOf<String>()
        val trueList = mutableListOf<String>()
        currentKeyItemList.forEach() {
            val result = it.keyInfo.typeConverter.test()
            if (result) {
                trueList.add(it.keyInfo.keyName + getString(R.string.debug_analysis_and_antianalysis_normal))
            } else {
                val name = it.keyInfo.keyName + getString(R.string.debug_parse_or_antiparse_exception)
                falseList.add(name)
            }
        }
        falseList.forEach {
            keyItemOperateCallBack.actionChange(MsgType.MSG_INFO, it)
        }
        trueList.forEach {
            keyItemOperateCallBack.actionChange(MsgType.MSG_INFO, it)
        }
    }

    /**
     * Key list filtering
     */
    private fun keyFilterOperate() {
    }


    private fun channelTypeFilterOperate() {
        val showChannelList: MutableList<ChannelType>
        val capabilityChannelList = arrayOf(
            ChannelType.CHANNEL_TYPE_CAMERA,
            ChannelType.CHANNEL_TYPE_FLIGHT_MISSION,
            ChannelType.CHANNEL_TYPE_FLIGHT_CONTROL,
            ChannelType.CHANNEL_TYPE_GIMBAL,
            ChannelType.CHANNEL_TYPE_AIRLINK,
            ChannelType.CHANNEL_TYPE_AISERVICE,
            ChannelType.CHANNEL_TYPE_FLIGHT_PARAMS,
            ChannelType.CHANNEL_TYPE_VISION,
            ChannelType.CHANNEL_TYPE_REMOTE_CONTROLLER,
            ChannelType.COMMON_UPLOAD,
            ChannelType.CHANNEL_TYPE_AI_TRACK,
            ChannelType.CHANNEL_TYPE_REMOTE_ID,
            ChannelType.CHANNEL_TYPE_NEST,
            ChannelType.CHANNEL_TYPE_MESH,
            ChannelType.SYSTEM_MANAGER,
            ChannelType.MISSION_MANAGER,
            ChannelType.ACCESSORIES_PROXY,
            ChannelType.CLOUD_API,
            ChannelType.CHANNEL_TYPE_AUTONOMY,
            ChannelType.CHANNEL_TYPE_RADAR,
            ChannelType.CHANNEL_TYPE_RTK,
            ChannelType.CHANNEL_TYPE_NTRIP,
            ChannelType.CHANNEL_TYPE_LTEMODULE,
            ChannelType.CHANNEL_TYPE_MQTT,
            ChannelType.CHANNEL_TYPE_ACCURATE_RETAKE,
            ChannelType.CHANNEL_TYPE_RTMP,
            ChannelType.CHANNEL_TYPE_COMMAND_CENTER,
            ChannelType.CHANNEL_TYPE_WIFI,
            ChannelType.HARDWARE_DATA_SECURITY,
            ChannelType.PAYLOAD_KEY,
            ChannelType.A_MESH,
            ChannelType.CHANNEL_TYPE_RTC,
            ChannelType.DF_FLIGHT_CONTROL_KEY,
            ChannelType.DF_COMMON_CMD_KEY,
            ChannelType.DF_WIFI_STATION_CMD_KEY
        )
        showChannelList = capabilityChannelList.toMutableList()
        KeyValueDialogUtil.showChannelFilterListWindow(
            binding.tvOperateTitle,
            showChannelList
        ) { _, channelType ->
            currentChannelType = channelType
            currentKeyItem = null
            processChannelInfo()
            processListenLogic()
        }
    }

    private fun setKeyInfo() {}

    /**
     * Get operation
     */
    private fun get() {
        get(currentKeyItem)
    }

    /**
     * Get operation
     */
    private fun get(keyItem: KeyItem<Any, Any>?) {
        if (!keyItem?.canGet()!!) {
            showToast(getString(R.string.debug_not_support_get))
            return
        }
        keyItem.setKeyOperateCallBack(keyItemOperateCallBack)
        keyItem.doGet()
    }

    /**
     * Batch get operation
     */
    private fun getList(keyItems: List<KeyItem<Any, Any>?>) {
        keyItemsWrapper.getList(keyItems)
        keyItemsWrapper.keyItemWrapperListener = keyItemWrapperListener
    }

    /**
     * Batch set-and-get operation
     */
    private fun setAndGetList(keyItems: List<KeyItem<Any, Any>?>) {
        keyItemsWrapper.getList(keyItems)
        keyItemsWrapper.keyItemWrapperListener = keyItemWrapperListener
    }

    /**
     * Batch get operation
     */
    private fun setList(keyItems: List<KeyItem<Any, Any>?>) {
        keyItemsWrapper.setList(keyItems)
        keyItemsWrapper.keyItemWrapperListener = keyItemWrapperListener
    }

    private fun unListenAll() {
        release()
        processListenLogic()
    }

    /**
     * Listen operation
     */
    private fun listen() {
        if (!currentKeyItem?.canListen()!!) {
            showToast(getString(R.string.debug_not_support_listen))
            return
        }
        currentKeyItem!!.setPushCallBack(pushCallback)
        currentKeyItem!!.setKeyOperateCallBack(keyItemOperateCallBack)
        val listenHolder = currentKeyItem!!.listenHolder
        /*if (listenHolder == null) {
            currentKeyItem!!.listen(this)
            val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
            if (isEnglish) {
                binding.layoutKey.btListen.text = "Un-Listen"
            } else {
                binding.layoutKey.btListen.text = "Unlisten"
            }

        } else if (listenHolder is KeyValueFragment) {
            currentKeyItem!!.cancelListen(this)
            val isEnglish = getBooleanValue(MmkvConstants.IS_ENGLISH, false)
            if (isEnglish) {
                binding.layoutKey.btListen.text = "Listen"
            } else {
                binding.layoutKey.btListen.text = "Listen"
            }
        }*/
        if (listenHolder == null) {
            currentKeyItem!!.listen(this)
            binding.layoutKey.btListen.text=  getString(R.string.debug_un_listen)

        } else if (listenHolder is KeyValueFragment) {
            currentKeyItem!!.cancelListen(this)
            binding.layoutKey.btListen.text=  getString(R.string.debug_listen)
        }
        processListenLogic()
    }

    /**
     * Set operation
     */
    private fun set() {
        set(currentKeyItem)
    }

    /**
     * Set operation
     */
    private fun set(keyItem: KeyItem<Any, Any>?, useDefaultData: Boolean = false) {
        if (!keyItem?.canSet()!!) {
            showToast(getString(R.string.debug_not_support_set))
            return
        }
        keyItem.setKeyOperateCallBack(keyItemOperateCallBack)
        if (useDefaultData) {
            keyItem.doSet(keyItem.paramJsonStr)
        } else if (keyItem.subItemMap.isNotEmpty()) {
            processSubListLogic(
                binding.layoutKey.btSet,
                keyItem.param,
                keyItem.subItemMap as Map<String?, List<EnumItem>>,
                object :
                    KeyItemActionListener<String?> {
                    override fun actionChange(msgType: MsgType,paramJsonStr: String?) {
                        if (Util.isBlank(paramJsonStr)) {
                            return
                        }
                        keyItem.doSet(paramJsonStr)
                    }
                })
        } else {
            KeyValueDialogUtil.showInputDialog(
                activity,
                keyItem,
                object :
                    KeyItemActionListener<String?> {
                    override fun actionChange(msgType: MsgType, s: String?) {
                        if (Util.isBlank(s)) {
                            return
                        }
                        keyItem.doSet(s)
                    }
                })
        }
    }

    /**
     * Action operation
     */
    private fun action() {
        if (!currentKeyItem?.canAction()!!) {
            showToast(getString(R.string.debug_not_support_action))
            return
        }
        currentKeyItem!!.setKeyOperateCallBack(keyItemOperateCallBack)
        if (currentKeyItem?.subItemMap!!.isNotEmpty()) {
            processSubListLogic(
                binding.layoutKey.btSet,
                currentKeyItem?.param,
                currentKeyItem?.subItemMap as Map<String?, List<EnumItem>>,
                object :
                    KeyItemActionListener<String?> {
                    override fun actionChange(msgType: MsgType, paramJsonStr: String?) {
                        if (Util.isBlank(paramJsonStr)) {
                            return
                        }
                        currentKeyItem!!.doAction(paramJsonStr)
                    }
                })
        } else if (currentKeyItem!!.paramJsonStr == null) {
            currentKeyItem!!.doAction(null)
        } else {
            KeyValueDialogUtil.showInputDialog(
                activity,
                currentKeyItem
            ) { _,s -> currentKeyItem!!.doAction(s) }
        }
    }

    private fun String.toIntOrUseDefault(defaultValue: Int): Int {
        return try {
            toInt()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }


    /**
     * Report operation
     */
    private fun report() {
        if (!currentKeyItem?.canReport()!!) {
            showToast(getString(R.string.debug_not_support_report))
            return
        }
        currentKeyItem!!.setKeyOperateCallBack(keyItemOperateCallBack)

        val spSubtypeValue = binding.layoutKey.spSubtype.text.toString()
        val srcNodeId = spSubtypeValue.toIntOrUseDefault(1)

        val spSubIndexValue = binding.layoutKey.spSubindex.text.toString()
        val dstNodeId = spSubIndexValue.toIntOrUseDefault(0)

        currentKeyItem?.keyInfo?.setSrcNodeId(srcNodeId)
        currentKeyItem?.keyInfo?.setDestNodeId(dstNodeId)


        if(currentKeyItem?.keyInfo?.keyName == MessageTypeConstant.PAYLOAD_LIGHT_SPEAKER_RUNTIME_MESSAGE){
            showToast("Loudspeaker started")

            var index = 0
            while(currentKeyItem?.isStop == false){
                if (!TextUtils.isEmpty(currentKeyItem?.paramJsonStr)) {
                    currentKeyItem!!.doReport(currentKeyItem?.paramJsonStr)
                    Thread.sleep(100L)
                    index++
                }
            }

            currentKeyItem?.isStop = false

            showToast("Loudspeaker finished, $index rounds")

        } else{
            if (currentKeyItem?.subItemMap!!.isNotEmpty()) {
                processSubListLogic(
                    binding.layoutKey.btSet,
                    currentKeyItem?.param,
                    currentKeyItem?.subItemMap as Map<String?, List<EnumItem>>,
                    object :
                        KeyItemActionListener<String?> {
                        override fun actionChange(msgType: MsgType, paramJsonStr: String?) {
                            if (Util.isBlank(paramJsonStr)) {
                                return
                            }
                            currentKeyItem!!.doReport(paramJsonStr)
                        }
                    })
            } else if (currentKeyItem!!.paramJsonStr == null) {
                currentKeyItem!!.doReport(null)
            } else {
                KeyValueDialogUtil.showInputDialog(
                    activity,
                    currentKeyItem
                ) { _, s -> currentKeyItem!!.doReport(s) }
            }
        }
    }

    /**
     * Unregister Listen and remove business callbacks
     *
     * @param list
     */
    private fun releaseKeyInfo(list: MutableList<KeyItem<Any, Any>>?) {
        if (list == null) {
            return
        }
        for (item in list) {
            item.removeCallBack()
            item.cancelListen(this)
        }
    }

    private fun release() {
        if (currentKeyItem != null) {
            currentKeyItem!!.cancelListen(this)
        }
        releaseKeyInfo(batteryKeyList)
        releaseKeyInfo(gimbalKeyList)
        releaseKeyInfo(cameraKeyList)
        releaseKeyInfo(wifiKeyList)
        releaseKeyInfo(flightAssistantKeyList)
        releaseKeyInfo(flightControlKeyList)
        releaseKeyInfo(airlinkKeyList)
        releaseKeyInfo(productKeyList)
        releaseKeyInfo(rtkKeyList)
        releaseKeyInfo(remoteControllerKeyList)
        releaseKeyInfo(radarKeyList)
        releaseKeyInfo(appKeyList)
        releaseKeyInfo(accurateRetakeKeyList)
        releaseKeyInfo(payLoadKeyList)
        releaseKeyInfo(cloudAPiKeyList)
        releaseKeyInfo(rtcKeyList)
    }

    override fun onDestroy() {
        super.onDestroy()
        release()
        // Clear only this page's listeners; clearing all would kill the app's listeners too
        isNowListenAll = true
        listenAll()
//        currentKeyItem?.cancelAllListen()
        keyValueVM.displayLiveData.postValue(false)
    }
}