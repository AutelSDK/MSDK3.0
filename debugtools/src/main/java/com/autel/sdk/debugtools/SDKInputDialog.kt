package com.autel.sdk.debugtools

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import com.autel.sdk.debugtools.databinding.SdkLayoutInputDialogBinding

/**
 * Generic input dialog
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/9/9.
 */
class SDKInputDialog(context: Context) : SDKBaseDialog(context) {

    private val binding = SdkLayoutInputDialogBinding.inflate(LayoutInflater.from(context))

    /** Whether the dialog closes automatically */
    private var isAutoDismiss = true
    private var onBtnClick: ((String) -> Unit)? = null

    init {
        binding.tvCancel.setOnClickListener {
            if (isAutoDismiss) {
                dismiss()
            }
        }

        binding.tvConfirm.setOnClickListener {
            if (isAutoDismiss) {
                dismiss()
            }
            onBtnClick?.invoke(binding.etMsg.text.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
    }

    fun setTitle(title: String) {
        binding.tvTitle.text = title
    }

    fun setMessage(msg: String) {
        binding.etMsg.setText(msg)
    }

    fun setInputType(type: Int) {
        binding.etMsg.inputType = type
    }

    fun setButtonText(text: String) {
        binding.tvConfirm.text = text
    }

    fun setAutoDismiss(isAutoDismiss: Boolean) {
        this.isAutoDismiss = isAutoDismiss
    }

    fun setOnConfirmListener(listener: (String) -> Unit) {
        this.onBtnClick = listener
    }
}