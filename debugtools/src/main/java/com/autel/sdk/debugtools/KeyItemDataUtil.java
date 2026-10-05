package com.autel.sdk.debugtools;


import com.autel.drone.sdk.SDKConfig;
import com.autel.drone.sdk.log.SDKLog;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AIServiceKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AITrackingKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AccessoriesProxyKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AccurateRetakeKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AirLinkKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AutonomyKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CameraKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CloudApiKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommandCenterKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightControlKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightMissionKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightPropertyKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.GimbalKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.HardwareDataSecurityKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.LteModuleKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.MissionManagerKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.MqttPropertyKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.NestKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.AMeshKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.NetMeshKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.NtripAccountKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.PayloadCameraKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.PayloadGimbalKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.PayloadKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.PayloadUpgradeKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.PayloadWidgetKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RadarPropertyKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RemoteControllerKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RemoteIDKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RtcKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RtkPropertKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RtmpKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.SystemManagerKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.UgrVehicleKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.VisionKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.WifiKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelActionKeyInfo;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKeyInfo;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.DFCommonCmdKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.DFFlightControlKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.DFMeshKey;
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.dragonfish.WifiBaseStationKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Key items generator data utils class
 *
 * @author Autel Robotics on 2022/12/17.
 */

public class KeyItemDataUtil {
    private static final String TAG = KeyItemDataUtil.class.getSimpleName();
    private static final String KEY_RREFIX = "Key";
    private static final String UNSUPPORTED_KEY_CLASS_PREFIX = "Unsupported";

    private KeyItemDataUtil() {
        //dosomething
    }



    public static void initAirlinkKeyList(List<KeyItem<Object, Object>> keylist) {

        initList(keylist, AirLinkKey.class);
    }

    public static void initAIServiceKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, AIServiceKey.class);
    }


    public static void initAccurateRetakeKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, AccurateRetakeKey.class);
    }


    public static void initGimbalKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, GimbalKey.class);
    }

    public static void initCameraKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, CameraKey.class);
    }

    public static void initSystemManagerKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, SystemManagerKey.class);
    }

    public static void initFlightParamsKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, FlightPropertyKey.class);
    }

    public static void initFlightControllerKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, FlightControlKey.class);
    }

    public static void initRemoteControllerKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, RemoteControllerKey.class);
    }

    public static void initBleKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initProductKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initRtkBaseStationKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initRtkMobileStationKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initOcuSyncKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initVisionKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, VisionKey.class);
    }

    public static void initRtkKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, RtkPropertKey.class);
    }


    public static void initWifiKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, WifiKey.class);
    }
    public static void initRadarKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, RadarPropertyKey.class);
    }
    public static void initNtripKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, NtripAccountKey.class);
    }

    public static void initLteModuleList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, LteModuleKey.class);
    }
    public static void initMqttKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, MqttPropertyKey.class);
    }

    public static void initAppKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initMobileNetworkKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initMobileNetworkLinkRCKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initOnboardKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initPayloadKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initLidarKeyList(List<KeyItem<Object, Object>> keyList) {
    }

    public static void initMissionKeyList(List<KeyItem<Object, Object>> keyList) {

        initList(keyList, FlightMissionKey.class);
    }

    public static void initRemoteIdKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, RemoteIDKey.class);
    }

    public static void initTrackingKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, AITrackingKey.class);
    }

    public static void initCommonUploadKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, CommonKey.class);
    }

    public static void initMeshKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, NetMeshKey.class);
    }

    public static void initNestKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, NestKey.class);
    }

    public static void initMissionManagerKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, MissionManagerKey.class);
    }
    public static void initAccessoriesKeyListList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, AccessoriesProxyKey.class);
    }

    public static void initCloudAPiKeyListList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, CloudApiKey.class);
    }

    public static void initHardwareDataSecurityListList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, HardwareDataSecurityKey.class);
    }

    public static void initRtmpKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, RtmpKey.class);
    }
    public static void initCommandCenterKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, CommandCenterKey.class);
    }

    public static void initRtcKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, RtcKey.class);
    }

    public static void initPayloadKeyKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, PayloadKey.class);
    }

    public static void initPayloadGimbalKeyKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, PayloadGimbalKey.class);
    }
    public static void initPayloadCameraKeyKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, PayloadCameraKey.class);
    }

    public static void initPayloadWidgetKeyKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, PayloadWidgetKey.class);
    }

    public static void initPayloadUpgradeKeyKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, PayloadUpgradeKey.class);
    }

    public static void initAMeshKeyKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, AMeshKey.class);
    }

    public static Class<?> getAMeshKeyClass() {
        return AMeshKey.class;
    }

    public static void initDFFlightControlKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, DFFlightControlKey.class);
    }

    public static void initDFCommonCmdKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, DFCommonCmdKey.class);
    }

    public static void initWifiStationCmdKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, WifiBaseStationKey.class);
    }

    public static void initDFMeshDeviceKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, DFMeshKey.class);
    }

    public static void initUGRVehicleKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, UgrVehicleKey.class);
    }

    /**
     * Get fields of this class and its superclasses
     *
     * @param clazz current class object
     * @return field array
     */
    private static Field[] getAllFields(Class<?> clazz) {
        List<Field> fieldList = new ArrayList<>();
        while (clazz != null) {
            fieldList.addAll(new ArrayList<>(Arrays.asList(clazz.getDeclaredFields())));
            clazz = clazz.getSuperclass();
        }
        Field[] fields = new Field[fieldList.size()];
        return fieldList.toArray(fields);
    }


    public static boolean checkList(List<KeyItem<Object, Object>> keyList) {
        return keyList != null && !keyList.isEmpty();
    }

    /**
     * create key list
     *
     * @param keylist pass list for add more items
     * @param clazz   for botaining this class and methods
     */

    private static void initList(List<KeyItem<Object, Object>> keylist, Class<?> clazz) {
        if (checkList(keylist)) {
            return;
        }
        if (isUnsupportedKeyContainer(clazz)) {
            SDKLog.i(TAG, "skip unsupported key container: " + clazz.getSimpleName());
            return;
        }
        Field[] fields = getAllFields(clazz);
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(null);
                Class<?> fieldClass = field.getType();
                String keyName = field.getName();

                if (fieldClass.equals(AutelKeyInfo.class) || fieldClass.equals(AutelActionKeyInfo.class)) {
                    AutelKeyInfo<?> keyInfo = (AutelKeyInfo<?>) fieldValue;
                    if (!shouldIncludeInTestKey(keyInfo)) {
                        continue;
                    }
                    KeyItem<Object, Object> item = new KeyItem(keyInfo);
                    genericItem(item, keyInfo, keyName);

                    if (keylist != null) {
                        keylist.add(item);
                    }
                }
            } catch (Exception e) {
                SDKLog.e(TAG, Objects.requireNonNull(e.getMessage()));
            }

        }
    }


    public static List<KeyItem<Object, Object>> getKeyItemList(Class<?> clazz) {
        List<KeyItem<Object, Object>> keylist = new ArrayList();
        if (isUnsupportedKeyContainer(clazz)) {
            SDKLog.i(TAG, "skip unsupported key container: " + clazz.getSimpleName());
            return keylist;
        }
        Field[] fields = getAllFields(clazz);
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object fieldValue = field.get(null);
                Class<?> fieldClass = field.getType();
                String keyName = field.getName();

                if (fieldClass.equals(AutelKeyInfo.class) || fieldClass.equals(AutelActionKeyInfo.class)) {
                    AutelKeyInfo<?> keyInfo = (AutelKeyInfo<?>) fieldValue;
                    if (!shouldIncludeInTestKey(keyInfo)) {
                        continue;
                    }
                    KeyItem<Object, Object> item = new KeyItem(keyInfo);
                    genericItem(item, keyInfo, keyName);
                    if (keylist != null) {
                        keylist.add(item);
                    }
                }
            } catch (Exception e) {
                SDKLog.e(TAG, Objects.requireNonNull(e.getMessage()));
            }

        }
        return keylist;
    }

    private static boolean isUnsupportedKeyContainer(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        if (clazz.getSimpleName().startsWith(UNSUPPORTED_KEY_CLASS_PREFIX)) {
            return true;
        }
        // PbLegacy container moved to legacy.v25.keys; the debug page only shows @Deprecated aliases in the main Key classes
        return clazz.getName().contains(".legacy.v25.keys.Unsupported");
    }

    /**
     * TestKey display policy (§2.1 SSOT):
     * - E1 PB mode: show PbLegacy (ros2Supported=false) keys for VPS regression
     * - E2 ROS mode: show only native ROS keys (ros2Supported=true)
     */
    private static boolean shouldIncludeInTestKey(AutelKeyInfo<?> keyInfo) {
        if (!SDKConfig.Companion.isUseRos()) {
            return true;
        }
        return keyInfo.isRos2Supported();
    }

    /**
     * generating item key for with prefix or item it self
     *
     * @param item    for item object
     * @param keyInfo key info for get identifier
     * @param keyName name of key
     */

    public static void genericItem(KeyItem<Object, Object> item, AutelKeyInfo<?> keyInfo, String keyName) {

        try {
            if (keyInfo.getIdentifier().isEmpty()) {
                item.setName(keyName.contains(KEY_RREFIX) ? keyName.substring(KEY_RREFIX.length()) : keyName);
            }
        } catch (Exception e) {
            SDKLog.e(TAG, Objects.requireNonNull(e.getMessage()));
        }

    }

    public static void initAutonomyKeyList(List<KeyItem<Object, Object>> keyList) {
        initList(keyList, AutonomyKey.class);
    }
}
