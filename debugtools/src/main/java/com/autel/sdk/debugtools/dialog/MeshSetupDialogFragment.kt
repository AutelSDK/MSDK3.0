package com.autel.sdk.debugtools.dialog

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.autel.drone.sdk.vmodelx.interfaces.IMeshDeviceChangedListener
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.sdk.debugtools.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Mesh setup DialogFragment — full-screen dialog
 *
 * ======================================================================
 * Description
 * ======================================================================
 *
 * In the IM sharing Fragment, if the current RC is not networked (meshId empty),
 * tapping the "not networked" hint opens this dialog to guide the user through mesh setup.
 *
 * Two roles are supported:
 * 1. Master (create network) — calls startMeshMatching() and waits for slaves to join
 * 2. Slave (join network) — calls joinNetMesh() to search and join an existing mesh
 *
 * After completion the dialog closes and notifies IMDemoFragment to re-initialize RTM,
 * after which the chat room is created automatically and messaging starts.
 *
 * ======================================================================
 * Key APIs
 * ======================================================================
 *
 * // Create network (master)
 * DeviceManager.getMultiDeviceOperator().getNetMeshManager()
 *     .startMeshMatching(isContinue, maxRcNum, maxDroneNum, callback)
 *
 * // Join network (slave)
 * DeviceManager.getMultiDeviceOperator().getNetMeshManager()
 *     .joinNetMesh(callback)
 *
 * // Complete networking (master confirms)
 * DeviceManager.getMultiDeviceOperator().getNetMeshManager()
 *     .completeMesh(callback)
 *
 * // Cancel creation
 * DeviceManager.getMultiDeviceOperator().getNetMeshManager()
 *     .cancelCreateMesh(callback)
 *
 * // Cancel join
 * DeviceManager.getMultiDeviceOperator().getNetMeshManager()
 *     .cancelJoinMesh(callback)
 *
 * // Listen for mesh device changes
 * DeviceManager.getMultiDeviceOperator().addNetMeshChangeListener(listener)
 *
 * // Get networked device list
 * DeviceManager.getMultiDeviceOperator().getNetMeshManager().getAllMeshDeviceList()
 *
 * // Get MeshID (auto-generated after networking)
 * DeviceManager.getMultiDeviceOperator().getNetMeshManager().getNetId()
 *
 * ======================================================================
 */
class MeshSetupDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "MeshSetupDialog"

        /** Max RC count (same as NetMeshConfig) */
        private const val MAX_RC_NUM = 8

        /** Max drone count */
        private const val MAX_DRONE_NUM = 8
    }

    // =============================================
    // Views
    // =============================================
    private lateinit var tvTitle: TextView
    private lateinit var tvStatusMsg: TextView
    private lateinit var btnCreate: TextView
    private lateinit var btnJoin: TextView
    private lateinit var llChoicePanel: LinearLayout
    private lateinit var llMatchingPanel: LinearLayout
    private lateinit var tvMatchingStatus: TextView
    private lateinit var llDeviceList: LinearLayout
    private lateinit var btnComplete: TextView
    private lateinit var btnCancel: TextView

    // =============================================
    // State
    // =============================================
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** true = create network (master), false = join network (slave) */
    private var isCreateMode = true

    /** Whether the networking flow has started */
    private var isMeshStarted = false

    /**
     * Callback after networking completes
     * IMDemoFragment sets this callback to receive the completion notification
     */
    var onMeshCompleted: (() -> Unit)? = null

    // =================================================================
    // Fragment lifecycle
    // =================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen, no title bar style
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_mesh_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        registerMeshListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterMeshListener()
        scope.cancel()
    }

    // =================================================================
    // Initialization
    // =================================================================

    private fun initViews(root: View) {
        tvTitle = root.findViewById(R.id.tv_dialog_title)
        tvStatusMsg = root.findViewById(R.id.tv_status_msg)
        btnCreate = root.findViewById(R.id.btn_create_mesh)
        btnJoin = root.findViewById(R.id.btn_join_mesh)
        llChoicePanel = root.findViewById(R.id.ll_choice_panel)
        llMatchingPanel = root.findViewById(R.id.ll_matching_panel)
        tvMatchingStatus = root.findViewById(R.id.tv_matching_status)
        llDeviceList = root.findViewById(R.id.ll_device_list)
        btnComplete = root.findViewById(R.id.btn_complete_mesh)
        btnCancel = root.findViewById(R.id.btn_cancel_mesh)
    }

    private fun setupListeners() {
        // Create network (master)
        btnCreate.setOnClickListener {
            isCreateMode = true
            startCreateMesh()
        }

        // Join network (slave)
        btnJoin.setOnClickListener {
            isCreateMode = false
            startJoinMesh()
        }

        // Complete networking
        btnComplete.setOnClickListener {
            if (isCreateMode) {
                completeMesh()
            } else {
                // Slave: close directly after joining
                onMeshCompleted?.invoke()
                dismiss()
            }
        }

        // Cancel
        btnCancel.setOnClickListener {
            cancelMesh()
        }

        // Back (back button of the selection panel)
        view?.findViewById<TextView>(R.id.btn_back_choice)?.setOnClickListener {
            dismiss()
        }
    }

    // =================================================================
    // Create network (master)
    // =================================================================

    /**
     * Master: create a mesh network
     *
     * After startMeshMatching() the RC enters pairing mode
     * and waits for slave RCs to call joinNetMesh().
     * Device discovery is notified via IMeshDeviceChangedListener.onDiscoverReport().
     * After all devices join, the master taps "Complete" and calls completeMesh().
     */
    private fun startCreateMesh() {
        showMatchingPanel(getString(R.string.mesh_creating_network))

        scope.launch {
            try {
                val result = createMeshSuspend(isContinue = false)
                if (result) {
                    isMeshStarted = true
                    updateMatchingStatus(getString(R.string.mesh_network_created_wait))
                    btnComplete.visibility = View.VISIBLE
                    btnComplete.isEnabled = false // enabled only after a slave joins
                    refreshDeviceList()
                } else {
                    updateMatchingStatus(getString(R.string.mesh_create_network_failed))
                }
            } catch (e: Exception) {
                updateMatchingStatus(getString(R.string.mesh_create_network_failed_msg, e.message ?: ""))
            }
        }
    }

    // =================================================================
    // Join network (slave)
    // =================================================================

    /**
     * Slave: join an existing mesh network
     *
     * After joinNetMesh() the RC searches and joins the master-created network.
     * The dialog can be closed right after joining.
     */
    private fun startJoinMesh() {
        showMatchingPanel(getString(R.string.mesh_searching_network))
        btnComplete.visibility = View.GONE

        scope.launch {
            try {
                val result = joinMeshSuspend()
                if (result) {
                    isMeshStarted = true
                    updateMatchingStatus(getString(R.string.mesh_join_network_success))
                    btnComplete.text = getString(R.string.mesh_join_confirm_btn)
                    btnComplete.visibility = View.VISIBLE
                    btnCancel.visibility = View.GONE
                    refreshDeviceList()
                } else {
                    updateMatchingStatus(getString(R.string.mesh_join_network_failed))
                }
            } catch (e: Exception) {
                updateMatchingStatus(getString(R.string.mesh_join_network_failed_msg, e.message ?: ""))
            }
        }
    }

    // =================================================================
    // Complete networking (master)
    // =================================================================

    /**
     * Master: confirm completion
     *
     * After completeMesh() all joined devices are officially networked,
     * and the system generates the MeshID (= chat room channel ID).
     * Afterwards IMDemoFragment is notified to re-initialize RTM.
     */
    private fun completeMesh() {
        btnComplete.isEnabled = false
        updateMatchingStatus(getString(R.string.mesh_completing))

        scope.launch {
            try {
                val result = completeMeshSuspend()
                if (result) {
                    updateMatchingStatus(getString(R.string.mesh_complete_success))
                    mainHandler.postDelayed({
                        onMeshCompleted?.invoke()
                        dismiss()
                    }, 1000)
                } else {
                    updateMatchingStatus(getString(R.string.mesh_complete_failed))
                    btnComplete.isEnabled = true
                }
            } catch (e: Exception) {
                updateMatchingStatus(getString(R.string.mesh_complete_failed_msg, e.message ?: ""))
                btnComplete.isEnabled = true
            }
        }
    }

    // =================================================================
    // Cancel
    // =================================================================

    private fun cancelMesh() {
        if (!isMeshStarted) {
            dismiss()
            return
        }

        scope.launch {
            try {
                if (isCreateMode) {
                    cancelCreateMeshSuspend()
                } else {
                    cancelJoinMeshSuspend()
                }
            } catch (_: Exception) {
                // ignore cancel failure
            }
            dismiss()
        }
    }

    // =================================================================
    // UI helpers
    // =================================================================

    private fun showMatchingPanel(statusText: String) {
        llChoicePanel.visibility = View.GONE
        llMatchingPanel.visibility = View.VISIBLE
        tvMatchingStatus.text = statusText
        tvTitle.text = if (isCreateMode) getString(R.string.mesh_title_create) else getString(R.string.mesh_title_join)
    }

    private fun updateMatchingStatus(text: String) {
        mainHandler.post { tvMatchingStatus.text = text }
    }

    /**
     * Refresh the discovered device list
     *
     * Fetch all devices from getNetMeshManager().getAllMeshDeviceList()
     * and display them grouped by RC and drone.
     */
    private fun refreshDeviceList() {
        if (!isAdded) return

        val netMeshManager = DeviceManager.getMultiDeviceOperator().getNetMeshManager()
        val allDevices = netMeshManager.getAllMeshDeviceList()

        llDeviceList.removeAllViews()

        if (allDevices.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = getString(R.string.mesh_no_devices_found)
                setTextColor(Color.GRAY)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
            }
            llDeviceList.addView(emptyTv)
            return
        }

        allDevices.forEach { device ->
            val typeIcon = if (device.isRc()) "📱" else "🛩"
            val localTag = if (device.local) getString(R.string.mesh_local_tag) else ""
            val deviceTv = TextView(requireContext()).apply {
                text = "$typeIcon ${device.deviceName}$localTag  [ID: ${device.deviceId}]"
                setTextColor(Color.parseColor("#333333"))
                textSize = 14f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setBackgroundColor(Color.parseColor("#FFFFFF"))
            }
            llDeviceList.addView(deviceTv)

            // divider
            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
                setBackgroundColor(Color.parseColor("#EEEEEE"))
            }
            llDeviceList.addView(divider)
        }

        // Show MeshID
        val meshId = netMeshManager.getNetId()
        if (!meshId.isNullOrEmpty()) {
            val meshIdTv = TextView(requireContext()).apply {
                text = "🔗 MeshID: $meshId"
                setTextColor(Color.parseColor("#4C55FF"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(4))
            }
            llDeviceList.addView(meshIdTv)
        }

        // Update status bar message
        tvStatusMsg.text = getString(R.string.mesh_device_count, allDevices.size)
    }

    // =================================================================
    // Mesh device discovery listener
    // =================================================================

    /**
     * Device discovery callback
     *
     * Fires when a new device joins the network,
     * refreshing the device list and status.
     */
    private val meshDeviceListener = object : IMeshDeviceChangedListener {
        override fun onDiscoverReport() {
            super.onDiscoverReport()
            mainHandler.post {
                if (!isAdded) return@post
                refreshDeviceList()
                val count = DeviceManager.getMultiDeviceOperator()
                    .getNetMeshManager().getAllMeshDeviceList().size
                if (isCreateMode && count > 1) {
                    updateMatchingStatus(getString(R.string.mesh_discovered_count, count))
                    btnComplete.isEnabled = true
                }
            }
        }
    }

    private fun registerMeshListener() {
        DeviceManager.getMultiDeviceOperator().addNetMeshChangeListener(meshDeviceListener)
    }

    private fun unregisterMeshListener() {
        DeviceManager.getMultiDeviceOperator().removeNetMeshChangeListener(meshDeviceListener)
    }

    // =================================================================
    // Suspending wrappers for MSDK APIs
    // =================================================================

    /**
     * Create a mesh network
     *
     * @param isContinue true = continue on an existing network (add devices), false = create new
     */
    private suspend fun createMeshSuspend(isContinue: Boolean) =
        suspendCancellableCoroutine<Boolean> { continuation ->
            DeviceManager.getMultiDeviceOperator().getNetMeshManager()
                .startMeshMatching(isContinue, MAX_RC_NUM, MAX_DRONE_NUM) { result, statusCode ->
                    if (continuation.isActive) {
                        if (result) continuation.resume(true)
                        else continuation.resumeWithException(Exception(statusCode.msg))
                    }
                }
        }

    /**
     * Join an existing mesh network
     */
    private suspend fun joinMeshSuspend() =
        suspendCancellableCoroutine<Boolean> { continuation ->
            DeviceManager.getMultiDeviceOperator().getNetMeshManager()
                .joinNetMesh { result, statusCode ->
                    if (continuation.isActive) {
                        if (result) continuation.resume(true)
                        else continuation.resumeWithException(Exception(statusCode.msg))
                    }
                }
        }

    /**
     * Complete networking (master confirms)
     */
    private suspend fun completeMeshSuspend() =
        suspendCancellableCoroutine<Boolean> { continuation ->
            DeviceManager.getMultiDeviceOperator().getNetMeshManager()
                .completeMesh { result, statusCode ->
                    if (continuation.isActive) {
                        if (result) continuation.resume(true)
                        else continuation.resumeWithException(Exception(statusCode.msg))
                    }
                }
        }

    /**
     * Cancel network creation
     */
    private suspend fun cancelCreateMeshSuspend() =
        suspendCancellableCoroutine<Boolean> { continuation ->
            DeviceManager.getMultiDeviceOperator().getNetMeshManager()
                .cancelCreateMesh { result, statusCode ->
                    if (continuation.isActive) {
                        if (result) continuation.resume(true)
                        else continuation.resumeWithException(Exception(statusCode.msg))
                    }
                }
        }

    /**
     * Cancel network join
     */
    private suspend fun cancelJoinMeshSuspend() =
        suspendCancellableCoroutine<Boolean> { continuation ->
            DeviceManager.getMultiDeviceOperator().getNetMeshManager()
                .cancelJoinMesh { result, statusCode ->
                    if (continuation.isActive) {
                        if (result) continuation.resume(true)
                        else continuation.resumeWithException(Exception(statusCode.msg))
                    }
                }
        }

    // =================================================================
    // Utilities
    // =================================================================

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
