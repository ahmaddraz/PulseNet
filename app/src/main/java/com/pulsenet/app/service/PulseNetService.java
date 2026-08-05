package com.pulsenet.app.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.pulsenet.app.ble.BleAdvertiser;
import com.pulsenet.app.ble.BleGattClient;
import com.pulsenet.app.ble.BleGattServer;
import com.pulsenet.app.ble.BleScanner;
import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.DeviceIdentity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PulseNetService extends Service {

    private static final String TAG = "PulseNetService";

    public static final String ACTION_STOP = "com.pulsenet.app.STOP_SERVICE";

    private BluetoothAdapter bluetoothAdapter;
    private BleAdvertiser bleAdvertiser;
    private BleScanner bleScanner;
    private BleGattServer bleGattServer;
    private DatabaseHelper databaseHelper;
    private NotificationHelper notificationHelper;

    private String myDeviceId;
    private String myDeviceName;

    private final Map<String, Long> lastSyncTimeByDevice = new HashMap<>();
    private static final long RESYNC_INTERVAL_MS = 20_000L;

    private final Map<String, Long> lastRangeNotifyTimeByDevice = new HashMap<>();
    private static final long RANGE_NOTIFY_COOLDOWN_MS = 5 * 60 * 1000L;

    private volatile boolean isSyncInProgress = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate - الخدمة عم تتنشئ");

        notificationHelper = new NotificationHelper(this);
        databaseHelper = new DatabaseHelper(this);

        myDeviceId = DeviceIdentity.getDeviceId(this);
        myDeviceName = DeviceIdentity.getDeviceName(this);
        Log.i(TAG, "هويتنا: id=" + myDeviceId + " name=" + myDeviceName);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        bleAdvertiser = new BleAdvertiser(this, bluetoothAdapter);
        bleScanner = new BleScanner(bluetoothAdapter);

        // السيرفر هلق بيدعم بروتوكول الملخص/الطلب بدل إرسال كل شي دفعة وحدة
        bleGattServer = new BleGattServer(this, new BleGattServer.DataProvider() {
            @Override
            public String getSummaryJson() {
                return databaseHelper.getItemsSummaryAsJson();
            }

            @Override
            public String getItemsForIds(List<String> ids) {
                return databaseHelper.getItemsByIdsAsJson(ids, myDeviceId, myDeviceName);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        NotificationCompat.Builder notification = notificationHelper.buildServiceNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification.build(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification.build());
        }

        startBleOperations();

        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void startBleOperations() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "البلوتوث مطفي أو مش موجود");
            return;
        }

        Log.i(TAG, "عم نبلش السيرفر والبث والاكتشاف");
        bleGattServer.start();

        bleAdvertiser.startAdvertising(new BleAdvertiser.AdvertiseStateListener() {
            @Override
            public void onAdvertiseStarted() {
                Log.i(TAG, "البث بدأ بنجاح");
            }

            @Override
            public void onAdvertiseFailed(int errorCode) {
                Log.e(TAG, "فشل البث، كود: " + errorCode);
            }
        });

        bleScanner.startScanning(new BleScanner.ScanResultListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                String mac = device.getAddress();

                if (isSyncInProgress) return;

                long now = System.currentTimeMillis();
                Long lastSync = lastSyncTimeByDevice.get(mac);

                if (lastSync == null || (now - lastSync) > RESYNC_INTERVAL_MS) {
                    lastSyncTimeByDevice.put(mac, now);
                    connectAndSyncItems(device, rssi);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "فشل الاكتشاف، كود: " + errorCode);
            }
        });
    }

    /** يتصل بجهاز مكتشف عبر بروتوكول الملخص/الطلب، ويسجّل إحصائية التزامن الحقيقية */
    private void connectAndSyncItems(BluetoothDevice device, int rssi) {
        isSyncInProgress = true;
        String mac = device.getAddress();
        long now = System.currentTimeMillis();
        long syncStartTime = System.currentTimeMillis();

        BleGattClient client = new BleGattClient(this);
        client.connectAndFetchItems(device, new BleGattClient.ItemsReceivedListener() {
            @Override
            public void onItemsReceived(String json) {
                String senderId = null;
                String senderName = "جهاز PulseNet";

                try {
                    org.json.JSONObject envelope = new org.json.JSONObject(json);
                    senderId = envelope.optString("senderDeviceId", null);
                    senderName = envelope.optString("senderDeviceName", senderName);
                } catch (org.json.JSONException ignored) {
                }

                if (senderId == null || senderId.isEmpty()) {
                    // ما في شي ناقصنا فعلياً (رد فاضي بلا هوية) - برضه حدث ناجح، بس بلا استيراد
                    isSyncInProgress = false;
                    return;
                }

                databaseHelper.upsertDevice(senderId, mac, senderName, rssi, now);

                Long lastRangeNotify = lastRangeNotifyTimeByDevice.get(senderId);
                if (lastRangeNotify == null || (now - lastRangeNotify) > RANGE_NOTIFY_COOLDOWN_MS) {
                    lastRangeNotifyTimeByDevice.put(senderId, now);
                    notificationHelper.showDeviceInRangeNotification(senderName);
                }

                int[] result = databaseHelper.importRequestedItemsFromJson(json, senderId);
                int importedCount = result[0];
                int duplicateCount = result[1];

                long syncDuration = System.currentTimeMillis() - syncStartTime;
                databaseHelper.logSyncEvent(senderId, 0, importedCount, duplicateCount, syncDuration);
                databaseHelper.updateLastSyncAt(senderId, now);

                if (importedCount > 0) {
                    notificationHelper.showNewItemsNotification(importedCount);
                }

                isSyncInProgress = false;
            }

            @Override
            public void onConnectionFailed() {
                isSyncInProgress = false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        if (bleAdvertiser != null) bleAdvertiser.stopAdvertising();
        if (bleScanner != null) bleScanner.stopScanning();
        if (bleGattServer != null) bleGattServer.stop();
    }
}