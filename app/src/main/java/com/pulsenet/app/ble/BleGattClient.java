package com.pulsenet.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import com.pulsenet.app.data.DatabaseHelper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * "العميل" - بيطبّق بروتوكول "ملخص → مقارنة → طلب الناقص فقط" الكامل:
 * 1) يقرأ الملخص الخفيف (id+version+hash) من الجهاز التاني
 * 2) يقارنه محلياً مع قاعدة بياناتنا (findMissingItemIds)
 * 3) يكتب طلب فيه بس الـ id الناقصة
 * 4) يقرأ الرد الكامل (بالتفاصيل) بس للمطلوب
 */
public class BleGattClient {

    private static final String TAG = "BleGattClient";

    public interface ItemsReceivedListener {
        void onItemsReceived(String json);
        void onConnectionFailed();
    }

    private final Context context;
    private final DatabaseHelper databaseHelper;
    private BluetoothGatt bluetoothGatt;
    private ItemsReceivedListener listener;

    public BleGattClient(Context context) {
        this.context = context.getApplicationContext();
        this.databaseHelper = new DatabaseHelper(this.context);
    }

    @SuppressLint("MissingPermission")
    public void connectAndFetchItems(BluetoothDevice device, ItemsReceivedListener listener) {
        this.listener = listener;
        Log.i(TAG, "عم نبدأ اتصال بـ " + device.getAddress());
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "اتصلنا - عم نطلب حجم رسالة أكبر");
                boolean mtuRequested = gatt.requestMtu(512);
                if (!mtuRequested) gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(TAG, "onMtuChanged - mtu=" + mtu);
            gatt.discoverServices();
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                notifyFailed();
                return;
            }

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                notifyFailed();
                return;
            }

            BluetoothGattCharacteristic summaryCharacteristic =
                    service.getCharacteristic(BleConstants.SUMMARY_CHARACTERISTIC_UUID);
            if (summaryCharacteristic == null) {
                notifyFailed();
                return;
            }

            Log.i(TAG, "عم نقرأ الملخص الخفيف...");
            gatt.readCharacteristic(summaryCharacteristic);
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         byte[] value, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                notifyFailed();
                return;
            }

            String json = new String(value, StandardCharsets.UTF_8);

            if (BleConstants.SUMMARY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                handleSummaryReceived(gatt, json);
            } else {
                Log.i(TAG, "وصلنا الرد الكامل، طوله: " + json.length());
                if (listener != null) listener.onItemsReceived(json);
                gatt.disconnect();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (!BleConstants.REQUEST_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) return;

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "فشل إرسال الطلب");
                notifyFailed();
                return;
            }

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            BluetoothGattCharacteristic itemsCharacteristic =
                    service.getCharacteristic(BleConstants.ITEMS_CHARACTERISTIC_UUID);

            Log.i(TAG, "الطلب انبعت بنجاح، عم نقرأ الرد الكامل...");
            gatt.readCharacteristic(itemsCharacteristic);
        }
    };

    @SuppressLint("MissingPermission")
    private void handleSummaryReceived(BluetoothGatt gatt, String summaryJson) {
        String remoteDeviceId = null;
        String remoteDeviceName = "جهاز PulseNet";
        try {
            org.json.JSONObject summaryEnvelope = new org.json.JSONObject(summaryJson);
            remoteDeviceId = summaryEnvelope.optString("senderDeviceId", null);
            remoteDeviceName = summaryEnvelope.optString("senderDeviceName", remoteDeviceName);
        } catch (org.json.JSONException ignored) {
        }

        List<String> missingIds = databaseHelper.findMissingItemIds(summaryJson);
        Log.i(TAG, "الملخص وصل من " + remoteDeviceName + " - عنا نقص بـ " + missingIds.size() + " معلومة");

        if (missingIds.isEmpty()) {
            try {
                org.json.JSONObject emptyEnvelope = new org.json.JSONObject();
                emptyEnvelope.put("senderDeviceId", remoteDeviceId);
                emptyEnvelope.put("senderDeviceName", remoteDeviceName);
                emptyEnvelope.put("items", new org.json.JSONArray());
                if (listener != null) listener.onItemsReceived(emptyEnvelope.toString());
            } catch (org.json.JSONException e) {
                if (listener != null) listener.onItemsReceived("{\"items\":[]}");
            }
            gatt.disconnect();
            return;
        }

        try {
            org.json.JSONObject requestObj = new org.json.JSONObject();
            org.json.JSONArray idsArray = new org.json.JSONArray();
            for (String id : missingIds) idsArray.put(id);
            requestObj.put("requestedIds", idsArray);

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            BluetoothGattCharacteristic requestCharacteristic =
                    service.getCharacteristic(BleConstants.REQUEST_CHARACTERISTIC_UUID);

            byte[] requestBytes = requestObj.toString().getBytes(StandardCharsets.UTF_8);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(requestCharacteristic, requestBytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                requestCharacteristic.setValue(requestBytes);
                requestCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                gatt.writeCharacteristic(requestCharacteristic);
            }

            Log.i(TAG, "عم نبعت طلب الناقص...");
        } catch (org.json.JSONException e) {
            Log.e(TAG, "خطأ ببناء الطلب: " + e.getMessage());
            notifyFailed();
        }
    }

    private void notifyFailed() {
        if (listener != null) listener.onConnectionFailed();
        if (bluetoothGatt != null) disconnect();
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}