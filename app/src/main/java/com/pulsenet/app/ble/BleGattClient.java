package com.pulsenet.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pulsenet.app.data.DatabaseHelper;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class BleGattClient {

    private static final String TAG = "BleGattClient";

    public interface ItemsReceivedListener {
        void onItemsReceived(String json);
        void onConnectionFailed();
    }

    private final Context context;
    private final DatabaseHelper databaseHelper;
    private final String myDeviceId;
    private final String myDeviceName;
    private BluetoothGatt bluetoothGatt;
    private ItemsReceivedListener listener;

    private volatile boolean isServicesDiscovered = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable mtuTimeoutRunnable;

    public BleGattClient(Context context, String myDeviceId, String myDeviceName) {
        this.context = context.getApplicationContext();
        this.databaseHelper = new DatabaseHelper(this.context);
        this.myDeviceId = myDeviceId;
        this.myDeviceName = myDeviceName;
    }

    @SuppressLint("MissingPermission")
    public void connectAndFetchItems(BluetoothDevice device, ItemsReceivedListener listener) {
        this.listener = listener;
        this.isServicesDiscovered = false;
        Log.i(TAG, "عم نبدأ اتصال بـ " + device.getAddress());
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "اتصلنا - عم نطلب حجم رسالة أكبر (MTU)");

                // صمام أمان في حال عدم استجابة الجهاز لـ MTU خلال 1.5 ثانية
                mtuTimeoutRunnable = () -> {
                    if (!isServicesDiscovered && bluetoothGatt != null) {
                        Log.w(TAG, "تأخر الـ MTU، جاري اكتشاف الخدمات تلقائياً...");
                        isServicesDiscovered = true;
                        gatt.discoverServices();
                    }
                };
                mainHandler.postDelayed(mtuTimeoutRunnable, 1500);

                if (!gatt.requestMtu(512)) {
                    // إذا فشل طلب الـ MTU فوراً، اكتشف الخدمات مباشرة
                    mainHandler.removeCallbacks(mtuTimeoutRunnable);
                    isServicesDiscovered = true;
                    gatt.discoverServices();
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "انقطع الاتصال بالجهاز");
                closeGattSilently();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(TAG, "onMtuChanged - mtu=" + mtu);
            if (mtuTimeoutRunnable != null) mainHandler.removeCallbacks(mtuTimeoutRunnable);

            if (!isServicesDiscovered) {
                isServicesDiscovered = true;
                gatt.discoverServices();
            }
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

        // --- دعم أجهزة Android 12 وما دون (API < 33) ---
        @Override
        @Deprecated
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                byte[] value = characteristic.getValue();
                processCharacteristicRead(gatt, characteristic, value, status);
            }
        }

        // --- دعم أجهزة Android 13 وما فوق (API >= 33) ---
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                processCharacteristicRead(gatt, characteristic, value, status);
            }
        }

        @SuppressLint("MissingPermission")
        private void processCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS || value == null) {
                notifyFailed();
                return;
            }

            String json = new String(value, StandardCharsets.UTF_8);

            if (BleConstants.SUMMARY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                handleSummaryReceived(gatt, json);
            } else {
                Log.i(TAG, "وصلنا الرد الكامل، طوله: " + json.length());
                if (listener != null) listener.onItemsReceived(json);
                disconnect();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (!BleConstants.REQUEST_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) return;

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "فشل إرسال الطلب");
                notifyFailed();
                return;
            }

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                notifyFailed();
                return;
            }

            BluetoothGattCharacteristic itemsCharacteristic =
                    service.getCharacteristic(BleConstants.ITEMS_CHARACTERISTIC_UUID);

            Log.i(TAG, "الطلب انبعت بنجاح، عم نقرأ الرد الكامل...");
            gatt.readCharacteristic(itemsCharacteristic);
        }
    };

    @SuppressLint("MissingPermission")
    private void handleSummaryReceived(BluetoothGatt gatt, String summaryJson) {
        Log.i(TAG, "محتوى الملخص الخام: " + summaryJson);
        List<String> missingIds = databaseHelper.findMissingItemIds(summaryJson);
        Log.i(TAG, "الملخص وصل - عنا نقص بـ " + missingIds.size() + " معلومة");

        // لو ما عنا أي نقص، نرسل طلب الهوية للتسجيل وننهي بعد ثانية (مش فوراً) حتى الكتابة توصل فعلياً
        if (missingIds.isEmpty()) {
            Log.i(TAG, "ما عنا نقص - عم نبعت هويتنا بس، ومنستنى قبل ما نقطع");
            sendRequesterIdentityOnly(gatt);
            if (listener != null) listener.onItemsReceived("{\"items\":[]}");
            mainHandler.postDelayed(this::disconnect, 1000);
            return;
        }

        try {
            org.json.JSONObject requestObj = new org.json.JSONObject();
            org.json.JSONArray idsArray = new org.json.JSONArray();
            for (String id : missingIds) idsArray.put(id);
            requestObj.put("requestedIds", idsArray);
            requestObj.put("requesterDeviceId", myDeviceId);
            requestObj.put("requesterDeviceName", myDeviceName);

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                notifyFailed();
                return;
            }

            BluetoothGattCharacteristic requestCharacteristic =
                    service.getCharacteristic(BleConstants.REQUEST_CHARACTERISTIC_UUID);

            byte[] requestBytes = requestObj.toString().getBytes(StandardCharsets.UTF_8);

            writeCharacteristicData(gatt, requestCharacteristic, requestBytes);
            Log.i(TAG, "عم نبعت طلبنا (وهويتنا) - عدد الناقص: " + missingIds.size());

        } catch (org.json.JSONException e) {
            Log.e(TAG, "خطأ ببناء الطلب: " + e.getMessage());
            notifyFailed();
        }
    }

    @SuppressLint("MissingPermission")
    private void sendRequesterIdentityOnly(BluetoothGatt gatt) {
        try {
            org.json.JSONObject requestObj = new org.json.JSONObject();
            requestObj.put("requestedIds", new org.json.JSONArray());
            requestObj.put("requesterDeviceId", myDeviceId);
            requestObj.put("requesterDeviceName", myDeviceName);

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic requestCharacteristic =
                        service.getCharacteristic(BleConstants.REQUEST_CHARACTERISTIC_UUID);
                if (requestCharacteristic != null) {
                    writeCharacteristicData(gatt, requestCharacteristic, requestObj.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressLint("MissingPermission")
    private void writeCharacteristicData(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            characteristic.setValue(data);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            gatt.writeCharacteristic(characteristic);
        }
    }

    private void notifyFailed() {
        if (listener != null) listener.onConnectionFailed();
        disconnect();
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
            } catch (Exception ignored) {}
        }
    }

    @SuppressLint("MissingPermission")
    private void closeGattSilently() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (Exception ignored) {}
            bluetoothGatt = null;
        }
    }
}