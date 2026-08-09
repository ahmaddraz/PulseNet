package com.pulsenet.app.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.pulsenet.app.ble.BleAdvertiser;
import com.pulsenet.app.ble.BleGattClient;
import com.pulsenet.app.ble.BleGattServer;
import com.pulsenet.app.ble.BleScanner;
import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.DeviceIdentity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // تتبع الأجهزة التي يجري الاتصال بها حالياً بناءً على الـ MAC Address لتفادي الحظر الشامل
    private final Set<String> activeSyncingMacs = Collections.synchronizedSet(new HashSet<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean bleOperationsStarted = false;

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

        bleGattServer = new BleGattServer(this, new BleGattServer.DataProvider() {
            @Override
            public String getSummaryJson() {
                return databaseHelper.getItemsSummaryAsJson(myDeviceId, myDeviceName);
            }

            @Override
            public String getItemsForIds(List<String> ids) {
                return databaseHelper.getItemsByIdsAsJson(ids, myDeviceId, myDeviceName);
            }

            @Override
            public void onDeviceRequestReceived(String requesterDeviceId, String requesterDeviceName, String mac) {
                long now = System.currentTimeMillis();
                databaseHelper.upsertDevice(requesterDeviceId, mac, requesterDeviceName, 0, now);
                Log.i(TAG, "سجّلنا الجهاز الطالب من جهتنا: " + requesterDeviceName);
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
        if (bleOperationsStarted) {
            Log.i(TAG, "البلوتوث شغال أصلاً - ما محتاجين نبلشه من جديد");
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "البلوتوث مطفي أو مش موجود");
            return;
        }

        bleOperationsStarted = true;
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

        startScanningInternal();
    }

    private void startScanningInternal() {
        if (bleScanner == null) return;

        bleScanner.startScanning(new BleScanner.ScanResultListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                String mac = device.getAddress();

                // لو الجهاز عم نتحادث معه حالياً لا نفتح معه اتصال تاني
                if (activeSyncingMacs.contains(mac)) return;

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
        final String mac = device.getAddress();
        activeSyncingMacs.add(mac);

        long now = System.currentTimeMillis();
        long syncStartTime = System.currentTimeMillis();

        BleGattClient client = new BleGattClient(this, myDeviceId, myDeviceName);

        // مهلة أمان: 12 ثانية لكل جهاز بشكل مستقل
        final Runnable timeoutRunnable = () -> {
            if (activeSyncingMacs.contains(mac)) {
                Log.e(TAG, "انتهت مهلة الاتصال للجهاز (" + mac + ") - عم نفك القفل يدوياً");
                client.disconnect();
                activeSyncingMacs.remove(mac);
            }
        };
        mainHandler.postDelayed(timeoutRunnable, 12_000L);

        client.connectAndFetchItems(device, new BleGattClient.ItemsReceivedListener() {
            @Override
            public void onItemsReceived(String json) {
                mainHandler.removeCallbacks(timeoutRunnable);
                activeSyncingMacs.remove(mac);

                String senderId = null;
                String senderName = "جهاز PulseNet";

                try {
                    org.json.JSONObject envelope = new org.json.JSONObject(json);
                    senderId = envelope.optString("senderDeviceId", null);
                    senderName = envelope.optString("senderDeviceName", senderName);
                } catch (org.json.JSONException ignored) {
                }

                if (senderId == null || senderId.isEmpty()) {
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
            }

            @Override
            public void onConnectionFailed() {
                mainHandler.removeCallbacks(timeoutRunnable);
                activeSyncingMacs.remove(mac);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        bleOperationsStarted = false;
        activeSyncingMacs.clear();
        mainHandler.removeCallbacksAndMessages(null);

        if (bleAdvertiser != null) bleAdvertiser.stopAdvertising();
        if (bleScanner != null) bleScanner.stopScanning();
        if (bleGattServer != null) bleGattServer.stop();
    }
}