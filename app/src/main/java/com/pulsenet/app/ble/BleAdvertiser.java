package com.pulsenet.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

public class BleAdvertiser {

    private static final String TAG = "BleAdvertiser";

    public interface AdvertiseStateListener {
        void onAdvertiseStarted();
        void onAdvertiseFailed(int errorCode);
    }

    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bleAdvertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isAdvertising = false;

    public BleAdvertiser(Context context, BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public boolean isAdvertising() {
        return isAdvertising;
    }

    @SuppressLint("MissingPermission")
    public void startAdvertising(AdvertiseStateListener listener) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth غير مفعّل - ما بقدر أبلش البث");
            if (listener != null) listener.onAdvertiseFailed(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR);
            return;
        }

        bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (bleAdvertiser == null) {
            Log.e(TAG, "هذا الجهاز ما بيدعم البث عبر BLE");
            if (listener != null) listener.onAdvertiseFailed(AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED);
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                isAdvertising = true;
                Log.i(TAG, "البث بدأ بنجاح");
                if (listener != null) listener.onAdvertiseStarted();
            }

            @Override
            public void onStartFailure(int errorCode) {
                isAdvertising = false;
                Log.e(TAG, "فشل البث، كود الخطأ: " + errorCode);
                if (listener != null) listener.onAdvertiseFailed(errorCode);
            }
        };

        bleAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (bleAdvertiser != null && advertiseCallback != null) {
            bleAdvertiser.stopAdvertising(advertiseCallback);
        }
        isAdvertising = false;
    }
}
