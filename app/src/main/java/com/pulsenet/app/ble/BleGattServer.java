package com.pulsenet.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * "السيرفر" المحلي - هلق بيدعم بروتوكول "ملخص → طلب → رد" الكامل:
 * 1) يعرض ملخص خفيف (id+version+hash) عبر SUMMARY_CHARACTERISTIC
 * 2) يستقبل طلب "بدي هاي الـ id المحددة" عبر REQUEST_CHARACTERISTIC (كتابة)
 * 3) يحضّر ويرجع التفاصيل الكاملة بس للمطلوب عبر ITEMS_CHARACTERISTIC
 */
public class BleGattServer {

    private static final String TAG = "BleGattServer";

    public interface DataProvider {
        /** يرجع ملخص خفيف (id+version+hash) لكل معلوماتنا */
        String getSummaryJson();

        /** يرجع حزمة كاملة (بالتفاصيل) بس للمعلومات يلي ضمن اللائحة المطلوبة */
        String getItemsForIds(List<String> ids);
    }

    private final Context context;
    private final DataProvider dataProvider;
    private BluetoothGattServer gattServer;

    // آخر رد جهّزناه بعد طلب "الناقص فقط" - بيُقرأ لاحقاً عبر ITEMS_CHARACTERISTIC
    private volatile String pendingItemsResponse = "{}";

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
        Log.i(TAG, "GATT Server بدأ (ملخص + طلب + رد)");
    }

    private final BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            String json;
            if (BleConstants.SUMMARY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                json = dataProvider.getSummaryJson();
            } else {
                json = pendingItemsResponse; // آخر رد جهّزناه بعد طلب سابق
            }

            byte[] fullData = json.getBytes(StandardCharsets.UTF_8);
            byte[] chunk;
            if (offset > fullData.length) {
                chunk = new byte[0];
            } else {
                int length = Math.min(fullData.length - offset, 512);
                chunk = new byte[length];
                System.arraycopy(fullData, offset, chunk, 0, length);
            }

            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk);
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            if (BleConstants.REQUEST_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                try {
                    String requestJson = new String(value, StandardCharsets.UTF_8);
                    org.json.JSONObject obj = new org.json.JSONObject(requestJson);
                    org.json.JSONArray idsArray = obj.getJSONArray("requestedIds");

                    java.util.List<String> ids = new java.util.ArrayList<>();
                    for (int i = 0; i < idsArray.length(); i++) {
                        ids.add(idsArray.getString(i));
                    }

                    // نجهّز الرد فوراً - رح يُقرأ بالخطوة الجاية عبر ITEMS_CHARACTERISTIC
                    pendingItemsResponse = dataProvider.getItemsForIds(ids);
                    Log.i(TAG, "استقبلنا طلب لـ " + ids.size() + " معلومة، وجهزنا الرد");
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "خطأ بقراءة الطلب: " + e.getMessage());
                    pendingItemsResponse = "{}";
                }
            }

            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void stop() {
        if (gattServer != null) {
            gattServer.close();
        }
    }
}