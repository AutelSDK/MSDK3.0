package com.autel.sdk.debugtools.dialog

import com.autel.drone.sdk.log.SDKLog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.autel.drone.sdk.vmodelx.enums.MoveMode
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.CoordinateType
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.UAVImageControlMessageBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.UAVPositionControlMessageBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.mission.bean.UAVVelocityControlMessageBean
import com.autel.drone.sdk.vmodelx.module.camera.bean.LensTypeEnum
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.SDKBaseDialog
import com.google.gson.Gson

class UAVControlDialog(
    context: Context,
    private val type: Int,
    private val initialData: String? = null
) : SDKBaseDialog(context) {
    private lateinit var spinnerCoordinateType: Spinner
    private lateinit var etPositions: EditText
    private lateinit var etMaxSpeed: EditText
    private lateinit var etYaw: EditText
    private lateinit var etMaxYawRate: EditText
    private lateinit var etGimbalPos: EditText
    private lateinit var etMaxGimbalRate: EditText
    private lateinit var etVelocity: EditText
    private lateinit var etYawRate: EditText
    private lateinit var etGimbalRate: EditText

    private lateinit var cbPosValid: CheckBox
    private lateinit var cbYawValid: CheckBox
    private lateinit var cbGimbalValid: CheckBox
    private lateinit var cbVelocityValid: CheckBox
    private lateinit var cbYawRateValid: CheckBox
    private lateinit var cbGimbalRateValid: CheckBox

    private lateinit var spLensType: Spinner
    private lateinit var spMoveMode: Spinner
    private lateinit var etMoveTime: EditText
    private lateinit var etImageWidth: EditText
    private lateinit var etImageHeight: EditText
    private lateinit var ivLoading: ImageView
    private lateinit var layoutControl: LinearLayout
    private lateinit var layoutImage: LinearLayout
    private lateinit var layoutVelocity: LinearLayout
    private lateinit var layoutAll: LinearLayout
    private lateinit var btnConfirm: Button

    private val handler: Handler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            FREQUENCY_MESSAGE -> sendFrequencyData2()
            else -> {}
        }
        return@Handler true
    }

    var onDataEntered: ((String) -> Unit)? = null

    private var lensTypes: List<LensTypeEnum>? = null
    private var sending = false
    private var resultData: String = ""

    companion object {
        private const val FREQUENCY_MESSAGE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_uav_control)

        setCanceledOnTouchOutside(false)
        setCancelable(false)

        initializeViews()
        if (type != 2) {
            setupCoordinateTypeSpinner()
        } else {
            setupMoveModeSpinner()
            setupLensTypeSpinner()
        }
        setupInitialData()

        findViewById<TextView>(R.id.tvTitle).text = if (type == 0) "Position Control" else if (type == 1) "Velocity Control" else "Image Control"
        setOnDismissListener {
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun initializeViews() {
        layoutAll = findViewById(R.id.layout1)
        layoutControl = findViewById(R.id.layoutControl)
        layoutImage = findViewById(R.id.layoutImage)
        layoutVelocity = findViewById(R.id.layoutVelocityControl)
        if (type != 2) {
            layoutAll.visibility = android.view.View.VISIBLE
            layoutImage.visibility = android.view.View.GONE
            layoutVelocity.visibility = android.view.View.GONE
            layoutControl.visibility = android.view.View.VISIBLE

            spinnerCoordinateType = findViewById(R.id.spinnerCoordinateType)
            etPositions = findViewById(R.id.etPositions)
            etMaxSpeed = findViewById(R.id.etMaxSpeed)
            etYaw = findViewById(R.id.etYaw)
            etMaxYawRate = findViewById(R.id.etMaxYawRate)
            etGimbalPos = findViewById(R.id.etGimbalPos)
            etMaxGimbalRate = findViewById(R.id.etMaxGimbalRate)

            cbPosValid = findViewById(R.id.cbPositionValid)
            cbYawValid = findViewById(R.id.cbYawValid)
            cbGimbalValid = findViewById(R.id.cbGimbalValid)

            // Show/hide relevant fields based on control type
            if (type == 1) {
                layoutControl.visibility = android.view.View.GONE
                layoutVelocity.visibility = android.view.View.VISIBLE
                // Add velocity control fields
                etVelocity = findViewById(R.id.etVelocity)
                cbVelocityValid = findViewById(R.id.cbVelocityValid)
                cbYawRateValid = findViewById(R.id.cbYawRateValid)
                cbGimbalRateValid = findViewById(R.id.cbGimbalRateValid)
                etYawRate = findViewById(R.id.etYawRate)
                etGimbalRate = findViewById(R.id.etGimbalRate)
            }
        } else {
            layoutAll.visibility = android.view.View.GONE
            layoutImage.visibility = android.view.View.VISIBLE
            spLensType = findViewById(R.id.spinnerLensType)
            spMoveMode = findViewById(R.id.spinnerMoveMode)
            etMoveTime = findViewById(R.id.etMoveTime)
            etImageWidth = findViewById(R.id.etImageWidth)
            etImageHeight = findViewById(R.id.etImageHeight)
        }
        ivLoading = findViewById(R.id.iv_loading)
        btnConfirm = findViewById(R.id.btnConfirm)
        findViewById<Button>(R.id.btnCancel).setOnClickListener { handleCancel() }
        btnConfirm.setOnClickListener { handleOk() }
    }

    private fun setupCoordinateTypeSpinner() {
        val coordinateTypes = CoordinateType.values()
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            coordinateTypes.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCoordinateType.adapter = adapter
    }

    private fun setupInitialData() {
        if (initialData != null) {
            try {
                when (type) {
                    0 -> {
                        val data =
                            Gson().fromJson(initialData, UAVPositionControlMessageBean::class.java)
                        populatePositionData(data)
                    }

                    1 -> {
                        val data =
                            Gson().fromJson(initialData, UAVVelocityControlMessageBean::class.java)
                        populateVelocityData(data)
                    }

                    else -> {
                        val data =
                            Gson().fromJson(initialData, UAVImageControlMessageBean::class.java)
                        populateImageData(data)
                    }
                }
            } catch (e: Exception) {
                SDKLog.printSafeStackTrace(e)
            }
        }
    }

    private fun populatePositionData(data: UAVPositionControlMessageBean) {
        spinnerCoordinateType.setSelection(data.coordinateType.ordinal)
        etPositions.setText(data.positons.joinToString(","))
        etMaxSpeed.setText(data.maxSpeed.toString())
        etYaw.setText(data.yaw.toString())
        etMaxYawRate.setText(data.maxYawRate.toString())
        etGimbalPos.setText(data.gimbalPos.joinToString(","))
        etMaxGimbalRate.setText(data.maxGimbalRate.toString())

        cbPosValid.isChecked = data.posValid
        cbYawValid.isChecked = data.yawValid
        cbGimbalValid.isChecked = data.gimbalValid
    }

    private fun populateImageData(data: UAVImageControlMessageBean) {
        spMoveMode.setSelection(data.moveMode.ordinal)
        spMoveMode.setSelection(0)
        etMoveTime.setText(data.moveTime.toString())
        etImageWidth.setText(data.imageWidth.toString())
        etImageHeight.setText(data.imageHeight.toString())
    }

    private fun setupMoveModeSpinner() {
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            listOf(context.getString(R.string.debug_text_single), context.getString(R.string.debug_text_continue))
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMoveMode.adapter = adapter
    }

    private fun setupLensTypeSpinner() {
        val drone = DeviceManager.getDeviceManager().getFirstDroneDevice()
        lensTypes = drone?.getCameraAbilitySetManger()?.getLensList(drone.getGimbalDeviceType())
            ?: emptyList()
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            lensTypes?.map { it.name } ?: emptyList()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spLensType.adapter = adapter
    }

    private fun populateVelocityData(data: UAVVelocityControlMessageBean) {
        spinnerCoordinateType.setSelection(data.coordinateType.ordinal)
        etVelocity.setText(data.velocity.joinToString(","))
        etYawRate.setText(data.yawRate.toString())
        etGimbalRate.setText(data.gimbalRate.joinToString(","))

        cbVelocityValid.isChecked = data.velocityValid
        cbYawRateValid.isChecked = data.yawRateValid
        cbGimbalRateValid.isChecked = data.gimbalRateValid
    }

    private fun handleCancel() {
        if (!sending) {
            dismiss()
            return
        }

        sending = false
        btnConfirm.isEnabled = true
        ivLoading.clearAnimation()
        ivLoading.visibility = android.view.View.GONE
        handler.removeMessages(FREQUENCY_MESSAGE)
    }

    private fun handleOk() {
        if (sending) return
        try {
            when (type) {
                0 -> {
                    val data = UAVPositionControlMessageBean(
                        coordinateType = CoordinateType.values()[spinnerCoordinateType.selectedItemPosition],
                        posValid = cbPosValid.isChecked,
                        positons = etPositions.text.toString().split(",").mapNotNull { it.toFloatOrNull() },
                        maxSpeed = etMaxSpeed.text.toString().toFloatOrNull() ?: 0f,
                        yawValid = cbYawValid.isChecked,
                        yaw = etYaw.text.toString().toFloatOrNull() ?: 0f,
                        maxYawRate = etMaxYawRate.text.toString().toFloatOrNull() ?: 0f,
                        gimbalValid = cbGimbalValid.isChecked,
                        gimbalPos = etGimbalPos.text.toString().split(",").mapNotNull { it.toFloatOrNull() },
                        maxGimbalRate = etMaxGimbalRate.text.toString().toFloatOrNull() ?: 0f
                    )
                    onDataEntered?.invoke(Gson().toJson(data))
                    dismiss()
                }

                1 -> {
                    val data = UAVVelocityControlMessageBean(
                        coordinateType = CoordinateType.values()[spinnerCoordinateType.selectedItemPosition],
                        velocityValid = cbVelocityValid.isChecked,
                        velocity = etVelocity.text.toString().split(",")
                            .mapNotNull { it.toFloatOrNull() },
                        yawRateValid = cbYawRateValid.isChecked,
                        yawRate = etYawRate.text.toString().toFloatOrNull() ?: 0f,
                        gimbalRateValid = cbGimbalRateValid.isChecked,
                        gimbalRate = etGimbalRate.text.toString().split(",")
                            .mapNotNull { it.toFloatOrNull() }
                    )
                    resultData = Gson().toJson(data)
                    sendFrequencyData()
                }

                else -> {
                    val drone = DeviceManager.getDeviceManager().getFirstDroneDevice() ?: return
                    val lensType = lensTypes?.get(spLensType.selectedItemPosition) ?: LensTypeEnum.Zoom
                    val data = UAVImageControlMessageBean(
                        moveTime = etMoveTime.text.toString().toIntOrNull() ?: 1000,
                        cameraId = drone.getCameraAbilitySetManger().getLenId(lensType, drone.getGimbalDeviceType()) ?: 0,
                        moveMode = MoveMode.values()[spMoveMode.selectedItemPosition],
                        imageWidth = etImageWidth.text.toString().toIntOrNull() ?: 0,
                        imageHeight = etImageHeight.text.toString().toIntOrNull() ?: 0
                    )
                    resultData = Gson().toJson(data)
                    if (data.moveMode == MoveMode.MODE_SINGLE) {
                        onDataEntered?.invoke(Gson().toJson(data))
                        return
                    }
                    sendFrequencyData()
//                    onDataEntered?.invoke(Gson().toJson(data))
                }
            }
        } catch (e: Exception) {
            SDKLog.printSafeStackTrace(e)
        }
    }

    private fun sendFrequencyData() {
        sending = true
        ivLoading.visibility = android.view.View.VISIBLE
        btnConfirm.isEnabled = false
        val rotateAnimation = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation.duration = 600
        rotateAnimation.repeatCount = -1
        rotateAnimation.interpolator = LinearInterpolator()
        ivLoading.startAnimation(rotateAnimation)

        handler.sendEmptyMessage(FREQUENCY_MESSAGE)
    }

    private fun sendFrequencyData2() {
        if (sending) {
            onDataEntered?.invoke(resultData)
            handler.sendEmptyMessageDelayed(FREQUENCY_MESSAGE, 100)
        }
    }
} 