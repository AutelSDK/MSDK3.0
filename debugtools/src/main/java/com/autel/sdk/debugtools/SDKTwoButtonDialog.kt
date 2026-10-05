package com.autel.sdk.debugtools

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import com.autel.sdk.debugtools.databinding.SdkLayoutTwoButtonDialogBinding

/**
 * Two-button operation dialog
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/9/9
 */
class SDKTwoButtonDialog(context: Context) : SDKBaseDialog(context) {

    private val binding = SdkLayoutTwoButtonDialogBinding.inflate(LayoutInflater.from(context))

    /**
     * Whether the dialog closes automatically
     */
    private var isAutoDismiss = true
    private var onLeftBtnClick: (() -> Unit)? = null
    private var onRightBtnClick: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        binding.tvCancel.setOnClickListener {
            if (isAutoDismiss) {
                dismiss()
            }
            onLeftBtnClick?.invoke()
        }
        binding.tvConfirm.setOnClickListener {
            if (isAutoDismiss) {
                dismiss()
            }
            onRightBtnClick?.invoke()
        }
    }

    fun setMessage(msg: String) {
        binding.tvMsg.text = msg
    }

    fun setRightBtnStr(text: String) {
        binding.tvConfirm.text = text
    }

    fun setLeftBtnStr(text: String) {
        binding.tvCancel.text = text
    }

    fun setAutoDismiss(isAutoDismiss: Boolean) {
        this.isAutoDismiss = isAutoDismiss
    }

    fun setRightBtnListener(listener: () -> Unit) {
        onRightBtnClick = listener
    }

    fun setLeftBtnListener(listener: () -> Unit) {
        onLeftBtnClick = listener
    }
}