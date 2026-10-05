package com.autel.sdk.debugtools;

import com.autel.drone.sdk.log.SDKLog;
import com.autel.drone.sdk.vmodelx.SDKManager;
import com.autel.drone.sdk.vmodelx.constants.SDKUtils;
import com.autel.drone.sdk.vmodelx.interfaces.IBaseDevice;
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager;
import com.autel.drone.sdk.vmodelx.manager.DeviceManager;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelActionKeyInfo;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKeyInfo;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.KeyTools;
import com.autel.drone.sdk.vmodelx.utils.ToastUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Key base structure provider for generated mesage
 * Copyright: Autel Robotics
 *
 * @author Autel Robotics on 2022/10/29.
 */
public class KeyBaseStructure<P, R> {

    public int retryCount = 3;

    private static final String TAG = KeyBaseStructure.class.getSimpleName();

    /**
     * Set parameters
     */
    protected P param;

    /**
     * Return result
     */
    protected R result;

    /**
     * Push data records
     */
    protected String listenRecord = "";

    /**
     * Push listener host
     */
    protected Object listenHolder;
    protected int componetIndex = -1;
    protected int subComponetType = -1;
    protected int subComponetIndex = -1;
    /**
     * Enum list
     */
    protected Map<String, List<EnumItem>> subItemMap = new HashMap<>();

    public int getComponetIndex() {
        return componetIndex;
    }

    public void setComponetIndex(int componetIndex) {
        this.componetIndex = componetIndex;
    }

    public int getSubComponetType() {
        return subComponetType;
    }

    public void setSubComponetType(int subComponetType) {
        this.subComponetType = subComponetType;
    }

    public int getSubComponetIndex() {
        return subComponetIndex;
    }

    public void setSubComponetIndex(int subComponetIndex) {
        this.subComponetIndex = subComponetIndex;
    }

    //    /**
//     * Instantiate generic types via reflection
//     */
    protected void initGenericInstance() {
        try {
            KeyItemHelper.INSTANCE.initClassData(param);
            KeyItemHelper.INSTANCE.initClassData(result);
            initSubItemData();
        } catch (Exception e) {
            SDKLog.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    /**
     * Initialize list data for enums or nested enum lists
     */
    protected void initSubItemData() {
        if (param == null) {
            return;
        }
        subItemMap.clear();
        subItemMap.putAll(KeyItemHelper.INSTANCE.initSubItemData(param));
    }


    /**
     * Get the parameter instance to set
     *
     * @return
     */
    public P getParam() {
        return param;
    }

    /**
     * Get the parameter map list
     *
     * @return
     */
    public Map<String, List<EnumItem>> getSubItemMap() {
        return subItemMap;
    }

    /**
     * Get push data records
     *
     * @return
     */
    public String getListenRecord() {
        return listenRecord;
    }


    /**
     * Async get
     *
     * @param keyInfo
     * @param getCallback
     */
    protected void get(AutelKeyInfo<R> keyInfo, CommonCallbacks.CompletionCallbackWithParam<R> getCallback) {
        AutelKey<R> key = createKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).getValue(key, getCallback, retryCount);
        }
    }

    protected IKeyManager getKeyManager(AutelKey key) {

        if (SDKUtils.INSTANCE.isRemoteControlMode(key.mComponentIndex)) {
            if (DeviceManager.Companion.getDeviceManager().getFirstRemoteDevice() != null) {
                return DeviceManager.Companion.getDeviceManager().getFirstRemoteDevice().getKeyManager();
            } else {
                return null;
            }
        } else if (SDKUtils.INSTANCE.isRCHiddenMode(key.mComponentIndex)) {
            if (DeviceManager.Companion.getDeviceManager().getFirstRCHiddenDevice() != null) {
                return DeviceManager.Companion.getDeviceManager().getFirstRCHiddenDevice().getKeyManager();
            } else {
                return null;
            }
        } else if(SDKUtils.INSTANCE.isRcAtService(key.mComponentIndex)){
            if (DeviceManager.Companion.getDeviceManager().getFirstRemoteDevice() != null) {
                return DeviceManager.Companion.getDeviceManager().getFirstRemoteDevice().getATKeyManager();
            } else {
                return null;
            }
        } else if(SDKUtils.INSTANCE.isUpgrade(key.mComponentIndex)){
            if (DeviceManager.Companion.getDeviceManager().getFirstRemoteDevice() != null) {
                return DeviceManager.Companion.getDeviceManager().getFirstDroneDevice().getUpgradeKeyManager();
            } else {
                return null;
            }
        } else {
            if (getDefaultDrone() != null) {
                return getDefaultDrone().getKeyManager();
            } else {
                return null;
            }
        }
    }

    private IBaseDevice getDefaultDrone() {
        return DeviceManager.Companion.getDeviceManager().getFirstDroneDevice();
    }

    protected void getList(List<AutelKeyInfo<?>> keyInfoList, CommonCallbacks.CompletionCallbackWithParam<List<?>> getCallback) {
        List<AutelKey<?>> keyList = new ArrayList<>();
        for (AutelKeyInfo keyInfo : keyInfoList) {
            AutelKey key = createKey(keyInfo);
            keyList.add(key);
        }
        if (getKeyManager(keyList.get(0)) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(keyList.get(0)).getValueList(keyList, getCallback, retryCount);
        }
    }

    /**
     * Set property
     *
     * @param keyInfo
     * @param setCallback
     */
    protected void set(AutelKeyInfo<P> keyInfo, P param, CommonCallbacks.CompletionCallback setCallback) {
        AutelKey<P> key = createKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).setValue(key, param, setCallback, retryCount);
        }
    }


    /**
     * Set property
     *
     * @param keyInfoList
     * @param params
     * @param setCallback
     */
    protected void setList(List<AutelKeyInfo<?>> keyInfoList, List<P> params, CommonCallbacks.CompletionCallbackWithParam<List<?>> setCallback) {
        List<AutelKey<Object>> keyList = new ArrayList<>();
        for (AutelKeyInfo keyInfo : keyInfoList) {
            AutelKey key = createKey(keyInfo);
            keyList.add(key);
        }
        if (getKeyManager(keyList.get(0)) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(keyList.get(0)).setValueList(keyList, params, setCallback);
        }
    }

    /**
     * Set listener
     *
     * @param keyInfo
     * @param listenCallback
     */
    protected void listen(AutelKeyInfo<R> keyInfo, CommonCallbacks.KeyListener<R> listenCallback) {
        AutelKey<R> key = createKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).listen(key, listenCallback);
        }
    }

    /**
     * Cancel listener
     *
     * @param keyInfo
     */
    protected void cancelListen(AutelKeyInfo<R> keyInfo, CommonCallbacks.KeyListener<R> listenCallback) {
        AutelKey<R> key = createKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).cancelListen(key, listenCallback);
        }
    }

    public void cancelAllListen() {
        IBaseDevice device = getDefaultDrone();
        if (device == null || DeviceManager.Companion.getDeviceManager().getFirstRemoteDevice() == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_device_null));
        } else {
//            device.getKeyManager().removeAllListen();
//            DeviceManager.Companion.getDeviceManager().getFirstRemoteDevice().getKeyManager().removeAllListen();
        }
    }

    /**
     * No-arg action
     *
     * @param keyInfo
     * @param actonCallback
     */
    protected void action(AutelActionKeyInfo<P, R> keyInfo, CommonCallbacks.CompletionCallbackWithParam<R> actonCallback) {
        AutelKey.ActionKey<P, R> key = createActionKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).performAction(key, null, actonCallback, retryCount, "0");
        }
    }

    /**
     * Action with args
     *
     * @param keyInfo
     * @param param
     * @param actonCallback
     */
    protected void action(AutelActionKeyInfo<P, R> keyInfo, P param, CommonCallbacks.CompletionCallbackWithParam<R> actonCallback) {
        AutelKey.ActionKey<P, R> key = createActionKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).performAction(key, param, actonCallback,retryCount, "0");
        }
    }

    /**
     * Report API
     *
     * @param keyInfo
     * @param param
     */
    protected void report(AutelKeyInfo<P> keyInfo, P param) {
        AutelKey<P> key = createKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).setFrequencyReport(key, param);
        }
    }

    protected void report(AutelActionKeyInfo<P, R> keyInfo, P param, CommonCallbacks.CompletionCallbackWithParam<R> actonCallback) {
        AutelKey.ActionKey<P, R> key = createActionKey(keyInfo);
        if (getKeyManager(key) == null) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_keymanager_null));
        } else {
            getKeyManager(key).setFrequencyReport(key, param, actonCallback);
        }
    }


    protected AutelKey.ActionKey<P, R> createActionKey(AutelActionKeyInfo<P, R> keyInfo) {
        AutelKey.ActionKey<P, R> key = null;
        key = KeyTools.createKey(keyInfo);
        return key;
    }

    protected <Param> AutelKey<Param> createKey(AutelKeyInfo<Param> keyInfo) {
        AutelKey<Param> key;
        key = KeyTools.createKey(keyInfo);
        return key;
    }

}
