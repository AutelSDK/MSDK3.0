package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.autel.drone.sdk.libbase.error.AutelStatusCode
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.log.SDKLog
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKeyInfo
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools
import com.autel.drone.sdk.vmodelx.utils.ToastUtils
import com.autel.sdk.debugtools.MAIN_FRAGMENT_PAGE_TITLE
import com.autel.sdk.debugtools.MSDKInfoVm
import com.autel.sdk.debugtools.R

/**
 * base fragment for debug tools
 * @author Autel Robotics on 2022/12/17.
 */
open class AutelFragment : Fragment() {

    protected val msdkInfoVm: MSDKInfoVm by activityViewModels()
    protected val TAG = this::class.java.simpleName
    private var _handler: Handler? = null
    protected open val handler: Handler
        get() = _handler ?: run {
            val looper = context?.mainLooper ?: Looper.getMainLooper()
            Handler(looper) { handleMessage(it) }.also { _handler = it }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _handler = Handler(requireContext().mainLooper) {
            return@Handler handleMessage(it)
        }
    }

    open fun updateTitle() {
        arguments?.let {
            val title = it.getInt(MAIN_FRAGMENT_PAGE_TITLE, R.string.debug_testing_tools)
            msdkInfoVm.mainTitle.value = getString(R.string.debug_testing_tools)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        updateTitle()
    }

    open fun handleMessage(msg: Message): Boolean {
        return false
    }

    protected fun safePost(action: () -> Unit) {
        if (!isAdded) return
        val h = _handler ?: return
        h.post {
            if (isAdded) {
                try {
                    action()
                } catch (e: Exception) {
                    SDKLog.printSafeStackTrace(e)
                }
            }
        }
    }

    protected fun safePostDelayed(delayMs: Long, action: () -> Unit) {
        if (!isAdded) return
        val h = _handler ?: return
        h.postDelayed({
            if (isAdded) {
                try {
                    action()
                } catch (e: Exception) {
                    SDKLog.printSafeStackTrace(e)
                }
            }
        }, delayMs)
    }

    /**
     * Runs UI logic while the Fragment is attached; async callbacks from worker threads are automatically moved to the main thread.
     */
    protected fun runOnUiThreadIfAttached(action: () -> Unit) {
        if (!isAdded) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runIfAttached(action)
        } else {
            safePost(action)
        }
    }

    protected inline fun runIfAttached(crossinline action: () -> Unit) {
        if (!isAdded) return
        try {
            action()
        } catch (e: Exception) {
            SDKLog.printSafeStackTrace(e)
        }
    }

    protected fun <T> guardedCallbackWithParam(
        onSuccess: (T?) -> Unit = {},
        onFailure: (IAutelCode, String?) -> Unit = { _, _ -> },
    ): CommonCallbacks.CompletionCallbackWithParam<T> =
        object : CommonCallbacks.CompletionCallbackWithParam<T> {
            override fun onSuccess(t: T?) = runOnUiThreadIfAttached { onSuccess(t) }

            override fun onFailure(error: IAutelCode, msg: String?) =
                runOnUiThreadIfAttached { onFailure(error, msg) }
        }

    protected fun guardedCompletionCallback(
        onSuccess: () -> Unit = {},
        onFailure: (IAutelCode, String?) -> Unit = { _, _ -> },
    ): CommonCallbacks.CompletionCallback =
        object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() = runOnUiThreadIfAttached { onSuccess() }

            override fun onFailure(code: IAutelCode, msg: String?) =
                runOnUiThreadIfAttached { onFailure(code, msg) }
        }

    protected fun showToast(msg: String) {
        safePost {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _handler?.removeCallbacksAndMessages(null)
        _handler = null
    }

    protected fun bindSwitchWithKey(
        switchView: SwitchCompat,
        keyInfo: AutelKeyInfo<Boolean>,
        keyManager: IKeyManager?
    ) {
        val key = KeyTools.createKey(keyInfo) as AutelKey<Boolean>

        val applySwitchState: (Boolean) -> Unit = { isChecked ->
            safePost {
                switchView.setOnCheckedChangeListener(null)
                switchView.isChecked = isChecked
                switchView.setOnCheckedChangeListener { _, newChecked ->
                    keyManager?.setValue(key, newChecked, object : CommonCallbacks.CompletionCallback {
                        override fun onSuccess() {
                            SDKLog.i(TAG, "set ${keyInfo.keyName} onSuccess")
                        }

                        override fun onFailure(code: IAutelCode, msg: String?) {
                            SDKLog.i(TAG, "set ${keyInfo.keyName} onFailure($code, $msg)")
                            safePost {
                                switchView.setOnCheckedChangeListener(null)
                                switchView.isChecked = !newChecked
                                switchView.setOnCheckedChangeListener { _, c ->
                                    keyManager.setValue(key, c, this)
                                }
                            }
                            showToast("onFailure($code, $msg)")
                        }
                    })
                }
            }
        }

        fun fetchValue(retryCount: Int) {
            keyManager?.getValue(key, object : CommonCallbacks.CompletionCallbackWithParam<Boolean> {
                override fun onSuccess(t: Boolean?) {
                    SDKLog.i(TAG, "get ${keyInfo.keyName} onSuccess($t)")
                    applySwitchState(t ?: false)
                }

                override fun onFailure(error: IAutelCode, msg: String?) {
                    SDKLog.e(TAG, "get ${keyInfo.keyName} onFailure($msg, $error) retry=$retryCount")
                    if (error == AutelStatusCode.ROS2_NOT_SUPPORTED) {
                        ToastUtils.showToast("[${keyInfo.keyName}]=> $msg")
                        return
                    }
                    if (retryCount < MAX_BIND_RETRY) {
                        safePostDelayed(BIND_RETRY_DELAY_MS) { fetchValue(retryCount + 1) }
                    } else {
                        SDKLog.e(TAG, "get ${keyInfo.keyName} all retries exhausted, skip init")
                        applySwitchState(false)
                    }
                }
            })
        }

        fetchValue(0)
    }

    companion object {
        private const val MAX_BIND_RETRY = 3
        private const val BIND_RETRY_DELAY_MS = 2000L
    }

}