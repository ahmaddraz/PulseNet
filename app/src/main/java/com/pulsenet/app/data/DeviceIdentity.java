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
    private static final String KEY_NAME_IS_CUSTOM = "device_name_is_custom";

    // حد أقصى لطول الاسم - حتى يضل صغير بما فيه الكفاية إنه ينضم لأي رسالة
    // BLE بدون ما يقرّبها من حد الـ 512 بايت (خصوصاً إذا كان الاسم عربي،
    // لأنه كل حرف عربي بياخد بايتين بترميز UTF-8).
    public static final int MAX_NAME_LENGTH = 20;

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

    /**
     * يحفظ اسم يختاره المستخدم بنفسه (بدل الاسم العشوائي التلقائي)، متل
     * فكرة تطبيق Bitchat: كل جهاز عنده "يوزر" واحد يعرّف فيه حاله للأجهزة
     * التانية. بيرجع true إذا انحفظ فعلياً، false إذا كان الاسم فاضي.
     *
     * ملاحظة مهمة: هاد الاسم محفوظ بـ SharedPreferences يلي بينمسح لو
     * التطبيق انحذف وانثبّت من جديد (زي أي بيانات تانية للتطبيق) - هاي
     * حدود نظام أندرويد نفسه، مش شي نقدر نتفاداه من داخل التطبيق بدون
     * حساب مستخدم على سيرفر خارجي. اللي بنضمنه هون إنه المستخدم يقدر
     * يختار نفس اسمه المعروف بأي وقت (بدل ما ينولد له اسم عشوائي جديد
     * ما بيقدر يتحكم فيه)، فحتى لو أعاد التثبيت، بيقدر يكتب "أحمد" من
     * جديد ويرجع معروف لأصحابه.
     */
    public static boolean setDeviceName(Context context, String newName) {
        if (newName == null) return false;
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_DEVICE_NAME, trimmed)
                .putBoolean(KEY_NAME_IS_CUSTOM, true)
                .apply();
        return true;
    }

    /** هل المستخدم اختار اسمه بنفسه، ولا لسا شغال بالاسم التلقائي (PulseNet-XXXX)؟ */
    public static boolean hasCustomName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NAME_IS_CUSTOM, false);
    }
}
