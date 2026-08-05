package com.pulsenet.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BleScanner {

    private static final String TAG = "BleScanner";

    public interface ScanResultListener {
        void onDeviceFound(BluetoothDevice device, int rssi);
        void onScanFailed(int errorCode);
    }

    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;
    private ScanResultListener listener;
    private boolean isRunning = false;

    public BleScanner(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @SuppressLint("MissingPermission")
    public void startScanning(ScanResultListener listener) {
        this.listener = listener;

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth غير مفعّل - ما بقدر أبلش الاكتشاف");
            if (listener != null) listener.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Log.e(TAG, "هذا الجهاز ما بيدعم الاكتشاف عبر BLE");
            if (listener != null) listener.onScanFailed(ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED);
            return;
        }

        isRunning = true;
        runScanCycle();
    }

    @SuppressLint("MissingPermission")
    private void runScanCycle() {
        if (!isRunning) return;

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .build());

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (listener != null) {
                    listener.onDeviceFound(result.getDevice(), result.getRssi());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "فشل الاكتشاف، كود الخطأ: " + errorCode);
                if (listener != null) listener.onScanFailed(errorCode);
            }
        };

        bleScanner.startScan(filters, settings, scanCallback);
        Log.i(TAG, "بدأت دورة اكتشاف جديدة");

        handler.postDelayed(() -> {
            stopCurrentScan();
            if (isRunning) {
                handler.postDelayed(this::runScanCycle, BleConstants.SCAN_REST_MS);
            }
        }, BleConstants.SCAN_PERIOD_MS);
    }

    @SuppressLint("MissingPermission")
    private void stopCurrentScan() {
        if (bleScanner != null && scanCallback != null) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (IllegalStateException e) {
                Log.w(TAG, "تعذر إيقاف الاكتشاف: " + e.getMessage());
            }
        }
    }

    public void stopScanning() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        stopCurrentScan();
    }
}
