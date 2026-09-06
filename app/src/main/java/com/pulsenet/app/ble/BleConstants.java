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

    // ================= حدود حجم الرسائل عبر BLE (مهم جداً!) =================
    // مواصفات بروتوكول BLE (ATT) بتحدد حد أقصى مطلق لأي "قيمة خاصية" واحدة
    // (Characteristic Value) بـ 512 بايت. أي محاولة نرسل فيها JSON أكبر من
    // هيك بعملية قراءة/كتابة وحدة، بينقص/يتقطع الكلام بصمت (بدون أي Exception
    // واضح)، وهذا كان سبب مشاكل "ضياع/تسرب" معلومات لما تراكم عدد المعلومات
    // أو طال نص أي معلومة. لهيك، ما عاد نرسل "كل شي دفعة وحدة": صرنا نتبادل
    // الملخص على شكل صفحات صغيرة (Pagination)، ونطلب المعلومات الناقصة وحدة
    // وحدة، مع هامش أمان تحت الـ 512 بايت.
    public static final int BLE_ATTRIBUTE_HARD_LIMIT_BYTES = 512;
    public static final int SAFE_RESPONSE_BYTES = 460; // هامش أمان تحت الحد الأقصى
    public static final int SUMMARY_PAGE_SIZE = 6; // عدد أقصى تقريبي للمدخلات بكل صفحة ملخص
    public static final int ITEMS_BATCH_SIZE = 1;  // نطلب معلومة كاملة وحدة بكل مرة (أضمن حل)

    private BleConstants() {
        // كلاس أدوات فقط - ما بننشئ منه كائنات
    }
}