package com.autel.sdk.debugtools;


import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.autel.drone.sdk.libbase.error.AutelStatusCode;
import com.autel.drone.sdk.libbase.error.IAutelCode;
import com.autel.drone.sdk.log.SDKLog;
import com.autel.drone.sdk.pbprotocol.interaction.msg.payload.ChannelTopicMessageBean;
import com.autel.drone.sdk.vmodelx.SDKManager;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelActionKeyInfo;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKeyInfo;
import com.autel.drone.sdk.vmodelx.mono.KeyBackendRouter;
import com.autel.drone.sdk.vmodelx.mono.MonoKeyOperation;
import com.autel.drone.sdk.vmodelx.utils.ToastUtils;
import com.autel.sdk.debugtools.fragment.KeyItemActionListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * KeyItem encapsulates key capabilities and actions
 *
 * @author Autel Robotics on 2022/10/29.
 */

public class KeyItem<P, R> extends KeyBaseStructure implements Comparable<KeyItem<?, ?>> {

    public static final String TAG = KeyItem.class.getSimpleName();
    public static final int GET = 0;
    public static final int SET = 1;
    public static final int ACTION = 2;
    public static final int LISTEN = 3;
    public static final int UN_LISTEN = 4;
    public static final int REPORT = 5;
    /**
     * Whether listening
     */
    public boolean isListening = false;
    /**
     * Display name
     */
    public String name;
    public boolean isSingleAutelValue;
    /**
     * Capability carrier entity of the parameter key
     */
    public AutelKeyInfo<?> keyInfo;
    /**
     * Caller-injected callback interface for result notification
     */
    protected KeyItemActionListener<Object> keyOperateCallBack;
    private final CommonCallbacks.CompletionCallbackWithParam<R> actionCallback = new CommonCallbacks.CompletionCallbackWithParam<R>() {
        @Override
        public void onSuccess(Object data) {
            if (data != null) {
                endSuccess(ACTION, data.toString());
            } else {
                endSuccess(ACTION, null);
            }
        }

        @Override
        public void onFailure(@NonNull IAutelCode error, @Nullable String errorMsg) {
            endFail(ACTION, error, errorMsg);
        }
    };
    /**
     * Push data callback (caller-injected)
     */
    protected KeyItemActionListener<String> pushCallBack;
    /**
     * Usage count, for sorting
     */
    private long count;
    private boolean isItemSelected;
    /**
     * Last GET raw value written back (for TestKey Set-Back; avoids parsing UI callback text).
     */
    @Nullable
    private String lastGetValueForSetBack;
    /**
     * Push callback
     */
    private CommonCallbacks.KeyListener<R> listenSDKCallback = new CommonCallbacks.KeyListener<R>() {
        @Override
        public void onChannelTopicMessageReceive(@Nullable ChannelTopicMessageBean value) {
            StringBuilder sb = new StringBuilder("【LISTEN】");
            sb.append(getName());
            sb.append(" onChannelTopicMessageReceive:");
            // sb.append("oldValue:").append(oldValue);
            sb.append(" value:").append(value);

            if (pushCallBack != null) {
                pushCallBack.actionChange(MsgType.MSG_SUCCESS, sb.toString());
            }
        }

        @Override
        public void onValueRealChange(R value) {
        }

        @Override
        public void onValueChange(R oldValue, R newValue) {
            StringBuilder sb = new StringBuilder("【LISTEN】");
            sb.append(getName());
            sb.append(" result:");
            // sb.append("oldValue:").append(oldValue);
            sb.append(" newValue:").append(newValue);

            if (pushCallBack != null) {
                pushCallBack.actionChange(MsgType.MSG_SUCCESS, sb.toString());
            }
        }
    };


    public KeyItem(AutelKeyInfo<?> keyInfo) {
        super();
        this.keyInfo = keyInfo;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public boolean isItemSelected() {
        return isItemSelected;
    }

    public void setItemSelected(boolean itemSelected) {
        isItemSelected = itemSelected;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? keyInfo.getIdentifier() : name;
    }

    @Nullable
    public String getLastGetValueForSetBack() {
        return lastGetValueForSetBack;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AutelKeyInfo getKeyInfo() {
        return keyInfo;
    }

    /**
     * Get the listen host
     *
     * @return
     */
    public Object getListenHolder() {
        return listenHolder;
    }

    /**
     * Whether Get is supported
     *
     * @return
     */
    public boolean canGet() {
        return keyInfo.isCanGet();
    }

    /**
     * Whether Set is supported
     *
     * @return
     */
    public boolean canSet() {
        return keyInfo.isCanSet();
    }

    /**
     * Whether Listen is supported
     *
     * @return
     */
    public boolean canListen() {
        return keyInfo.isCanListen();
    }

    /**
     * Whether it is an action
     *
     * @return
     */
    public boolean canAction() {
        return keyInfo.isCanPerformAction();
    }

    /**
     * Whether it is fixed-frequency reporting
     *
     * @return
     */
    public boolean canReport() {
        return keyInfo.isCanReport();
    }

    /**
     * Caller-injected callback interface for result notification
     *
     * @param keyOperateCallBack
     */
    public void setKeyOperateCallBack(KeyItemActionListener<Object> keyOperateCallBack) {
        this.keyOperateCallBack = keyOperateCallBack;
    }

    /**
     * Push callback
     *
     * @param pushCallBack
     */
    public void setPushCallBack(KeyItemActionListener<String> pushCallBack) {
        this.pushCallBack = pushCallBack;
    }

    /**
     * Get request
     */
    public void doGet() {
        try {
            start(GET, null);
            get(keyInfo, new CommonCallbacks.CompletionCallbackWithParam<R>() {
                @Override
                public void onFailure(@NonNull IAutelCode code, @Nullable String errorMsg) {
                    lastGetValueForSetBack = null;
                    endFail(GET, code, errorMsg);
                }

                @Override
                public void onSuccess(R data) {
                    if (data != null) {
                        lastGetValueForSetBack = formatGetResultForSet(data);
                        endSuccess(GET, lastGetValueForSetBack);
                    } else {
                        lastGetValueForSetBack = null;
                        endSuccess(GET, null);
                    }
                }
            });
        } catch (Exception e) {
            SDKLog.e(TAG, "error="+e );
            endSuccess(GET, "error="+e);
        }
    }

    private void start(int type, String param) {
        if (keyOperateCallBack != null) {
            if (param == null) {
                param = "";
            }
            final String backendTag = backendLabel(type);
            switch (type) {
                case GET:
                    keyOperateCallBack.actionChange(MsgType.MSG_INFO, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_send)
                            + "：【GET】 【" + keyInfo.keyName + "】" + backendTag + " "
                            + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter)
                            + param);
                    break;
                case SET:
                    keyOperateCallBack.actionChange(MsgType.MSG_INFO, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_send)
                            + "：【SET】 【" + keyInfo.keyName + "】" + backendTag + " "
                            + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter)
                            + param);
                    break;
                case ACTION:
                    keyOperateCallBack.actionChange(MsgType.MSG_INFO, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_send)
                            + "：【ACTION】 【" + keyInfo.keyName + "】" + backendTag + " "
                            + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter)
                            + param);
                    break;
                case LISTEN:
                    keyOperateCallBack.actionChange(MsgType.MSG_INFO, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_send)
                            + "：【LISTEN】 【" + keyInfo.keyName + "】 "
                            + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter)
                            + param);
                    break;
                case UN_LISTEN:
                    keyOperateCallBack.actionChange(MsgType.MSG_INFO, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_send)
                            + "：【UN_LISTEN】 【" + keyInfo.keyName + "】 "
                            + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter)
                            + param);
                    break;
                case REPORT:
                    keyOperateCallBack.actionChange(MsgType.MSG_INFO, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_send)
                            + "：【REPORT】 【" + keyInfo.keyName + "】" + backendTag + " "
                            + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter)
                            + param);
                    break;
            }
        }
    }

    /**
     * Dual-stack routing display: MonoService (MonoSDK gateway) or RosBridgeManager (ROS2 topic).
     */
    private String backendLabel(int type) {
        MonoKeyOperation operation = null;
        switch (type) {
            case GET:
                operation = MonoKeyOperation.GET;
                break;
            case SET:
                operation = MonoKeyOperation.SET;
                break;
            case ACTION:
                operation = MonoKeyOperation.ACTION;
                break;
            case REPORT:
                operation = MonoKeyOperation.REPORT;
                break;
            default:
                break;
        }
        if (operation == null || keyInfo == null || keyInfo.keyName == null) {
            return "";
        }
        return " [backend=" + KeyBackendRouter.displayName(keyInfo.keyName, operation) + "]";
    }

    private void endSuccess(int type, String result) {
        if (keyOperateCallBack != null) {
            if (result == null) {
                result = "";
            }
            switch (type) {
                case GET:
                    keyOperateCallBack.actionChange(MsgType.MSG_SUCCESS, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【GET】 【" + keyInfo.keyName + "】 " +
                            SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_success)
                            + "：" + result);
                    break;
                case SET:
                    keyOperateCallBack.actionChange(MsgType.MSG_SUCCESS, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【SET】 【" + keyInfo.keyName + "】 " +
                            SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_success)
                            + "：" + result);
                    break;
                case ACTION:
                    keyOperateCallBack.actionChange(MsgType.MSG_SUCCESS, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【ACTION】 【" + keyInfo.keyName + "】 " +
                            SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_success)
                            + "：" + result);
                    break;
                case LISTEN:
                    keyOperateCallBack.actionChange(MsgType.MSG_SUCCESS, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【LISTEN】 【" + keyInfo.keyName + "】 " +
                            SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_success)
                            + "：" + result);
                    break;
            }
        }
    }

    private void endFail(int type, @NonNull IAutelCode error, @Nullable String errorMsg) {
        if (keyOperateCallBack != null) {
            String errString = SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_failed)
                    + "：" + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_error_code_is)
                    + "：" + error + ","
                    + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_error_message)
                    + "：" + errorMsg;

            switch (type) {
                case GET:
                    keyOperateCallBack.actionChange(MsgType.MSG_ERROR, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【GET】 【" + keyInfo.keyName + "】" + errString);
                    break;
                case SET:
                    keyOperateCallBack.actionChange(MsgType.MSG_ERROR, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【SET】 【" + keyInfo.keyName + "】 " + errString);
                    break;
                case ACTION:
                    keyOperateCallBack.actionChange(MsgType.MSG_ERROR, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【ACTION】 【" + keyInfo.keyName + "】 " + errString);
                    break;
                case LISTEN:
                    keyOperateCallBack.actionChange(MsgType.MSG_ERROR, SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_take_over)
                            + "：【LISTEN】 【" + keyInfo.keyName + "】 " + errString);
                    break;
            }
        }
    }

    public void doGetList() {
        List<AutelKeyInfo<?>> keyList = new ArrayList<>();
        keyList.add(CameraKey.INSTANCE.getKeyAELock());
        keyList.add(CameraKey.INSTANCE.getKeyZoomFactor());
        keyList.add(CameraKey.INSTANCE.getKeyVideoEncoderConfig());
        start(GET, null);
        try {
            getList(keyList, new CommonCallbacks.CompletionCallbackWithParam<List<?>>() {
                @Override
                public void onFailure(@NonNull IAutelCode code, @Nullable String errorMsg) {
                    if (code instanceof AutelStatusCode) {
                        endFail(SET, code, ((AutelStatusCode) code).getMsg());
                    } else {
                        endFail(GET, code, errorMsg);
                    }
                }

                @Override
                public void onSuccess(List<?> objects) {
                    if (objects != null) {
                        endSuccess(GET, objects.toString());
                    } else {
                        endSuccess(GET, null);
                    }
                }
            });
        } catch (Exception e) {
            SDKLog.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    /**
     * Set request
     */
    public void doSet(String jsonStr) {
        try {
            final P p = validPrams(jsonStr);
            if (p == null) {
                return;
            }
            start(SET, jsonStr);
            set(keyInfo, p, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onFailure(@NonNull IAutelCode code, @Nullable String msg) {
                    endFail(SET, code, msg);
                }

                @Override
                public void onSuccess() {
                    endSuccess(SET, null);
                }
            });
        } catch (Exception e) {
            ToastUtils.INSTANCE.showToast(SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter_input_error));
        }
    }

    /**
     * Action request
     */
    public void doAction(String jsonStr) {
        AutelActionKeyInfo<?, ?> actionKeyInfo = (AutelActionKeyInfo<?, ?>) keyInfo;
        P p = null;
        if (jsonStr != null && !jsonStr.isEmpty()) {
            p = validPrams(jsonStr);
        }
        start(ACTION, jsonStr);
        if (p == null) {
            action(actionKeyInfo, actionCallback);
        } else {
            action(actionKeyInfo, p, actionCallback);
        }
    }

    /**
     * Action request
     */
    public void doReport(String jsonStr) {
        AutelKeyInfo<?> actionKeyInfo = (AutelKeyInfo<?>) keyInfo;
        P p = null;
        if (jsonStr != null && !jsonStr.isEmpty()) {
            p = validPrams(jsonStr);
        }
        start(REPORT, jsonStr);
        if (p == null) {
            report(actionKeyInfo, null);
        } else {
            report(actionKeyInfo, p);
        }
    }

    public void doReportWithCallback(String jsonStr) {

        AutelActionKeyInfo<?, ?> actionKeyInfo = (AutelActionKeyInfo<?, ?>) keyInfo;
        P p = null;
        if (jsonStr != null && !jsonStr.isEmpty()) {
            p = validPrams(jsonStr);
        }
        start(REPORT, jsonStr);
        if (p == null) {
            report(actionKeyInfo, actionCallback);
        } else {
            report(actionKeyInfo, p, actionCallback);
        }
    }

    /**
     * Register Listen (new API)
     *
     * @param listenHolder
     */
    public void listen(Object listenHolder) {
        start(LISTEN, null);
        this.listenHolder = listenHolder;
        isListening = true;
        listen(keyInfo, listenSDKCallback);
    }

    /**
     * Unregister Listen (new API)
     *
     * @param listenHolder
     */
    public void cancelListen(Object listenHolder) {
        start(UN_LISTEN, null);
        isListening = false;
        if (this.listenHolder == listenHolder) {
            this.listenHolder = null;
            cancelListen(keyInfo, listenSDKCallback);
            pushCallBack = null;
            listenRecord = "";
        }
    }

    /**
     * GET result to SET parameter: matches KeyValueFragment.doSet; outputs a value parseable by converter.fromJsonStr.
     */
    private String formatGetResultForSet(R data) {
        if (data == null) {
            return null;
        }
        String serialized = serializeGetResultForSet(data);
        if (serialized != null && buildParamFromJsonStr(serialized) != null) {
            return serialized;
        }
        return data.toString();
    }

    /**
     * Serialize the GET return value into a SET parameter string, preferring enum numeric fields (getValue/getIdx).
     */
    private String serializeGetResultForSet(R data) {
        if (data instanceof Enum<?>) {
            Enum<?> enumData = (Enum<?>) data;
            for (String methodName : new String[]{"getValue", "getIdx"}) {
                try {
                    java.lang.reflect.Method getter = enumData.getClass().getMethod(methodName);
                    Object value = getter.invoke(enumData);
                    if (value instanceof Number) {
                        return String.valueOf(((Number) value).intValue());
                    }
                } catch (Exception ignored) {
                    // try the next accessor
                }
            }
        }
        if (data instanceof Number || data instanceof Boolean) {
            return data.toString();
        }
        if (data instanceof String) {
            return (String) data;
        }
        return data.toString();
    }

    /**
     * Validate parameters
     *
     * @param jsonStr
     * @return
     */
    private P validPrams(String jsonStr) {
        if (Util.isBlank(jsonStr)) {
            String msg = "【SET】【" + getName() + "】【" + keyInfo.keyName + "】 "
                    + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_please_set_parameters_first);
            if (keyOperateCallBack != null) {
                keyOperateCallBack.actionChange(MsgType.MSG_INFO,msg);
            }
            ToastUtils.INSTANCE.showToast(msg);
            return null;
        }
        final P p = buildParamFromJsonStr(jsonStr);
        if (p == null) {
            String msg = "【SET】【" + getName() + "】【" + keyInfo.keyName + "】 "
                    + SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_please_set_parameters_first)
                    + jsonStr +
                    SDKManager.get().getSContext().getString(com.autel.sdk.debugtools.R.string.debug_parameter);
            if (keyOperateCallBack != null) {
                keyOperateCallBack.actionChange(MsgType.MSG_INFO,msg);
            }
            ToastUtils.INSTANCE.showToast(msg);
            return null;
        }
        return p;
    }

    /**
     * Deserialize: build an object from a JSON string
     *
     * @param jsonStr
     * @return
     */
    public P buildParamFromJsonStr(String jsonStr) {
        if (jsonStr == null) {
            return null;
        }
        P p = null;
        try {
            Object o;
            if (isRosChannel()) {
                o = keyInfo.getRos2Converter().fromJsonStr(jsonStr);
            } else {
                o = keyInfo.getTypeConverter().fromJsonStr(jsonStr);
            }
            if (o != null) {
                p = (P) o;
            }
        } catch (Exception e) {
            SDKLog.e(TAG, e.getMessage());
        }
        return p;
    }

    /**
     * Get the primitive wrapper value inside a SingleValue
     *
     * @param jsonStr
     * @return
     */
    private String getSingleJsonValue(String jsonStr) {
        String value = "";
        try {
            JSONObject jsonObj = new JSONObject(jsonStr);
            value = jsonObj.getString("value");
        } catch (Exception e) {
            SDKLog.e(TAG, e.getMessage());
        }
        return value;
    }

    /**
     * Serialize: get the default JSON string
     *
     * @return
     */
    public String getParamJsonStr() {
        if (isRosChannel()) {
            return keyInfo.getRos2Converter().getJsonStr();
        }
        return keyInfo.getTypeConverter().getJsonStr();
    }

    /**
     * Remove callback
     */
    public void removeCallBack() {
        cancelListen(listenHolder);
        keyOperateCallBack = null;
    }

    @Override
    public int compareTo(KeyItem keyItem) {
        if (keyItem.count - this.count > 0) {
            return 1;
        } else if (keyItem.count - this.count < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    public Boolean isStop() {
        if (isRosChannel()) {
            return keyInfo.getRos2Converter().isStop();
        }

        return keyInfo.getTypeConverter().isStop();
    }

    public void  setStop(Boolean stop) {
        if (isRosChannel()) {
            keyInfo.getRos2Converter().setStop(stop);
            return;
        }
        keyInfo.getTypeConverter().setStop(stop);
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    // TODO: add a ROS2 usage check here; without this logic an NPE can occur
    // java.lang.NullPointerException: Attempt to invoke virtual method
    // 'java.lang.String com.autel.drone.sdk.vmodelx.manager.keyvalue.converter.IAutelConverter.getJsonStr()' on a null object reference
    private boolean isRosChannel() {
        return keyInfo.getRos2Converter() != null && (keyInfo.rosGetTopic != null || keyInfo.rosSetTopic != null || keyInfo.rosParam != 0);
    }
}
