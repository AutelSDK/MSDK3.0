package com.autel.sdk.debugtools.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.interfaces.IModelSOTAUpgradeManager
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.ModelSOTAUpgradeManager
import com.autel.drone.sdk.vmodelx.manager.OTAUpgradeManger
import com.autel.drone.sdk.vmodelx.manager.UpgradeManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.upgrade.bean.UpgradeResultBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.upgrade.enums.ResponseResultEnum
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.upgrade.enums.UpgradeClientTypeEnum
import com.autel.drone.sdk.vmodelx.manager.upgrade.UpgradeErrorStateEnum
import com.autel.drone.sdk.vmodelx.manager.upgrade.UpgradeListener
import com.autel.drone.sdk.vmodelx.module.fileservice.FileTransmitListener
import com.autel.drone.sdk.vmodelx.module.fileservice.FileUtils
import com.autel.drone.sdk.vmodelx.module.upgrade.bean.ota.CheckModelUpgradeDataBean
import com.autel.drone.sdk.vmodelx.module.upgrade.bean.ota.ModelInfoBean
import com.autel.drone.sdk.vmodelx.module.upgrade.enums.UpgradeResultEnum
import com.autel.drone.sdk.libbase.utils.FileUtil
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.upgrade.bean.UpgradeProgressBean
import com.autel.drone.sdk.vmodelx.utils.S3DownloadInterceptor
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentRemoteModelSotaUpgradeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * RC model SOTA upgrade test Fragment
 * Tests and verifies RC AI model SOTA upgrade features
 * 
 * Main features:
 * 1. One-click RC AI model upgrade (check, download, upgrade)
 * 2. Auto-fetch device info (SN, model, version, etc.)
 * 3. System default language or manually specified language
 * 4. UI shows upgrade progress and detailed logs
 * 
 * @author Autel Robotics on 2024/12/19
 */
@SuppressLint("SetTextI18n")
class RemoteModelSOTAUpgradeFragment : AutelFragment() {

    companion object {
        private const val TAG = "RemoteModelSOTA-Test"
        
        // Default value constants (for testing)
        private const val DEFAULT_MODEL_NAME = "Qwen34BInstruct"
        private const val DEFAULT_MODEL_VERSION = "V1.0.0.0"
    }

    private lateinit var binding: FragmentRemoteModelSotaUpgradeBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logList: MutableList<String> = arrayListOf()
    private val modelSotaManager: IModelSOTAUpgradeManager = ModelSOTAUpgradeManager.getInstance()
    
    // Current upgrade data
    private var currentUpgradeData: CheckModelUpgradeDataBean? = null
    private var currentDownloadFile: File? = null
    private var s3DownloadInterceptor: S3DownloadInterceptor? = null
    private var upgradeManager: com.autel.drone.sdk.vmodelx.interfaces.IUpgradeManager? = null
    private var isUpgrading = false
    private var isProcessing = false // whether the upgrade flow is running
    
    /**
     * Upgrade version listener
     */
    private val upgradeVersionListener = object : OTAUpgradeManger.UpgradeVersionListener {
        override fun onDeviceUpgrade(manual:Boolean,beanMap: HashMap<String, com.autel.drone.sdk.vmodelx.module.upgrade.bean.ota.CheckResponseBean.Data>) {
            showLogDebug("onDeviceUpgrade=${beanMap.size}")
            // Get full model upgrade data from ModelSOTAUpgradeManager
            val modelData = modelSotaManager.getRemoteModelUpgradeData()
            if (modelData != null) {
                // Log the full model upgrade data for debugging
                showLogDebug("========================================")
                showLogDebug("Model upgrade data details:")
                showLogDebug("========================================")
                showLogDebug("isNeed_upgrade: ${modelData.isNeed_upgrade}")
                showLogDebug("pkg_url: ${modelData.pkg_url ?: "N/A"}")
                
                // Log product info
                modelData.product?.let { product ->
                    showLogDebug("Product info:")
                    showLogDebug("  product_sn: ${product.product_sn ?: "N/A"}")
                    showLogDebug("  product_model: ${product.product_model ?: "N/A"}")
                    showLogDebug("  accept_remind: ${product.accept_remind ?: "N/A"}")
                    showLogDebug("  img_url: ${product.img_url ?: "N/A"}")
                }
                
                // Log upgrade task info (all fields)
                modelData.upgrade_task?.let { task ->
                    showLogDebug("Upgrade task info:")
                    showLogDebug("  task_id: ${task.task_id}")
                    showLogDebug("  task_name: ${task.task_name ?: "N/A"}")
                    showLogDebug("  interact_way: ${task.interact_way ?: "N/A"}")
                    showLogDebug("  upgrade_way: ${task.upgrade_way ?: "N/A"}")
                    showLogDebug("  upgrade_task.toString(): ${task.toString()}")
                } ?: showLogDebug("Upgrade task info: N/A")
                
                // Log upgradable model list
                modelData.upgradeable_models?.let { models ->
                    showLogDebug("Upgradable model count: ${models.size}")
                    models.forEachIndexed { index, model ->
                        showLogDebug("Model ${index + 1}:")
                        showLogDebug("  model_name: ${model.model_name ?: "N/A"}")
                        showLogDebug("  model_version: ${model.model_version ?: "N/A"}")
                        showLogDebug("  file_size: ${model.file_size ?: "N/A"}")
                        showLogDebug("  file_md5: ${model.file_md5 ?: "N/A"}")
                        showLogDebug("  pkg_url: ${model.pkg_url ?: "N/A"}")
                    }
                } ?: showLogDebug("Upgradable model list: N/A")
                
                showLogDebug("========================================")
                
                if (modelData.isNeed_upgrade) {
                    showLogDebug("✓ RC model upgrade available")
                    currentUpgradeData = modelData
                    updateUpgradeResultUI(modelData)
                    // Auto start download
                    lifecycleScope.launch(Dispatchers.Main) {
                        downloadModelFile()
                    }
                } else {
                    showLogDebug("✗ No RC model upgrade available")
                    binding.tvUpgradeResult.text = getString(R.string.remote_sota_no_upgrade)
                    currentUpgradeData = null
                    isProcessing = false
                    binding.btnUpgradeModel.isEnabled = true
                    binding.btnUpgradeModel.text = getString(R.string.remote_sota_start_btn)
                }
            } else {
                showLogDebug("✗ RC model upgrade data is empty")
                binding.tvUpgradeResult.text = getString(R.string.remote_sota_data_empty)
                currentUpgradeData = null
                isProcessing = false
                binding.btnUpgradeModel.isEnabled = true
                binding.btnUpgradeModel.text = getString(R.string.remote_sota_start_btn)
            }
        }

        override fun onEnterUpgradeMode(success: Boolean) {
            showLogDebug("onEnterUpgradeMode success=$success")
        }

        override fun onExitUpgradeMode(success: Boolean) {
            showLogDebug("onExitUpgradeMode success=$success")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentRemoteModelSotaUpgradeBinding.inflate(layoutInflater)
        initView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRemoteModelSotaUpgradeBinding.inflate(inflater)
        initUpgradeListener()
        initView()
        return binding.root
    }

    /**
     * Initialize upgrade listeners
     */
    private fun initUpgradeListener() {
        // Use the ModelSOTAUpgradeManager listener
        modelSotaManager.addUpgradeVersionListener(upgradeVersionListener)
    }

    /**
     * Initialize views
     */
    private fun initView() {
        // One-click upgrade button (check + download + upgrade)
        binding.btnUpgradeModel.setOnClickListener {
            if (isProcessing) {
                showLogDebug("Upgrade in progress, please wait", isError = true)
                return@setOnClickListener
            }
            showLogDebug(">> Start RC model upgrade clicked")
            startUpgradeFlow()
        }

        // Clear-log button
        binding.tvClearLog.setOnClickListener {
            binding.tvLogInfo.text = ""
            logList.clear()
            showLogDebug("Log cleared")
        }
    }

    /**
     * Get a parameter value, falling back to the default when empty
     */
    private fun getValueOrDefault(editText: android.widget.EditText, defaultValue: String): String {
        val value = editText.text.toString().trim()
        return if (TextUtils.isEmpty(value)) defaultValue else value
    }

    /**
     * Start upgrade flow (check -> download -> upgrade)
     */
    private fun startUpgradeFlow() {
        if (isProcessing) {
            return
        }

        isProcessing = true
        binding.btnUpgradeModel.isEnabled = false
        binding.btnUpgradeModel.text = getString(R.string.remote_sota_upgrading)
        currentUpgradeData = null
        currentDownloadFile = null

        // Get model info, fall back to defaults when empty
        val modelName = getValueOrDefault(binding.etModelName, DEFAULT_MODEL_NAME)
        val modelVersion = getValueOrDefault(binding.etModelVersion, DEFAULT_MODEL_VERSION)
        
        // Get language (optional); fall back to system default when empty
        val acceptLanguage = binding.etAcceptLanguage.text.toString().trim()
        val language = if (TextUtils.isEmpty(acceptLanguage)) null else acceptLanguage

        // Build model info
        val modelInfo = ModelInfoBean()
        modelInfo.model_name = modelName
        modelInfo.model_version = modelVersion
        val models = listOf(modelInfo)

        showLogDebug("========================================")
        showLogDebug("Starting RC model upgrade flow")
        showLogDebug("========================================")
        showLogDebug("Step 1: Check model upgrade")
        showLogDebug("Model name: $modelName")
        showLogDebug("Model version: $modelVersion")
        if (language != null) {
            showLogDebug("Accept language: $language")
        } else {
            showLogDebug("Accept language: system default")
        }

        binding.tvUpgradeResult.text = getString(R.string.remote_sota_detecting)
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.tvDownloadProgress.visibility = View.VISIBLE
        binding.tvDownloadProgress.text = getString(R.string.remote_sota_preparing)

        // Call the ModelSOTAUpgradeManager check API (device info auto-fetched)
        modelSotaManager.detectRemoteModelUpdateInfo(language)
        
        ToastUtils.showToast(getString(R.string.remote_sota_start_toast))
    }

    /**
     * Download the model file (automatic; no user action needed)
     */
    private fun downloadModelFile() {
        val upgradeData = currentUpgradeData
        if (upgradeData == null || !upgradeData.isNeed_upgrade) {
            showLogDebug("✗ No downloadable upgrade file", isError = true)
            isProcessing = false
            binding.btnUpgradeModel.isEnabled = true
            binding.btnUpgradeModel.text = getString(R.string.remote_sota_start_btn)
            binding.progressBar.visibility = View.GONE
            binding.tvDownloadProgress.visibility = View.GONE
            return
        }

        // Get the download URL from the upgradable model list
        val upgradeableModel = upgradeData.upgradeable_models?.firstOrNull()
        val pkgUrl = upgradeableModel?.pkg_url
        if (pkgUrl.isNullOrEmpty()) {
            showLogDebug("✗ Upgrade file URL is empty", isError = true)
            isProcessing = false
            binding.btnUpgradeModel.isEnabled = true
            binding.btnUpgradeModel.text = getString(R.string.remote_sota_start_btn)
            binding.progressBar.visibility = View.GONE
            binding.tvDownloadProgress.visibility = View.GONE
            return
        }

        val fileName = pkgUrl.substringAfterLast("/").substringBefore("?") ?: "model_upgrade.bin"

        showLogDebug("========================================")
        showLogDebug("Step 2: Download model file")
        showLogDebug("========================================")
        showLogDebug("File name: $fileName")
        showLogDebug("Download URL: $pkgUrl")
        
        binding.progressBar.progress = 0
        binding.tvDownloadProgress.text = getString(R.string.remote_sota_preparing_download)

        s3DownloadInterceptor = modelSotaManager.downloadFile(
            pkgUrl,
            fileName,
            0,
            object : FileTransmitListener<File> {
                override fun onSuccess(file: File?) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        showLogDebug("✓ Download success: ${file?.absolutePath}")
                        if (file == null) {
                            showLogDebug("✗ Downloaded file is empty", isError = true)
                            resetUpgradeState()
                            return@launch
                        }

                        // Verify MD5
                        showLogDebug("Verifying file MD5...")
                        withContext(Dispatchers.IO) {
                            val md5 = FileUtils.md5sum(file)
                            showLogDebug("File MD5: $md5")
                            
                            withContext(Dispatchers.Main) {
                                val expectedMd5 = upgradeData.upgradeable_models?.firstOrNull()?.file_md5
                                if (!expectedMd5.isNullOrEmpty() && md5 != expectedMd5) {
                                    showLogDebug("✗ MD5 mismatch: expected=$expectedMd5, actual=$md5", isError = true)
                                    ToastUtils.showToast(getString(R.string.remote_sota_md5_failed))
                                    FileUtil.deleteFile(file.absolutePath)
                                    resetUpgradeState()
                                } else {
                                    showLogDebug("✓ MD5 verified")
                                    currentDownloadFile = file
                                    binding.progressBar.progress = 0
                                    binding.tvDownloadProgress.text = getString(R.string.remote_sota_download_complete)
                                    // Auto start upgrade
                                    delay(500) // brief delay so the UI can update
                                    upgradeModel()
                                }
                            }
                        }
                    }
                }

                override fun onProgress(sendLength: Long, totalLength: Long, speed: Long) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        val progress = if (totalLength > 0) {
                            (sendLength.toDouble() / totalLength * 100).toInt()
                        } else {
                            0
                        }
                        binding.progressBar.progress = progress
                        
                        val speedText = if (speed < 1000) {
                            String.format(Locale.getDefault(), "%.2f KB/s", speed / 1024f)
                        } else {
                            String.format(Locale.getDefault(), "%.2f MB/s", speed / 1024f / 1024f)
                        }
                        
                        val progressText = getString(R.string.remote_sota_download_progress, progress, speedText)
                        binding.tvDownloadProgress.text = progressText
                        if (progress % 10 == 0 || progress == 100) { // log every 10% or on completion
                            showLogDebug(progressText)
                        }
                    }
                }

                override fun onFailed(errorCode: IAutelCode?, msg: String?) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        showLogDebug("✗ Download failed: code=${errorCode?.code}, msg=$msg", isError = true)
                        ToastUtils.showToast(getString(R.string.remote_sota_download_failed, msg))
                        resetUpgradeState()
                    }
                }
            }
        )
    }

    /**
     * Upgrade the model (automatic; no user action needed)
     */
    private fun upgradeModel() {
        val file = currentDownloadFile
        if (file == null || !file.exists()) {
            showLogDebug("✗ Upgrade file not found", isError = true)
            resetUpgradeState()
            return
        }

        val remoteDevice = DeviceManager.getDeviceManager().getLocalRemoteDevice()
        val deviceId = remoteDevice.getDeviceNumber()

        showLogDebug("========================================")
        showLogDebug("Step 3: Upgrade model")
        showLogDebug("========================================")
        showLogDebug("Upgrade file: ${file.name}")
        showLogDebug("Device ID: $deviceId")
        
        isUpgrading = true
        binding.progressBar.progress = 0
        binding.tvDownloadProgress.text = getString(R.string.remote_sota_preparing_upgrade)

        // Create the upgrade manager (RC uses CLIENT_TYPE_GND)
        upgradeManager = UpgradeManager(deviceId).init(UpgradeClientTypeEnum.CLIENT_TYPE_GND)

        upgradeManager?.registerUpgradeListener(object : UpgradeListener {
            override fun onUpgradeFlowChange(deviceId: Int, flowState: com.autel.drone.sdk.vmodelx.manager.upgrade.UpgradeFlowEnum) {
                showLogDebug("Upgrade flow state: $flowState")
            }

            override fun onUpgradeStateChange(deviceId: Int, state: UpgradeErrorStateEnum) {
                // onUpgradeStateChange only fires on errors; every state is an error state
                showLogDebug("Upgrade state changed: $state", isError = true)
                resetUpgradeState()
                ToastUtils.showToast(getString(R.string.remote_sota_upgrade_failed_code, state))
            }

            override fun onUploadPackageProgress(deviceId: Int, totalLength: Long, sendLength: Long, progress: Int, speed: Long) {
                val speedText = if (speed < 1000) {
                    String.format(Locale.getDefault(), "%.2f KB/s", speed / 1024f)
                } else {
                    String.format(Locale.getDefault(), "%.2f MB/s", speed / 1024f / 1024f)
                }
                if (progress % 10 == 0 || progress == 100) { // log every 10% or on completion
                    showLogDebug("Upload progress: $progress% - $speedText")
                }
                binding.progressBar.progress = progress
                binding.tvDownloadProgress.text = getString(R.string.remote_sota_upload_progress, progress)
            }

            override fun onUpgradeProgress(deviceId: Int, progress: Int) {
                if (progress % 10 == 0 || progress == 100) { // log every 10% or on completion
                    showLogDebug("Upgrade progress: $progress%")
                }
                binding.progressBar.progress = progress
                binding.tvDownloadProgress.text = getString(R.string.remote_sota_upgrade_progress, progress)
            }

            override fun onUpgradeResult(deviceId: Int, resultBean: UpgradeResultBean) {
                showLogDebug("========================================")
                showLogDebug("Upgrade result: ${resultBean.result}")
                showLogDebug("========================================")
                resetUpgradeState()
                
                when (resultBean.result) {
                    ResponseResultEnum.SUCCESS -> {
                        showLogDebug("✓ Upgrade success!")
                        ToastUtils.showToast(getString(R.string.remote_sota_upgrade_success))
                        binding.btnUpgradeModel.text = getString(R.string.remote_sota_upgrade_success)
                        // Report upgrade status
                        currentUpgradeData?.upgrade_task?.task_id?.let { taskId ->
                            val remoteDevice = DeviceManager.getDeviceManager().getLocalRemoteDevice()
                            val productSn = remoteDevice.getSnImmediately() ?: ""
                            if (productSn.isNotEmpty()) {
                                showLogDebug("Report upgrade status: success")
                                modelSotaManager.reportUpgrade(
                                    UpgradeResultEnum.SUCCESS,
                                    taskId,
                                    productSn,
                                    "Model upgrade success"
                                )
                            }
                        }
                    }
                    ResponseResultEnum.ERROR, ResponseResultEnum.UNKNOWN -> {
                        showLogDebug("✗ Upgrade failed: ${resultBean.errCode}", isError = true)
                        ToastUtils.showToast(getString(R.string.remote_sota_upgrade_failed_code, resultBean.errCode))
                        binding.btnUpgradeModel.text = getString(R.string.remote_sota_upgrade_failed_retry)
                        // Report upgrade status
                        currentUpgradeData?.upgrade_task?.task_id?.let { taskId ->
                            val remoteDevice = DeviceManager.getDeviceManager().getLocalRemoteDevice()
                            val productSn = remoteDevice.getSnImmediately() ?: ""
                            if (productSn.isNotEmpty()) {
                                showLogDebug("Report upgrade status: failed")
                                modelSotaManager.reportUpgrade(
                                    UpgradeResultEnum.FAIL,
                                    taskId,
                                    productSn,
                                    "Model upgrade failed: ${resultBean.errCode}"
                                )
                            }
                        }
                    }
                    else -> {
                        showLogDebug("? Unknown upgrade result: ${resultBean.result}")
                        ToastUtils.showToast(getString(R.string.remote_sota_upgrade_unknown))
                        binding.btnUpgradeModel.text = getString(R.string.remote_sota_upgrade_unknown_retry)
                    }
                }
            }
        })

        upgradeManager?.startUpgradeFlow(file, null)
    }

    /**
     * Reset upgrade state
     */
    private fun resetUpgradeState() {
        isProcessing = false
        isUpgrading = false
        binding.btnUpgradeModel.isEnabled = true
        if (binding.btnUpgradeModel.text != getString(R.string.remote_sota_upgrade_success)) {
            binding.btnUpgradeModel.text = getString(R.string.remote_sota_start_btn)
        }
        upgradeManager?.unInit()
        upgradeManager = null
    }

    /**
     * Update the upgrade result UI
     */
    private fun updateUpgradeResultUI(data: CheckModelUpgradeDataBean) {
        val resultBuilder = StringBuilder()
        resultBuilder.append("Check result:\n")
        resultBuilder.append("Need upgrade: ${data.isNeed_upgrade}\n")
        resultBuilder.append("Package URL: ${data.pkg_url ?: "N/A"}\n")
        resultBuilder.append("\n")
        
        // Product info (all fields)
        data.product?.let { product ->
            resultBuilder.append("Product info:\n")
            resultBuilder.append("  SN: ${product.product_sn ?: "N/A"}\n")
            resultBuilder.append("  Model: ${product.product_model ?: "N/A"}\n")
            resultBuilder.append("  Accept remind: ${product.accept_remind ?: "N/A"}\n")
            resultBuilder.append("  Image URL: ${product.img_url ?: "N/A"}\n")
        } ?: resultBuilder.append("Product info: N/A\n")
        resultBuilder.append("\n")
        
        // Upgrade task info (all fields, incl. interact_way and upgrade_way)
        data.upgrade_task?.let { task ->
            resultBuilder.append("Upgrade task:\n")
            resultBuilder.append("  Task ID: ${task.task_id}\n")
            resultBuilder.append("  Task name: ${task.task_name ?: "N/A"}\n")
            resultBuilder.append("  Interact way: ${task.interact_way ?: "N/A"}\n")
            resultBuilder.append("  Upgrade way: ${task.upgrade_way ?: "N/A"}\n")
            // Log the full object for debugging
            resultBuilder.append("  Full object: ${task.toString()}\n")
        } ?: resultBuilder.append("Upgrade task: N/A\n")
        resultBuilder.append("\n")
        
        // Upgradable model list (all fields)
        data.upgradeable_models?.let { models ->
            if (models.isNotEmpty()) {
                resultBuilder.append("Upgradable models:\n")
                models.forEachIndexed { index, model ->
                    resultBuilder.append("  Model ${index + 1}:\n")
                    resultBuilder.append("    Name: ${model.model_name ?: "N/A"}\n")
                    resultBuilder.append("    Version: ${model.model_version ?: "N/A"}\n")
                    resultBuilder.append("    Accept name: ${model.accept_name ?: "N/A"}\n")
                    resultBuilder.append("    Accept desc: ${model.accept_desc ?: "N/A"}\n")
                    resultBuilder.append("    Package type: ${model.package_type ?: "N/A"}\n")
                    resultBuilder.append("    Base version: ${model.base_version ?: "N/A"}\n")
                    resultBuilder.append("    File size: ${model.file_size ?: "N/A"}\n")
                    resultBuilder.append("    File MD5: ${model.file_md5 ?: "N/A"}\n")
                    resultBuilder.append("    Cost: ${model.cost_min} min\n")
                    resultBuilder.append("    Package URL: ${model.pkg_url ?: "N/A"}\n")
                    if (index < models.size - 1) {
                        resultBuilder.append("\n")
                    }
                }
            } else {
                resultBuilder.append("Upgradable models: empty\n")
            }
        } ?: resultBuilder.append("Upgradable models: N/A\n")

        binding.tvUpgradeResult.text = resultBuilder.toString()
    }

    /**
     * Show log
     */
    private fun showLogDebug(log: String, isError: Boolean = false) {
        val timestamp = System.currentTimeMillis()
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(java.util.Date(timestamp))
        val logWithTime = "[$timeStr] $log"
        logList.add(logWithTime)

        if (isError) {
            SDKLog.e(TAG, log)
        } else {
            SDKLog.i(TAG, log)
        }

        // Ensure UI updates run on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateLogUI()
        } else {
            mainHandler.post { updateLogUI() }
        }
    }

    /**
     * Update the log UI
     */
    private fun updateLogUI() {
        val logListCopy = ArrayList(logList)
        val stringBuffer = StringBuilder()
        logListCopy.forEach {
            stringBuffer.append(it).append("\n")
        }
        binding.tvLogInfo.text = stringBuffer.toString()
        // Auto scroll to bottom
        binding.tvLogInfo.post {
            val scrollView = binding.tvLogInfo.parent as? android.widget.ScrollView
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        modelSotaManager.removeUpgradeVersionListener(upgradeVersionListener)
        s3DownloadInterceptor?.cancel()
        upgradeManager?.unInit()
    }
}
