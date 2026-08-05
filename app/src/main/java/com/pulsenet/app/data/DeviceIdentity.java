package com.pulsenet.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * هوية ثابتة لهذا الجهاز - تتولّد مرة وحدة بس أول ما يثبت التطبيق،
 * وتضل نفسها دايماً (بعكس عنوان البلوتوث العشوائي يلي بيتغير كل جلسة).
 */
public class DeviceIdentity {

    private static final String PREFS_NAME = "pulsenet_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DEVICE_NAME = "device_name";

    /** يرجع رقم تعريف فريد وثابت لهذا الجهاز (يتولّد أول مرة، وبعدها بضل نفسه للأبد) */
    public static String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    /** يرجع اسم ثابت لهذا الجهاز (مشتق من رقم التعريف، أو اسم اختاره المستخدم لاحقاً) */
    public static String getDeviceName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(KEY_DEVICE_NAME, null);
        if (name == null) {
            String id = getDeviceId(context);
            name = "PulseNet-" + id.substring(0, 4).toUpperCase();
            prefs.edit().putString(KEY_DEVICE_NAME, name).apply();
        }
        return name;
    }

    public static void setDeviceName(Context context, String newName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DEVICE_NAME, newName).apply();
    }
}
