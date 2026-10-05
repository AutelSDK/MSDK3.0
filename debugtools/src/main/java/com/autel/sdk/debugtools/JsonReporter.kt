package com.autel.sdk.debugtools

import android.content.Context
import android.os.Build
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.SDKManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * JSON acceptance report generator, complementing HtmlReporter.
 * HTML is for human review; JSON is for baseline diffing, CI parsing and trend tracking.
 */
object JsonReporter {
    private const val TAG = "JsonReporter"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    data class KeyTestResult(
        val module: String,
        val keyName: String,
        val operation: String,
        val success: Boolean,
        val responseTimeMs: Long,
        val result: String?,
        val errorCode: String? = null
    )

    fun generateReport(
        context: Context,
        results: List<KeyTestResult>,
        startTime: Long,
        endTime: Long
    ): String {
        val root = JSONObject()
        val now = Date()

        root.put("report_time", dateFormat.format(now))
        root.put("start_time", dateFormat.format(Date(startTime)))
        root.put("end_time", dateFormat.format(Date(endTime)))
        root.put("duration_seconds", (endTime - startTime) / 1000)

        val env = JSONObject()
        env.put("sdk_version", try { SDKManager.get().getSDKVersion() } catch (_: Exception) { "unknown" })
        env.put("device_model", Build.MODEL)
        env.put("android_version", Build.VERSION.RELEASE)
        env.put("android_sdk", Build.VERSION.SDK_INT)
        root.put("environment", env)

        val totalCount = results.size
        val passCount = results.count { it.success }
        val failCount = totalCount - passCount
        val avgResponseMs = if (results.isNotEmpty()) results.map { it.responseTimeMs }.average().toLong() else 0L

        val summary = JSONObject()
        summary.put("total_keys_tested", totalCount)
        summary.put("passed", passCount)
        summary.put("failed", failCount)
        summary.put("pass_rate", if (totalCount > 0) "%.1f%%".format(passCount * 100.0 / totalCount) else "N/A")
        summary.put("avg_response_time_ms", avgResponseMs)
        root.put("summary", summary)

        val moduleMap = results.groupBy { it.module }
        val moduleResults = JSONObject()
        for ((module, items) in moduleMap) {
            val mObj = JSONObject()
            val mPass = items.count { it.success }
            val mFail = items.size - mPass
            mObj.put("total", items.size)
            mObj.put("passed", mPass)
            mObj.put("failed", mFail)
            mObj.put("pass_rate", if (items.isNotEmpty()) "%.1f%%".format(mPass * 100.0 / items.size) else "N/A")
            mObj.put("avg_response_time_ms", if (items.isNotEmpty()) items.map { it.responseTimeMs }.average().toLong() else 0L)

            val failures = items.filter { !it.success }
            if (failures.isNotEmpty()) {
                val failArr = JSONArray()
                for (f in failures) {
                    val fObj = JSONObject()
                    fObj.put("key", f.keyName)
                    fObj.put("operation", f.operation)
                    fObj.put("error", f.result ?: "unknown")
                    fObj.put("error_code", f.errorCode ?: "")
                    fObj.put("response_time_ms", f.responseTimeMs)
                    failArr.put(fObj)
                }
                mObj.put("failures", failArr)
            }
            moduleResults.put(module, mObj)
        }
        root.put("module_results", moduleResults)

        val opStats = JSONObject()
        for (op in listOf("GET", "SET", "SET-BACK", "ACTION", "LISTEN", "REPORT")) {
            val opItems = results.filter { it.operation == op }
            if (opItems.isNotEmpty()) {
                val opObj = JSONObject()
                opObj.put("total", opItems.size)
                opObj.put("passed", opItems.count { it.success })
                opObj.put("failed", opItems.count { !it.success })
                opObj.put("avg_response_time_ms", opItems.map { it.responseTimeMs }.average().toLong())
                opStats.put(op, opObj)
            }
        }
        root.put("operation_stats", opStats)

        val path = context.getExternalCacheDir()?.absolutePath + "/AutelTest/" + fileDateFormat.format(now) + "/"
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()

        val filePath = path + "api_verification_report.json"
        try {
            File(filePath).writeText(root.toString(2))
            SDKLog.i(TAG, "JSON report saved: $filePath")
        } catch (e: Exception) {
            SDKLog.e(TAG, "Failed to save JSON report: ${e.message}")
        }
        return filePath
    }
}
