package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.v2.interfaces.IEsimManager
import com.autel.drone.sdk.vmodelx.SDKManager
import com.autel.drone.sdk.vmodelx.device.IAutelDroneListener
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.LteModuleKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.lte.EsimProtocol
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.lte.bean.EsimProfileBean
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentEsimBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EsimFragment : AutelFragment(), IAutelDroneListener {

    private lateinit var binding: FragmentEsimBinding
    private var droneDevice: IAutelDroneDevice? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logBuilder = StringBuilder()
    private var advancedExpanded = false
    private var guideExpanded = false
    private val profileList = mutableListOf<EsimProfileBean>()
    private val profileDisplayList = mutableListOf<String>()
    private var iccidAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEsimBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SDKManager.get().getDeviceManager().addDroneListener(this)
        droneDevice = SDKManager.get().getDeviceManager().getFirstDroneDevice()
        initViews()
    }

    private fun initViews() {
        binding.btnRefreshStatus.setOnClickListener { doRefreshAll() }

        binding.btnSwitchToSim.setOnClickListener { doSwitchSlot(SLOT_SIM) }
        binding.btnSwitchToEsim.setOnClickListener { doSwitchSlot(SLOT_ESIM) }

        profileDisplayList.add(getString(R.string.esim_no_profile))
        iccidAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, profileDisplayList)
        iccidAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerIccid.adapter = iccidAdapter

        binding.btnGetProfiles.setOnClickListener { doGetProfiles() }
        binding.btnEnableProfile.setOnClickListener { doEnableProfile() }
        binding.btnDisableProfile.setOnClickListener { doDisableProfile() }
        binding.btnDeleteProfile.setOnClickListener { doDeleteProfile() }

        val serverDisplayList = SMDP_SERVERS.map { "${it.second}\n${it.first}" }
        val serverAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, serverDisplayList)
        serverAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSmdpServer.adapter = serverAdapter

        binding.btnDownloadProfile.setOnClickListener { doDownloadProfile() }

        val apduDisplayList = APDU_PRESETS.map { it.name }
        val apduAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, apduDisplayList)
        apduAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerApduPresets.adapter = apduAdapter
        binding.spinnerApduPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cmd = APDU_PRESETS[position].commands
                if (cmd.isNotEmpty()) {
                    binding.etRawApdu.setText(cmd)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnSendRawApdu.setOnClickListener { doSendRawApdu() }
        binding.btnClearLog.setOnClickListener { clearLog() }

        binding.layoutAdvancedHeader.setOnClickListener { toggleAdvanced() }
        binding.layoutGuideHeader.setOnClickListener { toggleGuide() }
    }

    // ======================= Fold switch =======================

    private fun toggleAdvanced() {
        advancedExpanded = !advancedExpanded
        binding.layoutAdvancedContent.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        binding.tvAdvancedToggle.setText(
            if (advancedExpanded) R.string.esim_toggle_hide else R.string.esim_toggle_show
        )
    }

    private fun toggleGuide() {
        guideExpanded = !guideExpanded
        binding.tvGuideContent.visibility = if (guideExpanded) View.VISIBLE else View.GONE
        binding.tvGuideToggle.setText(
            if (guideExpanded) R.string.esim_toggle_hide else R.string.esim_toggle_show
        )
    }

    // ======================= Refresh =======================

    private fun doRefreshAll() {
        appendLog(">> Refresh ...")
        doGetEid()
        doGetCardDetect()
        doGetProfiles()
    }

    private fun doGetEid() {
        val mgr = getEsimManager() ?: return
        mgr.getEid(object : CommonCallbacks.CompletionCallbackWithParam<String> {
            override fun onSuccess(t: String?) {
                appendLog("<< EID = $t")
                safePost { binding.tvEid.text = "EID: ${t ?: "--"}" }
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                appendLog("<< GetEID FAILED: code=${error.code}, msg=$msg")
            }
        })
    }

    private fun doGetCardDetect() {
        val device = ensureDevice() ?: return
        val key = KeyTools.createKey(LteModuleKey.LTE_CARD_DETECT)
        device.getKeyManager().getValue(key,
            object : CommonCallbacks.CompletionCallbackWithParam<Int> {
                override fun onSuccess(t: Int?) {
                    val desc = when (t) {
                        0 -> "No card"
                        1 -> "SIM"
                        2 -> "eSIM"
                        else -> "Unknown($t)"
                    }
                    appendLog("<< Card = $t ($desc)")
                    safePost { binding.tvCardStatus.text = "Card: $desc" }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    appendLog("<< Card Detect FAILED: code=${error.code}, msg=$msg")
                }
            })
    }

    // ======================= SIM slot switch =======================

    private fun doSwitchSlot(targetSlot: Int) {
        val device = ensureDevice() ?: return
        val slotName = if (targetSlot == SLOT_SIM) "SIM" else "eSIM"
        appendLog(">> Switch slot to $slotName ($targetSlot) ...")
        appendLog("   Module will hard-reset, ~15s to recover")

        val key = KeyTools.createKey(LteModuleKey.LTE_CARD_DETECT)
        device.getKeyManager().setValue(key, targetSlot,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    appendLog("<< Switch command SUCCESS")
                    appendLog("   Waiting 15s for module reset ...")
                    Thread {
                        try {
                            Thread.sleep(15_000)
                        } catch (_: InterruptedException) {}
                        appendLog(">> Verifying card detect ...")
                        doGetCardDetect()
                    }.start()
                }

                override fun onFailure(code: IAutelCode, msg: String?) {
                    appendLog("<< Switch to $slotName FAILED: code=${code.code}, msg=$msg")
                }
            })
    }

    // ======================= Profile management =======================

    private fun doGetProfiles() {
        val mgr = getEsimManager() ?: return
        appendLog(">> Get Profiles ...")
        mgr.getProfiles(object : CommonCallbacks.CompletionCallbackWithParam<List<EsimProfileBean>> {
            override fun onSuccess(t: List<EsimProfileBean>?) {
                if (t.isNullOrEmpty()) {
                    appendLog("<< No profiles found")
                    safePost {
                        binding.tvActiveProfile.text = getString(R.string.esim_active_profile_default)
                        updateProfileSpinner(emptyList())
                    }
                    return
                }
                appendLog("<< Found ${t.size} profile(s):")
                var activeIccid: String? = null
                var activeIndex = -1
                t.forEachIndexed { i, p ->
                    val stateStr = if (p.isEnabled()) "[ACTIVE]" else "[disabled]"
                    appendLog("   [$i] $stateStr ICCID=${p.iccid} provider=${p.provider} nickname=${p.nickname}")
                    if (p.isEnabled()) {
                        activeIccid = p.iccid
                        activeIndex = i
                    }
                }
                safePost {
                    binding.tvActiveProfile.text = if (activeIccid != null) {
                        "Active Profile: $activeIccid"
                    } else {
                        getString(R.string.esim_active_profile_default)
                    }
                    updateProfileSpinner(t)
                    if (activeIndex >= 0) {
                        binding.spinnerIccid.setSelection(activeIndex)
                    }
                }
            }

            override fun onFailure(error: IAutelCode, msg: String?) {
                appendLog("<< GetProfiles FAILED: code=${error.code}, msg=$msg")
            }
        })
    }

    private fun updateProfileSpinner(profiles: List<EsimProfileBean>) {
        profileList.clear()
        profileDisplayList.clear()
        if (profiles.isEmpty()) {
            profileDisplayList.add(getString(R.string.esim_no_profile))
        } else {
            profileList.addAll(profiles)
            profiles.forEach { p ->
                val state = if (p.isEnabled()) " [ACTIVE]" else ""
                val label = when {
                    p.provider.isNotEmpty() -> " (${p.provider})"
                    p.nickname.isNotEmpty() -> " (${p.nickname})"
                    else -> ""
                }
                profileDisplayList.add("${p.iccid}$label$state")
            }
        }
        iccidAdapter?.notifyDataSetChanged()
    }

    private fun getSelectedIccid(): String? {
        val pos = binding.spinnerIccid.selectedItemPosition
        if (pos < 0 || pos >= profileList.size) {
            appendLog("!! " + getString(R.string.esim_select_profile_first))
            return null
        }
        return profileList[pos].iccid
    }

    private fun doEnableProfile() {
        val mgr = getEsimManager() ?: return
        val iccid = getSelectedIccid() ?: return
        appendLog(">> Enable Profile: $iccid ...")
        mgr.enableProfile(iccid, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                appendLog("<< Enable Profile SUCCESS")
                safePost { binding.tvActiveProfile.text = "Active Profile: $iccid" }
            }

            override fun onFailure(code: IAutelCode, msg: String?) {
                val friendly = parseProfileHint(msg)
                appendLog("<< Enable: $friendly")
            }
        })
    }

    private fun doDisableProfile() {
        val mgr = getEsimManager() ?: return
        val iccid = getSelectedIccid() ?: return
        appendLog(">> Disable Profile: $iccid ...")
        mgr.disableProfile(iccid, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                appendLog("<< Disable Profile SUCCESS")
            }

            override fun onFailure(code: IAutelCode, msg: String?) {
                val friendly = parseProfileHint(msg)
                appendLog("<< Disable: $friendly")
            }
        })
    }

    private fun doDeleteProfile() {
        val mgr = getEsimManager() ?: return
        val iccid = getSelectedIccid() ?: return
        appendLog(">> Delete Profile: $iccid ...")
        mgr.deleteProfile(iccid, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                appendLog("<< Delete Profile SUCCESS")
            }

            override fun onFailure(code: IAutelCode, msg: String?) {
                appendLog("<< Delete FAILED: code=${code.code}, msg=$msg")
            }
        })
    }

    // ======================= Number writing =======================

    @Volatile
    private var isDownloading = false

    private fun doDownloadProfile() {
        if (isDownloading) {
            appendLog("!! Download already in progress, please wait")
            return
        }
        val mgr = getEsimManager() ?: return
        val rawInput = binding.etMatchingId.text.toString().trim()
        if (rawInput.isEmpty()) {
            appendLog("!! " + getString(R.string.esim_matching_id_required))
            return
        }

        val input: String
        if (rawInput.uppercase().startsWith("LPA:")) {
            input = rawInput
            appendLog(">> Auto-detected full activation code")
        } else {
            val serverPos = binding.spinnerSmdpServer.selectedItemPosition
            if (serverPos < 0 || serverPos >= SMDP_SERVERS.size) {
                appendLog("!! Please select SM-DP+ server")
                return
            }
            val server = SMDP_SERVERS[serverPos].first
            input = "LPA:1\$$server\$$rawInput"
        }

        isDownloading = true
        safePost { binding.btnDownloadProfile.isEnabled = false }
        appendLog(">> Download Profile ...")
        appendLog("   Code: $input")
        appendLog("   (~30s ~ 2min ...)")

        mgr.downloadProfile(input,
            object : CommonCallbacks.CompletionCallbackWithParam<IEsimManager.DownloadProfileResult> {
                override fun onSuccess(t: IEsimManager.DownloadProfileResult?) {
                    isDownloading = false
                    safePost { binding.btnDownloadProfile.isEnabled = true }
                    if (t?.success == true) {
                        appendLog("<< Download SUCCESS!")
                        appendLog("   Click [Refresh] to verify.")
                    } else {
                        val readable = EsimProtocol.translateSgp22Error(t?.subjectCode, t?.reasonCode)
                        appendLog("<< Download FAILED (SM-DP+ rejected)")
                        appendLog("   $readable")
                        appendLogSgp22Hint(t?.subjectCode, t?.reasonCode)
                    }
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    isDownloading = false
                    safePost { binding.btnDownloadProfile.isEnabled = true }
                    appendLog("<< Download FAILED: code=${error.code}, msg=$msg")
                    if (msg != null && msg.contains("sub=") && msg.contains("reason=")) {
                        val sub = Regex("sub=(\\S+)").find(msg)?.groupValues?.get(1)
                        val reason = Regex("reason=(\\S+)").find(msg)?.groupValues?.get(1)
                        val readable = EsimProtocol.translateSgp22Error(sub, reason)
                        appendLog("   $readable")
                        appendLogSgp22Hint(sub, reason)
                    }
                }
            })
    }

    private fun appendLogSgp22Hint(subjectCode: String?, reasonCode: String?) {
        if (subjectCode == "8.2" && reasonCode == "1.2") {
            appendLog("   ----")
            appendLog("   HINT: This profile was previously downloaded.")
            appendLog("   The SM-DP+ server requires a delete notification before re-download.")
            appendLog("   Try: 1) Delete the old profile first, or 2) Use a new activation code.")
        }
    }

    // ======================= Raw APDU =======================

    private fun doSendRawApdu() {
        val mgr = getEsimManager() ?: return
        val input = binding.etRawApdu.text.toString().trim()
        if (input.isEmpty()) {
            appendLog("!! Please enter hex APDU")
            return
        }
        val apdus = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        appendLog(">> Send Raw APDU (${apdus.size}) ...")
        apdus.forEachIndexed { i, apdu -> appendLog("   [$i] >> $apdu") }

        mgr.sendRawApduSession(apdus,
            object : CommonCallbacks.CompletionCallbackWithParam<List<String>> {
                override fun onSuccess(t: List<String>?) {
                    t?.forEachIndexed { i, resp -> appendLog("   [$i] << $resp") }
                    appendLog("<< Raw APDU complete")
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    appendLog("<< Raw APDU FAILED: code=${error.code}, msg=$msg")
                }
            })
    }

    // ======================= Device listeners =======================

    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        super.onDroneChangedListener(connected, drone)
        if (connected) {
            droneDevice = drone
            appendLog("Device connected: ${drone.toSampleString()}")
        } else {
            droneDevice = null
            appendLog("Device disconnected")
            safePost {
                binding.tvEid.text = getString(R.string.esim_eid_default)
                binding.tvCardStatus.text = getString(R.string.esim_card_default)
                binding.tvActiveProfile.text = getString(R.string.esim_active_profile_default)
            }
        }
    }

    // ======================= Utilities =======================

    private fun ensureDevice(): IAutelDroneDevice? {
        droneDevice = droneDevice ?: SDKManager.get().getDeviceManager().getFirstDroneDevice()
        if (droneDevice == null) appendLog("!! No device connected")
        return droneDevice
    }

    private fun getEsimManager(): IEsimManager? {
        val device = ensureDevice() ?: return null
        return try {
            device.getEsimManager()
        } catch (e: NoSuchMethodError) {
            appendLog("!! getEsimManager() not available")
            appendLog("   Rebuild expose module from source")
            null
        }
    }

    private fun appendLog(msg: String) {
        val timestamp = timeFormat.format(Date())
        val line = "[$timestamp] $msg"
        SDKLog.d(TAG, msg)
        safePost {
            logBuilder.append(line).append("\n")
            binding.tvLog.text = logBuilder.toString()
            binding.scrollLog.post { binding.scrollLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun clearLog() {
        logBuilder.clear()
        binding.tvLog.text = ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        SDKManager.get().getDeviceManager().removeDroneListener(this)
    }

    private fun parseProfileHint(msg: String?): String {
        if (msg == null) return "Unknown error"
        val respIdx = msg.indexOf("response: ")
        if (respIdx < 0) return msg
        val resp = msg.substring(respIdx + "response: ".length).trim()
        val code = EsimProtocol.parseProfileOperationResult(resp)
        return if (code >= 0) EsimProtocol.profileResultToMessage(code) else msg
    }

    companion object {
        private const val SLOT_SIM = 1
        private const val SLOT_ESIM = 2

        private val SMDP_SERVERS = listOf(
            "secsmsminiapp.eastcompeace.com" to "EastcomPeace eSIM",
        )

        private data class ApduPreset(val name: String, val commands: String)
        private val APDU_PRESETS = listOf(
            ApduPreset("-- Select --", ""),
            ApduPreset("OPEN CHANNEL", "0070000001"),
            ApduPreset("SELECT ISD-R", "80AA000005A903830103"),
            ApduPreset("SELECT ISD-P (ch=01)", "01A4040010A0000005591010FFFFFFFF8900000100"),
            ApduPreset("CLOSE CHANNEL (ch=01)", "0070800100"),
        )
    }
}
