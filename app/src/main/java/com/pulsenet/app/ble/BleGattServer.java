package com.pulsenet.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * دور "الخادم" (Peripheral) بشبكة PulseNet - كل جهاز عم يشغّل هاد الكلاس
 * بنفس وقت ما هو "عميل" (Client) لأجهزة تانية عبر BleGattClient.
 *
 * بروتوكول التبادل (بنظام صفحات - Pagination):
 *   1. الجهاز الطالب (Client) يكتب رسالة تحكم صغيرة على خاصية "الطلب"
 *      (REQUEST_CHARACTERISTIC) فيها حقل "action":
 *        - "summary_page": بده صفحة من ملخص معلوماتنا تبلش من "cursor".
 *        - "items_page": بده الحزمة الكاملة لعدد صغير (وحدة غالباً) من الـ IDs.
 *        - "identity_only": بس بده يسجّل هويته، ما في عنده نقص.
 *   2. منجهّز الرد المناسب ونخزّنه بـ devicePendingResponses.
 *   3. الجهاز الطالب بعدين يقرأ خاصية "الملخص" أو "المعلومات" (حسب شو طلب)
 *      وبيوصله الرد المجهّز.
 *   4. بيكرر الدورة (صفحة ملخص جديدة، أو معلومة جديدة) لحد ما يخلص.
 *
 * ليش صفحات صغيرة بدل رد واحد كبير؟ لأنه بروتوكول BLE (ATT) بيمنع أي رسالة
 * وحدة توصل لأكتر من 512 بايت - أي محاولة نتجاوزها بترجع بيانات مقطوعة
 * بصمت. بتقسيم الرد لصفحات صغيرة (وقياس الحجم فعلياً بالبايت قبل الإرسال،
 * راجع DatabaseHelper.getItemsSummaryPageAsJson و Item.toJson) منضمن إنه
 * ولا رسالة توصل تتجاوز هالحد، بغض النظر كم عدد المعلومات أو طول نصوصها.
 */
public class BleGattServer {

    private static final String TAG = "BleGattServer";

    public interface DataProvider {
        /** صفحة وحدة من الملخص الخفيف تبلش من cursor (راجع DatabaseHelper.getItemsSummaryPageAsJson) */
        String getSummaryPageJson(int cursor);
        String getItemsForIds(List<String> ids);
        void onDeviceRequestReceived(String requesterDeviceId, String requesterDeviceName, String mac);
    }

    private final Context context;
    private final DataProvider dataProvider;
    private BluetoothGattServer gattServer;

    // تتبع الأجهزة المتصلة حالياً لمنع تكرار سجلات الفصل
    private final Map<String, BluetoothDevice> connectedDevicesMap = new ConcurrentHashMap<>();

    // تخزين الرد المجهز لكل جهاز
    private final Map<String, String> devicePendingResponses = new ConcurrentHashMap<>();

    // تتبع الـ MTU المفاوَض عليه لكل جهاز متصل (الافتراضي في BLE هو 23 بايت -> payload 20)
    private final Map<String, Integer> deviceMtuMap = new ConcurrentHashMap<>();

    // تجميع أجزاء الطلبات المكتوبة ذات الحجم الكبير لكل جهاز
    private final Map<String, ByteArrayOutputStream> writeBufferMap = new ConcurrentHashMap<>();

    public BleGattServer(Context context, DataProvider dataProvider) {
        this.context = context.getApplicationContext();
        this.dataProvider = dataProvider;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return;

        gattServer = bluetoothManager.openGattServer(context, serverCallback);
        if (gattServer == null) {
            Log.e(TAG, "ما قدرنا نفتح GATT Server");
            return;
        }

        BluetoothGattCharacteristic summaryCharacteristic = new BluetoothGattCharacteristic(
                BleConstants.SUMMARY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattCharacteristic itemsCharacteristic = new BluetoothGattCharacteristic(
                BleConstants.ITEMS_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattCharacteristic requestCharacteristic = new BluetoothGattCharacteristic(
                BleConstants.REQUEST_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        BluetoothGattService service = new BluetoothGattService(
                BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(summaryCharacteristic);
        service.addCharacteristic(itemsCharacteristic);
        service.addCharacteristic(requestCharacteristic);

        gattServer.addService(service);
        Log.i(TAG, "GATT Server بدأ بنجاح");
    }

    private final BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.i(TAG, "تم تحديث MTU الـ Server للجهاز (" + device.getAddress() + ") إلى: " + mtu);
            deviceMtuMap.put(device.getAddress(), mtu);
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (device == null) return;

            String mac = device.getAddress();

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // تسجيل الجهاز في الخريطة عند الاتصال
                connectedDevicesMap.put(mac, device);
                Log.i(TAG, "جهاز اتصل بالـ Server: " + mac);
                deviceMtuMap.put(mac, 23); // القيمة الافتراضية للمعايير القياسية

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // محاولة حذف الجهاز من الخريطة؛ ستتم العملية وتُرجع الكائن مرة واحدة فقط
                BluetoothDevice removedDevice = connectedDevicesMap.remove(mac);

                if (removedDevice != null) {
                    Log.i(TAG, "جهاز فصل عن الـ Server: " + mac);

                    // تنظيف الذاكرة وتحرير الموارد فور الانقطاع
                    devicePendingResponses.remove(mac);
                    deviceMtuMap.remove(mac);
                    writeBufferMap.remove(mac);

                    if (gattServer != null) {
                        try {
                            gattServer.cancelConnection(device);
                        } catch (Exception ignored) {}
                    }
                }
                // أي إشعار فصل مكرر بعد ذلك سيتم تجاهله تماماً دون طباعة أخطاء
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            // صرنا نتبادل البيانات صفحة-صفحة: سواء كانت القراءة لخاصية "الملخص"
            // أو خاصية "المعلومات"، الاثنين بيرجعوا آخر رد جهّزناه رداً على آخر
            // طلب (Request) وصل عبر processCompletedRequest - راجع تعليق البروتوكول
            // بأول الملف لتفاصيل ليش انتقلنا لنظام الصفحات.
            String json = devicePendingResponses.getOrDefault(device.getAddress(), "{}");
            if (json == null) json = "{}";

            byte[] fullData = json.getBytes(StandardCharsets.UTF_8);
            int mtu = deviceMtuMap.getOrDefault(device.getAddress(), 512);
            int maxChunkSize = Math.max(20, mtu - 3); // أقصى حجم حمولة مسموح في BLE Packet

            byte[] chunk;
            if (offset >= fullData.length) {
                chunk = new byte[0];
            } else {
                int length = Math.min(fullData.length - offset, maxChunkSize);
                chunk = new byte[length];
                System.arraycopy(fullData, offset, chunk, 0, length);
            }

            if (gattServer != null) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            String mac = device.getAddress();

            if (BleConstants.REQUEST_CHARACTERISTIC_UUID.equals(characteristic.getUuid()) && value != null) {
                try {
                    ByteArrayOutputStream buffer = writeBufferMap.get(mac);
                    if (buffer == null || offset == 0) {
                        buffer = new ByteArrayOutputStream();
                        writeBufferMap.put(mac, buffer);
                    }
                    buffer.write(value);

                    // إذا لم تكن كتابة مُجهزة على أجزاء، أو اكتمل الاستلام، نبدأ بالمعالجة
                    if (!preparedWrite) {
                        processCompletedRequest(mac, buffer.toByteArray());
                        writeBufferMap.remove(mac);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "خطأ أثناء معالجة أجزاء الكتابة: " + e.getMessage());
                }
            }

            if (responseNeeded && gattServer != null) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            String mac = device.getAddress();

            if (execute) {
                ByteArrayOutputStream buffer = writeBufferMap.remove(mac);
                if (buffer != null) {
                    processCompletedRequest(mac, buffer.toByteArray());
                }
            } else {
                writeBufferMap.remove(mac);
            }

            if (gattServer != null) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
        }
    };

    /**
     * كل رسالة توصلنا عبر خاصية "الطلب" فيها حقل "action" بيحدد شو نوع الرد
     * المطلوب تجهيزه: صفحة ملخص جديدة، أو حزمة معلومات كاملة لعدد صغير من
     * الـ IDs، أو مجرد تسجيل هوية بدون أي رد فعلي (لما ما يكون عند الطرف
     * الطالب أي نقص). هاد التقسيم لصفحات صغيرة هو يلي بيضمن ما نتجاوز الحد
     * الأقصى لحجم رسالة BLE وحدة (512 بايت) - راجع تعليق البروتوكول بأعلى الملف.
     */
    private void processCompletedRequest(String macAddress, byte[] data) {
        try {
            String requestJson = new String(data, StandardCharsets.UTF_8);
            org.json.JSONObject obj = new org.json.JSONObject(requestJson);
            String action = obj.optString("action", "items_page"); // توافق مع أي رسالة قديمة بلا action

            String requesterDeviceId = obj.optString("requesterDeviceId", null);
            String requesterDeviceName = obj.optString("requesterDeviceName", "جهاز PulseNet");
            if (requesterDeviceId != null && !requesterDeviceId.isEmpty()) {
                dataProvider.onDeviceRequestReceived(requesterDeviceId, requesterDeviceName, macAddress);
            }

            String responseJson;
            switch (action) {
                case "summary_page": {
                    int cursor = obj.optInt("cursor", 0);
                    responseJson = dataProvider.getSummaryPageJson(cursor);
                    break;
                }
                case "items_page": {
                    org.json.JSONArray idsArray = obj.optJSONArray("requestedIds");
                    List<String> ids = new java.util.ArrayList<>();
                    if (idsArray != null) {
                        for (int i = 0; i < idsArray.length(); i++) {
                            ids.add(idsArray.getString(i));
                        }
                    }
                    responseJson = dataProvider.getItemsForIds(ids);
                    break;
                }
                default: // "identity_only" أو أي قيمة غير متوقعة - بس تسجيل هوية بدون رد فعلي
                    responseJson = "{\"items\":[]}";
            }

            devicePendingResponses.put(macAddress, responseJson);
            Log.i(TAG, "جهّزنا رد (" + action + ") للجهاز: " + macAddress);
        } catch (org.json.JSONException e) {
            Log.e(TAG, "خطأ بقراءة الطلب المكتمل: " + e.getMessage());
            devicePendingResponses.put(macAddress, "{}");
        }
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        if (gattServer != null) {
            try {
                gattServer.close();
            } catch (Exception ignored) {}
            gattServer = null;
        }
        connectedDevicesMap.clear();
        devicePendingResponses.clear();
        deviceMtuMap.clear();
        writeBufferMap.clear();
    }
}