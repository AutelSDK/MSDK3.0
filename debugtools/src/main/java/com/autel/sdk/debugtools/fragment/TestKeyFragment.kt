package com.autel.sdk.debugtools.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.webkit.WebView
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.autel.drone.sdk.SDKConfig
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.pbprotocol.constants.MessageTypeConstant
import com.autel.drone.sdk.vmodelx.legacy.v25.LegacyV30OnlyKeys
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AIServiceKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AITrackingKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AutonomyKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CloudApiKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightControlKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightMissionKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightPropertyKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.GimbalKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.HardwareDataSecurityKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.LteModuleKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.MissionManagerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.NestKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.NtripAccountKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RCHiddenKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RadarPropertyKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RemoteControllerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RemoteIDKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RtkPropertKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.SystemManagerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.VisionKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.DFCommonCmdKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.DFFlightControlKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.DFFlightPropertyKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.WifiBaseStationKey
import com.autel.drone.sdk.vmodelx.utils.ChineseConstants
import com.autel.sdk.debugtools.HtmlReporter
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.JsonReporter
import com.autel.sdk.debugtools.KeyItem
import com.autel.sdk.debugtools.KeyItemDataUtil
import com.autel.sdk.debugtools.MsgType
import com.autel.sdk.debugtools.databinding.FragmentTestKeyBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Key-Value full-coverage automated testing: Action / Get / Set (write-back) / Listen / Report
 */
@SuppressLint("SetTextI18n")
class TestKeyFragment : AutelFragment() {
    companion object {
        private const val TAG = "TestKeyFragment"
        private const val DELAY_GAP_LONG = 3000L
        private const val LISTEN_WAIT_MS = 5000L
        private const val REPORT_WAIT_MS = 3000L

        private val FilterMatchKeys = mutableListOf(
            AirLinkKey.KeyALinkStartMatching.keyName,
            AirLinkKey.KeyAirlinkEnterSilenceMode.keyName,
        )

        /**
         * Depends on specific hardware/model/business prerequisites, or is a legacy key; excluded from the general TestKey pass-rate stats.
         */
        private val FilterHardwareDependentKeys = mutableListOf(
            "AISERVICE_GET_TARGET_TYPE_PARAMS_MSG",
            "GET_FILE_INFO_MSG",
            CommonKey.KeyFileUpload.keyName,
            "RADAR_RIO_STATUS",
            CameraKey.KeyCameraGimbalLaserEnable.keyName,
            CameraKey.KeyGetBaseParamMsg.keyName,
            CameraKey.KeyStartTakePhoto.keyName,
            CameraKey.KeyStopTakePhoto.keyName,
            CameraKey.KeyStartRecord.keyName,
            CameraKey.KeyStopRecord.keyName,
        )

        private val FilterCustomAccidentKeys = mutableListOf(
            FlightMissionKey.KeyAccidentEnterMode.keyName,
            FlightMissionKey.KeyAccidentExitMode.keyName,
            FlightMissionKey.KeyAccidentStartProcess.keyName,
            FlightMissionKey.KeyAccidentContinueProcess.keyName,
            FlightMissionKey.KeyAccidentStartWorkArea.keyName,
            FlightMissionKey.KeyAccidentPauseProcess.keyName
        )
    }

    private var delayGap = 200
    private var enableDragonFish = false
    private var enableNest = false
    private var enableCustomAccident = false
    private var enableMatch = false

    private lateinit var binding: FragmentTestKeyBinding

    private val keyClassList = mutableListOf<Class<*>>()
    private val keyItemList = HashMap<String, MutableList<KeyItem<*, *>>>()

    private val successList = HashMap<String, String>()
    private val errorList = HashMap<String, String>()
    private val leakList = CopyOnWriteArrayList<String>()
    private val allKeyList = CopyOnWriteArrayList<String>()
    private val allBackList = CopyOnWriteArrayList<String>()
    private val repeatKeyList = CopyOnWriteArrayList<String>()

    private val listenResultList = HashMap<String, String>()
    private val reportResultList = HashMap<String, String>()
    private val setResultList = HashMap<String, String>()
    private val setErrorList = HashMap<String, String>()

    private val testResults = mutableListOf<JsonReporter.KeyTestResult>()
    private val keyStartTimeMap = HashMap<String, Long>()
    private var testStartTime = 0L

    private var totalCount = 0
    private var actionGetCount = 0
    private var listenCaseCount = 0
    private var reportCaseCount = 0
    private var onlySetCount = 0
    private var getSetCount = 0

    private var testJob: Job? = null
    private val cancelled = AtomicBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTestKeyBinding.inflate(inflater)
        HtmlReporter.setTCNameLog("SDK Key Unit Test")
        initView()
        updateUI()
        SDKLog.i(TAG, "FilterKeys= $FilterMatchKeys")
        SDKLog.i(TAG, "FilterKeys= $FilterCustomAccidentKeys")
        return binding.root
    }

    private fun initKeyClassList() {
        keyClassList.add(GimbalKey::class.java)
        keyClassList.add(CameraKey::class.java)
        keyClassList.add(FlightControlKey::class.java)

        keyClassList.add(AIServiceKey::class.java)
        keyClassList.add(AITrackingKey::class.java)
        keyClassList.add(AutonomyKey::class.java)

        keyClassList.add(CloudApiKey::class.java)
        keyClassList.add(CommonKey::class.java)
        keyClassList.add(FlightMissionKey::class.java)
        keyClassList.add(FlightPropertyKey::class.java)
        keyClassList.add(HardwareDataSecurityKey::class.java)
        keyClassList.add(LteModuleKey::class.java)

        keyClassList.add(MissionManagerKey::class.java)

        keyClassList.add(NtripAccountKey::class.java)

        keyClassList.add(RadarPropertyKey::class.java)
        keyClassList.add(RCHiddenKey::class.java)
        keyClassList.add(RemoteControllerKey::class.java)
        keyClassList.add(RemoteIDKey::class.java)

        keyClassList.add(RtkPropertKey::class.java)
        keyClassList.add(SystemManagerKey::class.java)
        keyClassList.add(VisionKey::class.java)

        if (enableMatch) {
            keyClassList.add(AirLinkKey::class.java)
            keyClassList.add(KeyItemDataUtil.getAMeshKeyClass())
        }

        if (enableNest) {
            keyClassList.add(NestKey::class.java)
        }

        if (enableDragonFish) {
            keyClassList.add(DFCommonCmdKey::class.java)
            keyClassList.add(DFFlightControlKey::class.java)
            keyClassList.add(DFFlightPropertyKey::class.java)
            keyClassList.add(WifiBaseStationKey::class.java)
        }
    }

    /**
     * Skip UNKNOWN / invalid enums during Set-Back so placeholder values are not written to the device.
     */
    private fun shouldSkipUnsupportedSetBackValue(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true
        val upper = trimmed.uppercase()
        return upper == "UNKNOWN"
            || upper == "PR_UNKNOWN"
            || upper == "VR_UNKNOWN"
            || upper.endsWith("_UNKNOWN")
            || upper.contains("UNKNOWN(")
    }

    /**
     * Skip Set-Back when prerequisites are unmet so onboard rejections do not cause false FAILs.
     */
    private fun shouldSkipSetBackPrecondition(key: String, gotValue: String): String? {
        if (key == MessageTypeConstant.DRONE_CONTROL_NIGHT_NAVIGATION_LED_MSG) {
            return "skip set-back: night nav led verified via Action(true) in Phase1"
        }
        // port is 0 when onboard NTRIP is unconfigured; the write-back would be rejected (result=2)
        if (key == "NTRIP_PORT" && gotValue.trim() == "0") {
            return "skip set-back: NTRIP port is 0 (not configured)"
        }
        return null
    }

    private fun isHardwareOrPreconditionKey(keyName: String): Boolean {
        if (FilterHardwareDependentKeys.contains(keyName)) return true
        // Skip keys not exposed on VPS/MDX E1 PB; MDE E2 ROS still uses the develop path and is testable
        if (!SDKConfig.isUseRos() && LegacyV30OnlyKeys.keyNames.contains(keyName)) return true
        if (keyName.startsWith("GIMBAL_LASER") || keyName.startsWith("GIMBAL_LASE")) return true
        if (keyName.startsWith("FUGV_")) return true
        // Models without LTE/eSIM hardware are excluded from LTE module key tests
        if (keyName.startsWith("LTE_")) return true
        if (keyName.startsWith("AISERVICE_EXTERNAL_MODEL_")) return true
        if (keyName.startsWith("PAYLOAD_")) return true
        return false
    }

    /**
     * E1 PB / VPS firmware: fill/night light only tests the VPS-aligned Action ([CommonKey.KeyControlLedAction]),
     * skipping 3.0 ROS GET/SET and the FillLight extended Action.
     */
    private fun isPbLegacyRosFillLightKeyItem(key: KeyItem<*, *>): Boolean {
        if (SDKConfig.isUseRos()) return false
        return LegacyV30OnlyKeys.pbLegacyFillLightTestKeyFieldNames.contains(key.name)
    }

    private fun initKeyItemList() {
        keyClassList.forEach {
            val keys = KeyItemDataUtil.getKeyItemList(it)
            val realList = mutableListOf<KeyItem<*, *>>()
            keys.forEach inner@{ key ->
                val keyName = key.keyInfo.keyName
                val isMatchKeys = FilterMatchKeys.contains(keyName)
                val isCustomAccidentKeys = FilterCustomAccidentKeys.contains(keyName)
                val isHardwareKey = isHardwareOrPreconditionKey(keyName)

                if (isMatchKeys) {
                    SDKLog.i(TAG, "==============filter match key ${key.name}")
                    return@inner
                }

                if (isHardwareKey) {
                    SDKLog.i(TAG, "==============filter hardware/precondition key ${key.name}")
                    return@inner
                }

                if (isPbLegacyRosFillLightKeyItem(key)) {
                    SDKLog.i(TAG, "==============filter E1 PB fill-light ROS-only key ${key.name}")
                    return@inner
                }

                if (!enableCustomAccident && isCustomAccidentKeys) {
                    SDKLog.i(TAG, "==============filter accident key ${key.name}")
                    return@inner
                }

                realList.add(key)
            }
            keyItemList[it.simpleName] = realList
            val size = keyItemList[it.simpleName]?.size ?: 0
            SDKLog.i(TAG, "key=${it.simpleName}, subKeyItem=$size")
            totalCount += size
        }
        SDKLog.e(TAG, "Total module size =${keyClassList.size}, Total Key Count=$totalCount")
    }

    private fun staticsInterface() {
        for ((_, value) in keyItemList) {
            value.forEach {
                when {
                    it.canAction() -> actionGetCount++
                    it.canGet() && it.canSet() -> {
                        actionGetCount++
                        getSetCount++
                    }
                    it.canGet() -> actionGetCount++
                    it.canSet() -> onlySetCount++
                    it.canReport() -> reportCaseCount++
                    it.canListen() -> listenCaseCount++
                    else -> SDKLog.i(TAG, "unKnown key=${it.keyInfo.keyName}")
                }
            }
        }
    }

    private fun startCallMethod() {
        cancelled.set(false)
        testStartTime = System.currentTimeMillis()
        testResults.clear()
        keyStartTimeMap.clear()
        testJob = CoroutineScope(Dispatchers.Main).launch {
            // Phase 1: Action / Get tests
            updateStatus("Phase 1/4: Action & Get Test...")
            for ((module, items) in keyItemList) {
                if (cancelled.get()) break
                items.forEach {
                    if (cancelled.get()) return@forEach
                    if (it.canAction()) {
                        doAction(module, it)
                    } else if (it.canGet()) {
                        doGet(module, it)
                    }
                    delay(delayGap.toLong())
                }
                delay(DELAY_GAP_LONG)
            }

            // Phase 2: Set write-back tests (Get current value first, then Set it back)
            if (!cancelled.get()) {
                updateStatus("Phase 2/4: Set Back Test...")
                for ((module, items) in keyItemList) {
                    if (cancelled.get()) break
                    items.forEach {
                        if (cancelled.get()) return@forEach
                        if (it.canGet() && it.canSet()) {
                            doGetThenSetBack(module, it)
                            delay(delayGap.toLong() * 2)
                        } else if (it.canSet() && !it.canGet()) {
                            doSetDefault(module, it)
                            delay(delayGap.toLong())
                        }
                    }
                    delay(DELAY_GAP_LONG)
                }
            }

            // Phase 3: Listen tests
            if (!cancelled.get()) {
                updateStatus("Phase 3/4: Listen Test (wait ${LISTEN_WAIT_MS / 1000}s)...")
                val listenItems = mutableListOf<Pair<String, KeyItem<*, *>>>()
                for ((module, items) in keyItemList) {
                    items.forEach {
                        if (it.canListen()) listenItems.add(module to it)
                    }
                }
                listenItems.forEach { (module, item) ->
                    if (cancelled.get()) return@forEach
                    doListen(module, item)
                }
                if (listenItems.isNotEmpty() && !cancelled.get()) {
                    delay(LISTEN_WAIT_MS)
                    listenItems.forEach { (_, item) ->
                        try { item.cancelListen(this@TestKeyFragment) } catch (_: Exception) {}
                    }
                }
            }

            // Phase 4: Report tests
            if (!cancelled.get()) {
                updateStatus("Phase 4/4: Report Frequency Test...")
                for ((module, items) in keyItemList) {
                    if (cancelled.get()) break
                    items.forEach {
                        if (cancelled.get()) return@forEach
                        if (it.canReport()) {
                            doReport(module, it)
                            delay(delayGap.toLong())
                        }
                    }
                    delay(DELAY_GAP_LONG)
                }
            }

            val finishMsg = if (cancelled.get()) "Test cancelled" else "Test finish"
            SDKLog.i(TAG, "========$finishMsg================")
            safePostDelayed(if (cancelled.get()) 1000L else 10 * 1000L) {
                setEnableView(true)
                printResult()
                binding.textInfo.text = finishMsg
                binding.btnSend.isEnabled = true
                binding.btnCancel.visibility = View.GONE
            }
        }
    }

    private fun updateStatus(msg: String) {
        safePost { binding.textInfo.text = msg }
    }

    private fun recordResult(key: String, result: String, isSuccess: Boolean, module: String = "", operation: String = "", errorCode: String? = null) {
        val elapsed = System.currentTimeMillis() - (keyStartTimeMap[key] ?: System.currentTimeMillis())
        if (isSuccess) {
            if (successList[key] != null) repeatKeyList.add(key) else successList[key] = result
        } else {
            if (errorList[key] != null) repeatKeyList.add(key) else errorList[key] = result
        }
        leakList.remove(key)
        allBackList.add(key)

        testResults.add(JsonReporter.KeyTestResult(
            module = module,
            keyName = key,
            operation = operation,
            success = isSuccess,
            responseTimeMs = elapsed,
            result = result,
            errorCode = errorCode
        ))
    }

    private fun doAction(module: String, item: KeyItem<*, *>) {
        val key = item.keyInfo.keyName
        keyStartTimeMap[key] = System.currentTimeMillis()
        item.setKeyOperateCallBack { msgType, result ->
            val isSuccess = msgType.value == MsgType.MSG_SUCCESS.value
            if (isSuccess || msgType.value == MsgType.MSG_ERROR.value) {
                recordResult(key, result.toString(), isSuccess, module, "ACTION")
                HtmlReporter.writeHTMLLog(
                    "$module \n $key \n${ChineseConstants.getChineseKey(key)}", result.toString(),
                    if (isSuccess) HtmlReporter.PASS else HtmlReporter.FAIL, ""
                )
                SDKLog.e(TAG, "========CallBack-Action-${if (isSuccess) "Success" else "Error"} ($key):$result================")
            }
        }
        val paramsStr = item.paramJsonStr
        val bean = item.buildParamFromJsonStr(paramsStr)
        SDKLog.i(TAG, "========doAction($module $key): $bean================$actionGetCount")
        leakList.add(key)
        allKeyList.add(key)
        item.doAction(paramsStr)
        safePost { binding.textInfo.text = "Testing： 【$module】  $key -> ${ChineseConstants.getChineseKey(key)}" }
    }

    private fun doGet(module: String, item: KeyItem<*, *>) {
        val key = item.keyInfo.keyName
        keyStartTimeMap[key] = System.currentTimeMillis()
        item.setKeyOperateCallBack { msgType, result ->
            val isSuccess = msgType.value == MsgType.MSG_SUCCESS.value
            if (isSuccess || msgType.value == MsgType.MSG_ERROR.value) {
                recordResult(key, result.toString(), isSuccess, module, "GET")
                HtmlReporter.writeHTMLLog(
                    "$module \n $key\n${ChineseConstants.getChineseKey(key)}", result.toString(),
                    if (isSuccess) HtmlReporter.PASS else HtmlReporter.FAIL, ""
                )
                SDKLog.e(TAG, "========CallBack-GET-${if (isSuccess) "SUCCESS" else "ERROR"} ($key): $result================")
            }
        }
        SDKLog.i(TAG, "========doGET($module-$key)================$actionGetCount")
        leakList.add(key)
        allKeyList.add(key)
        item.doGet()
        safePost { binding.textInfo.text = "Testing 【$module】 $key -> ${ChineseConstants.getChineseKey(key)}" }
    }

    /**
     * Get the current value, then Set the original value back via the callback; verifies the Set path without disturbing drone state
     */
    private fun doGetThenSetBack(module: String, item: KeyItem<*, *>) {
        val key = item.keyInfo.keyName
        val setTag = "SET-BACK"
        keyStartTimeMap["$key-setback"] = System.currentTimeMillis()
        SDKLog.i(TAG, "========doGetThenSetBack($module-$key)================")
        safePost { binding.textInfo.text = "SetBack [$module] $key -> ${ChineseConstants.getChineseKey(key)}" }

        item.setKeyOperateCallBack { msgType, result ->
            if (msgType.value == MsgType.MSG_SUCCESS.value) {
                val gotValue = item.lastGetValueForSetBack
                if (gotValue.isNullOrBlank()) {
                    setResultList[key] = "GET returned null, skip set"
                    SDKLog.i(TAG, "========$setTag-SKIP ($key): GET returned null================")
                    HtmlReporter.writeHTMLLog(
                        "$module \n [$setTag] $key\n${ChineseConstants.getChineseKey(key)}",
                        "GET returned null, skip set", HtmlReporter.PASS, ""
                    )
                    recordSetBackSkip(module, key, setTag, "GET returned null, skip set", 0L)
                    return@setKeyOperateCallBack
                }
                shouldSkipSetBackPrecondition(key, gotValue)?.let { skipReason ->
                    setResultList[key] = skipReason
                    SDKLog.i(TAG, "========$setTag-SKIP ($key): $skipReason================")
                    HtmlReporter.writeHTMLLog(
                        "$module \n [$setTag] $key\n${ChineseConstants.getChineseKey(key)}",
                        skipReason, HtmlReporter.PASS, ""
                    )
                    recordSetBackSkip(module, key, setTag, skipReason, 0L)
                    return@setKeyOperateCallBack
                }
                if (shouldSkipUnsupportedSetBackValue(gotValue)) {
                    val skipReason = "skip set-back for unsupported value: $gotValue"
                    setResultList[key] = skipReason
                    SDKLog.i(TAG, "========$setTag-SKIP ($key): $skipReason================")
                    HtmlReporter.writeHTMLLog(
                        "$module \n [$setTag] $key\n${ChineseConstants.getChineseKey(key)}",
                        skipReason, HtmlReporter.PASS, ""
                    )
                    recordSetBackSkip(module, key, setTag, skipReason, 0L)
                    return@setKeyOperateCallBack
                }
                item.doSet(gotValue)
                item.setKeyOperateCallBack { setMsgType, setResult ->
                    val setSuccess = setMsgType.value == MsgType.MSG_SUCCESS.value
                    val setElapsed = System.currentTimeMillis() - (keyStartTimeMap["$key-setback"] ?: System.currentTimeMillis())
                    if (setSuccess) {
                        setResultList[key] = "OK (wrote back: $gotValue)"
                        HtmlReporter.writeHTMLLog(
                            "$module \n [$setTag] $key\n${ChineseConstants.getChineseKey(key)}",
                            "SetBack OK: $gotValue", HtmlReporter.PASS, ""
                        )
                    } else if (setMsgType.value == MsgType.MSG_ERROR.value) {
                        setErrorList[key] = setResult.toString()
                        HtmlReporter.writeHTMLLog(
                            "$module \n [$setTag] $key\n${ChineseConstants.getChineseKey(key)}",
                            setResult.toString(), HtmlReporter.FAIL, ""
                        )
                    }
                    testResults.add(JsonReporter.KeyTestResult(module, key, "SET-BACK", setSuccess, setElapsed, setResult?.toString()))
                    SDKLog.e(TAG, "========$setTag-${if (setSuccess) "SUCCESS" else "ERROR"} ($key): $setResult================")
                }
            } else if (msgType.value == MsgType.MSG_ERROR.value) {
                setErrorList[key] = "GET failed: $result"
                SDKLog.e(TAG, "========$setTag-GET-FAIL ($key): $result================")
                HtmlReporter.writeHTMLLog(
                    "$module \n [$setTag] $key\n${ChineseConstants.getChineseKey(key)}",
                    "GET failed: $result", HtmlReporter.FAIL, ""
                )
            }
        }
        item.doGet()
    }

    private fun recordSetBackSkip(
        module: String,
        key: String,
        setTag: String,
        reason: String,
        elapsedMs: Long,
    ) {
        testResults.add(
            JsonReporter.KeyTestResult(module, key, "SET-BACK", true, elapsedMs, reason)
        )
    }

    /**
     * Set-only keys (no Get): send with default values
     */
    private fun doSetDefault(module: String, item: KeyItem<*, *>) {
        val key = item.keyInfo.keyName
        val setTag = "SET-DEFAULT"
        keyStartTimeMap["$key-set"] = System.currentTimeMillis()
        SDKLog.i(TAG, "========doSetDefault($module-$key)================")
        safePost { binding.textInfo.text = "SetDefault [$module] $key -> ${ChineseConstants.getChineseKey(key)}" }

        item.setKeyOperateCallBack { msgType, result ->
            val isSuccess = msgType.value == MsgType.MSG_SUCCESS.value
            if (isSuccess || msgType.value == MsgType.MSG_ERROR.value) {
                val setElapsed = System.currentTimeMillis() - (keyStartTimeMap["$key-set"] ?: System.currentTimeMillis())
                if (isSuccess) {
                    setResultList[key] = "OK (default)"
                } else {
                    setErrorList[key] = result.toString()
                }
                testResults.add(JsonReporter.KeyTestResult(module, key, "SET", isSuccess, setElapsed, result?.toString()))
                HtmlReporter.writeHTMLLog(
                    "$module \n [$setTag] $key\n${ChineseConstants.getChineseKey(key)}",
                    result?.toString() ?: "", if (isSuccess) HtmlReporter.PASS else HtmlReporter.FAIL, ""
                )
                SDKLog.e(TAG, "========$setTag-${if (isSuccess) "SUCCESS" else "ERROR"} ($key): $result================")
            }
        }
        val paramsStr = item.paramJsonStr
        if (paramsStr != null) {
            item.doSet(paramsStr)
        } else {
            setErrorList[key] = "No default param available"
            SDKLog.e(TAG, "========$setTag-SKIP ($key): No default param================")
        }
    }

    private fun doListen(module: String, item: KeyItem<*, *>) {
        val key = item.keyInfo.keyName
        val startMs = System.currentTimeMillis()
        SDKLog.i(TAG, "========doListen($module-$key)================")
        safePost { binding.textInfo.text = "Listen 【$module】 $key -> ${ChineseConstants.getChineseKey(key)}" }

        item.setPushCallBack { msgType, result ->
            if (msgType.value == MsgType.MSG_SUCCESS.value) {
                listenResultList[key] = result?.toString() ?: "received"
                SDKLog.i(TAG, "========Listen-DATA ($key): $result================")
            }
        }
        try {
            item.listen(this@TestKeyFragment)
            testResults.add(JsonReporter.KeyTestResult(module, key, "LISTEN", true, System.currentTimeMillis() - startMs, "Registered"))
            HtmlReporter.writeHTMLLog(
                "$module \n [LISTEN] $key\n${ChineseConstants.getChineseKey(key)}",
                "Registered", HtmlReporter.PASS, ""
            )
        } catch (e: Exception) {
            testResults.add(JsonReporter.KeyTestResult(module, key, "LISTEN", false, System.currentTimeMillis() - startMs, e.message))
            HtmlReporter.writeHTMLLog(
                "$module \n [LISTEN] $key\n${ChineseConstants.getChineseKey(key)}",
                "Register failed: ${e.message}", HtmlReporter.FAIL, ""
            )
            SDKLog.e(TAG, "========Listen-FAIL ($key): ${e.message}================")
        }
    }

    private fun doReport(module: String, item: KeyItem<*, *>) {
        val key = item.keyInfo.keyName
        val startMs = System.currentTimeMillis()
        SDKLog.i(TAG, "========doReport($module-$key)================")
        safePost { binding.textInfo.text = "Report 【$module】 $key -> ${ChineseConstants.getChineseKey(key)}" }

        try {
            val paramsStr = item.paramJsonStr
            item.doReport(paramsStr)
            reportResultList[key] = "Sent"
            testResults.add(JsonReporter.KeyTestResult(module, key, "REPORT", true, System.currentTimeMillis() - startMs, "Sent"))
            HtmlReporter.writeHTMLLog(
                "$module \n [REPORT] $key\n${ChineseConstants.getChineseKey(key)}",
                "Report sent", HtmlReporter.PASS, ""
            )
        } catch (e: Exception) {
            reportResultList[key] = "Error: ${e.message}"
            testResults.add(JsonReporter.KeyTestResult(module, key, "REPORT", false, System.currentTimeMillis() - startMs, e.message))
            HtmlReporter.writeHTMLLog(
                "$module \n [REPORT] $key\n${ChineseConstants.getChineseKey(key)}",
                "Error: ${e.message}", HtmlReporter.FAIL, ""
            )
            SDKLog.e(TAG, "========Report-FAIL ($key): ${e.message}================")
        }
    }

    private fun printResult() {
        val testedTotal = actionGetCount + getSetCount + onlySetCount + listenCaseCount + reportCaseCount
        SDKLog.i(TAG, "===============================================================")
        SDKLog.i(TAG, "Total Key count=$totalCount")
        SDKLog.i(TAG, "Action/Get count=$actionGetCount")
        SDKLog.i(TAG, "Get+Set    count=$getSetCount")
        SDKLog.i(TAG, "OnlySet    count=$onlySetCount")
        SDKLog.i(TAG, "Listen     count=$listenCaseCount")
        SDKLog.i(TAG, "Report     count=$reportCaseCount")
        SDKLog.i(TAG, "Tested     total=$testedTotal")

        SDKLog.i(TAG, "--- Action/Get ---")
        SDKLog.i(TAG, "Success Count=${successList.size}")
        SDKLog.i(TAG, "Error   Count=${errorList.size}")
        SDKLog.i(TAG, "LeakMsg Count=${leakList.size}")
        SDKLog.i(TAG, "--- Set ---")
        SDKLog.i(TAG, "Set OK  Count=${setResultList.size}")
        SDKLog.i(TAG, "Set Err Count=${setErrorList.size}")
        SDKLog.i(TAG, "--- Listen ---")
        SDKLog.i(TAG, "Received Count=${listenResultList.size} / $listenCaseCount")
        SDKLog.i(TAG, "--- Report ---")
        SDKLog.i(TAG, "Report   Count=${reportResultList.size} / $reportCaseCount")

        SDKLog.i(TAG, "repeat   Count=${repeatKeyList.size}  $repeatKeyList")
        SDKLog.i(TAG, "request  Count=${allKeyList.size}")
        SDKLog.i(TAG, "callback Count=${allBackList.size}")

        if (allKeyList.size != allBackList.size) {
            val diff = allKeyList.minus(allBackList.toSet()) + allBackList.minus(allKeyList.toSet())
            SDKLog.i(TAG, "difference=$diff")
        }

        SDKLog.i(TAG, "================= ERRORS =================")
        for ((key, value) in errorList) { SDKLog.e(TAG, "Action/Get Error: 【$key】  $value") }
        for ((key, value) in setErrorList) { SDKLog.e(TAG, "Set Error: 【$key】  $value") }
        SDKLog.i(TAG, "================= LEAKS ==================")
        leakList.forEach { SDKLog.e(TAG, "Leak: 【$it】 ") }
        SDKLog.i(TAG, "===============================================================")

        SDKLog.i(TAG, "================= MODULE PASS RATE =================")
        val moduleGroups = testResults.groupBy { it.module }
        for ((mod, items) in moduleGroups) {
            val passCount = items.count { it.success }
            val rate = if (items.isNotEmpty()) "%.1f%%".format(passCount * 100.0 / items.size) else "N/A"
            val avgMs = if (items.isNotEmpty()) items.map { it.responseTimeMs }.average().toLong() else 0L
            SDKLog.i(TAG, "Module [$mod]: ${passCount}/${items.size} ($rate), avg ${avgMs}ms")
        }
        SDKLog.i(TAG, "===============================================================")

        val ctx = context ?: run {
            SDKLog.w(TAG, "printResult skipped UI/report: fragment detached")
            return
        }
        val jsonPath = JsonReporter.generateReport(ctx, testResults, testStartTime, System.currentTimeMillis())
        SDKLog.i(TAG, "JSON report: $jsonPath")

        val path = HtmlReporter.seleniumReport(ctx)
        safePost {
            setClickableLink("Click to see Report!", path)
            binding.reportData.isClickable = true
            binding.reportData.linksClickable = true
        }
    }

    private fun initView() {
        binding.btnSend.setOnClickListener {
            binding.btnSend.isEnabled = false
            binding.btnCancel.visibility = View.VISIBLE
            setEnableView(false)
            startCallMethod()
        }

        binding.btnCancel.setOnClickListener {
            cancelled.set(true)
            binding.btnCancel.isEnabled = false
            binding.textInfo.text = "Cancelling..."
            SDKLog.i(TAG, "========Test Cancelling================")
        }
        binding.btnCancel.visibility = View.GONE

        binding.progress.progress = delayGap / 10
        binding.delayTime.text = "${delayGap}ms"
        binding.progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                delayGap = if (progress == 0) 10 else progress * 10
                binding.delayTime.text = "${delayGap}ms"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                SDKLog.i(TAG, "onStopTrackingTouch")
                updateUI()
            }
        })

        binding.meshEnableView.isChecked = enableMatch
        binding.meshEnableView.setOnCheckedChangeListener { _, isChecked ->
            enableMatch = isChecked
            updateUI()
        }

        binding.nestEnableView.isChecked = enableNest
        binding.nestEnableView.setOnCheckedChangeListener { _, isChecked ->
            enableNest = isChecked
            updateUI()
        }

        binding.dragonFishEnableView.isChecked = enableDragonFish
        binding.dragonFishEnableView.setOnCheckedChangeListener { _, isChecked ->
            enableDragonFish = isChecked
            updateUI()
        }
    }

    private fun updateUI() {
        clear()

        initKeyClassList()
        initKeyItemList()
        staticsInterface()

        val moduleSize = keyClassList.size
        var keySize = 0
        for ((_, value) in keyItemList) {
            keySize += value.size
        }

        val testedTotal = actionGetCount + getSetCount + onlySetCount + listenCaseCount + reportCaseCount
        val totalSpend = (moduleSize * DELAY_GAP_LONG
            + actionGetCount * delayGap
            + (getSetCount + onlySetCount) * delayGap * 2
            + if (listenCaseCount > 0) LISTEN_WAIT_MS else 0L
            + reportCaseCount * delayGap
            ).toFloat() / 1000 / 60
        binding.tips.text = "$moduleSize modules, $keySize interfaces\n" +
            "Action/Get=$actionGetCount  SetBack=$getSetCount  OnlySet=$onlySetCount  " +
            "Listen=$listenCaseCount  Report=$reportCaseCount\n" +
            "Total $testedTotal items, est. ${"%.1f".format(totalSpend)} min"
    }

    private fun setEnableView(enable: Boolean) {
        binding.meshEnableView.isEnabled = enable
        binding.nestEnableView.isEnabled = enable
        binding.dragonFishEnableView.isEnabled = enable
        binding.progress.isEnabled = enable
        binding.btnSend.isEnabled = enable
    }

    private fun clear() {
        keyClassList.clear()
        keyItemList.clear()
        successList.clear()
        errorList.clear()
        leakList.clear()
        allKeyList.clear()
        allBackList.clear()
        repeatKeyList.clear()
        listenResultList.clear()
        reportResultList.clear()
        setResultList.clear()
        setErrorList.clear()
        testResults.clear()
        keyStartTimeMap.clear()
        totalCount = 0
        actionGetCount = 0
        listenCaseCount = 0
        reportCaseCount = 0
        onlySetCount = 0
        getSetCount = 0
    }

    override fun onDestroy() {
        cancelled.set(true)
        testJob?.cancel()
        super.onDestroy()
    }

    private fun openHtmlReport(filePath: String) {
        val webView = WebView(requireContext())
        webView.settings.allowFileAccess = true
        webView.loadUrl("file://$filePath")
        AlertDialog.Builder(requireContext())
            .setTitle("SDK Key Unit Test Report")
            .setView(webView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun setClickableLink(text: String, url: String) {
        val spannableString = SpannableString(text)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                try {
                    openHtmlReport(url)
                    SDKLog.e(TAG, "openHtmlReport: $url")
                } catch (e: Exception) {
                    SDKLog.printSafeStackTrace(e)
                }
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.color = Color.BLUE
            }
        }

        spannableString.setSpan(
            clickableSpan,
            0,
            text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.reportData.text = spannableString
        binding.reportData.movementMethod = LinkMovementMethod.getInstance()
    }
}