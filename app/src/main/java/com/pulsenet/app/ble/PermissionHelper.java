package com.pulsenet.app.ble;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * صلاحيات البلوتوث اختلفت بين نسخ أندرويد:
 * - قبل أندرويد 12: لازم صلاحية الموقع (ACCESS_FINE_LOCATION) عشان تسوي بحث BLE.
 * - أندرويد 12 وما فوق: صلاحيات مخصصة (BLUETOOTH_SCAN / ADVERTISE / CONNECT).
 */
public class PermissionHelper {

    public static String[] getRequiredBlePermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // أندرويد 12 = API 31
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // أندرويد 13 = API 33
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return permissions.toArray(new String[0]);
    }


    public static boolean hasAllBlePermissions(Context context) {
        for (String permission : getRequiredBlePermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
