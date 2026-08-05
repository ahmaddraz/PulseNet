package com.pulsenet.app.ble;

import java.util.UUID;

/**
 * ثوابت خاصة بشبكة PulseNet عبر البلوتوث.
 * كل نسخة تطبيق لازم تستخدم نفس الـ UUID بالضبط، حتى الأجهزة تتعرف على بعضها.
 */
public class BleConstants {

    public static final UUID SERVICE_UUID =
            UUID.fromString("8e3aa1d0-8f2b-4c5e-9a1a-6d4b2e7c9f10");

    public static final String DEVICE_NAME_PREFIX = "PulseNet-";

    // رقم تعريف "الخاصية" (Characteristic) يلي فيها المعلومات المتبادلة بين الأجهزة
    public static final UUID ITEMS_CHARACTERISTIC_UUID =
            UUID.fromString("8e3aa1d1-8f2b-4c5e-9a1a-6d4b2e7c9f10");

    // خاصية "الملخص" - id + version + hash فقط لكل معلومة، بدون التفاصيل الكاملة
    public static final UUID SUMMARY_CHARACTERISTIC_UUID =
            UUID.fromString("8e3aa1d2-8f2b-4c5e-9a1a-6d4b2e7c9f10");

    // خاصية "الطلب" - الجهاز يكتب فيها لائحة الـ id يلي ناقصاه، والسيرفر يحضّرها
    public static final UUID REQUEST_CHARACTERISTIC_UUID =
            UUID.fromString("8e3aa1d3-8f2b-4c5e-9a1a-6d4b2e7c9f10");

    // مدة كل دورة بحث (بالميلي ثانية) - بعدها منوقف شوي لتوفير البطارية
    public static final long SCAN_PERIOD_MS = 15_000L;
    public static final long SCAN_REST_MS = 5_000L;

    private BleConstants() {
        // كلاس أدوات فقط - ما بننشئ منه كائنات
    }
}