package com.autel.sdk.debugtools

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.autel.drone.sdk.vmodelx.SDKManager

/**
 * SDKInfo View interfaces operating MSDKInfoModel features
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/12/24.
 */
class MSDKInfoVm : ViewModel() {

    val msdkInfo = MutableLiveData<MSDKInfo>()
    val mainTitle = MutableLiveData<String>()
    val titleControl = MutableLiveData<Boolean>()

    init {
        msdkInfo.value = MSDKInfo(SDKManager.get().getSDKVersion())
        msdkInfo.value?.buildVer = ""
        refreshMSDKInfo()
    }


    fun refreshMSDKInfo() {
        msdkInfo.postValue(msdkInfo.value)
    }
}
