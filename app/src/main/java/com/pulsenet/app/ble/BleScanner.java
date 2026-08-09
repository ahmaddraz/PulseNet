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
    private ScanResultListener listener;
    private volatile boolean isRunning = false;
    private volatile boolean isScanningActive = false;

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

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "توقف الاكتشاف الدوري - البلوتوث غير مفعّل");
            stopScanning();
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) return;

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .build());

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED) // نمط متوازن لحفظ البطارية ومنع الحظر
                .build();

        try {
            stopCurrentScan();
            bleScanner.startScan(filters, settings, internalScanCallback);
            isScanningActive = true;
            Log.i(TAG, "بدأت دورة اكتشاف جديدة");
        } catch (Exception e) {
            Log.e(TAG, "فشل بدء الاكتشاف: " + e.getMessage());
            if (listener != null) listener.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
        }

        // جدولة إيقاف المسح الحالي والبدء بفترة الراحة
        handler.postDelayed(() -> {
            stopCurrentScan();
            if (isRunning) {
                handler.postDelayed(this::runScanCycle, BleConstants.SCAN_REST_MS);
            }
        }, BleConstants.SCAN_PERIOD_MS);
    }

    // مرجع موحد ثابت للـ Callback لمنع تسريب الذاكرة والحظر
    private final ScanCallback internalScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (listener != null && result != null && result.getDevice() != null) {
                listener.onDeviceFound(result.getDevice(), result.getRssi());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (listener != null && results != null) {
                for (ScanResult result : results) {
                    if (result != null && result.getDevice() != null) {
                        listener.onDeviceFound(result.getDevice(), result.getRssi());
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "فشل الاكتشاف، كود الخطأ: " + errorCode);
            isScanningActive = false;
            if (listener != null) listener.onScanFailed(errorCode);
        }
    };

    @SuppressLint("MissingPermission")
    private void stopCurrentScan() {
        if (isScanningActive && bleScanner != null) {
            try {
                bleScanner.stopScan(internalScanCallback);
            } catch (Exception e) {
                Log.w(TAG, "تعذر إيقاف الاكتشاف: " + e.getMessage());
            } finally {
                isScanningActive = false;
            }
        }
    }

    public void stopScanning() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        stopCurrentScan();
    }
}