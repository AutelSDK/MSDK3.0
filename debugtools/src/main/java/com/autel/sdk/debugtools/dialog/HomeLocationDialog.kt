package com.autel.sdk.debugtools.dialog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.HomeLocationDouble
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.enums.LocationTypeEnum
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.SDKBaseDialog
import com.autel.sdk.debugtools.databinding.HomeLocationDialogBinding

/**
 *
 *
 * @author Autel Robotics on 2023/5/18.
 */
class HomeLocationDialog(
    context: Context,
    isHomeLocation: Boolean = false,
    isGimbalSetting: Boolean = false,
    isNightSlamSetting:Boolean = false
) : SDKBaseDialog(context) {

    private val binding = HomeLocationDialogBinding.inflate(LayoutInflater.from(context))
    private var onBtnClick: ((HomeLocationDouble) -> Unit)? = null

    init {
        binding.spHost.setSelection(1)
        binding.tvCancel.setOnClickListener { dismiss() }

        if (isHomeLocation) {
            binding.tvTitle.text = context.getString(R.string.vps_home_location_setting)
        } else if (isNightSlamSetting) {
            binding.tvTitle.text = context.getString(R.string.part_night_slam)
            binding.tvHost.visibility = android.view.View.INVISIBLE
            binding.spHost.visibility = android.view.View.INVISIBLE
        } else if (!isGimbalSetting) {
            binding.tvTitle.text = context.getString(R.string.start_location_setting)
            binding.tvHost.visibility = android.view.View.INVISIBLE
            binding.spHost.visibility = android.view.View.INVISIBLE
            binding.tvPsw.visibility = android.view.View.INVISIBLE
            binding.etPsw.visibility = android.view.View.INVISIBLE
        } else {
            binding.tvTitle.text = context.getString(R.string.rotation_gimbal_attitude)
            binding.tvHost.text = context.getString(R.string.rotation_attitude_mode)
            binding.tvPost.text = "Pitch"
            binding.etPost.hint = "Pitch"
            binding.tvName.text = "Roll"
            binding.etName.hint = "Roll"
            binding.tvPsw.text = "Yaw"
            binding.etPsw.hint = "Yaw"

            val data = context.resources.getStringArray(R.array.debug_gimbal_setting_mode)
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, data)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spHost.adapter = adapter
        }

        binding.tvConfirm.setOnClickListener {
            val locationType = when (binding.spHost.selectedItemId) {
                0L -> LocationTypeEnum.TAKE_OFF
                1L -> LocationTypeEnum.RC
                else -> LocationTypeEnum.DRONE_CURRENT
            }
            val latitude = binding.etName.text.toString().toFloatOrNull() ?: 0.0f
            val longitude = binding.etPost.text.toString().toFloatOrNull() ?: 0.0f
            val height = binding.etPsw.text.toString().toFloatOrNull() ?: 0.0f
            onBtnClick?.invoke(HomeLocationDouble(latitude.toDouble(), longitude.toDouble(), height.toDouble(), locationType))
            dismiss()
        }
    }

    fun updateData(pos: Int, value1: Float, value2: Float, value3: Float) {
        binding.spHost.setSelection(pos)
        binding.etPost.setText(value1.toString())
        binding.etName.setText(value2.toString())
        binding.etPsw.setText(value3.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
    }

    fun setTitle(title: String) {
        binding.tvTitle.text = title
    }

    fun setOnConfirmListener(listener: (HomeLocationDouble) -> Unit) {
        this.onBtnClick = listener
    }
}