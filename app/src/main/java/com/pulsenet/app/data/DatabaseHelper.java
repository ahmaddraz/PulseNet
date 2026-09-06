package com.pulsenet.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.pulsenet.app.ble.BleConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * قاعدة بيانات PulseNet - مطابقة لمخطط ER الأكاديمي المعتمد.
 * 4 جداول: devices, items, sync_logs, admins.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "pulsenet.db";
    private static final int DATABASE_VERSION = 4;

    private final Context context;

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE devices (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "mac_address TEXT, " +
                "last_rssi INTEGER DEFAULT 0, " +
                "first_seen_at INTEGER, " +
                "last_seen_at INTEGER NOT NULL, " +
                "last_sync_at INTEGER DEFAULT 0" +
                ");");

        db.execSQL("CREATE TABLE items (" +
                "id TEXT PRIMARY KEY, " +
                "title TEXT NOT NULL, " +
                "body TEXT, " +
                "type TEXT NOT NULL, " +
                "area TEXT, " +
                "priority TEXT, " +
                "payload_hash TEXT, " +
                "version INTEGER DEFAULT 1, " +
                "origin_device_id TEXT, " +
                "origin_admin_id TEXT, " +
                "is_admin_broadcast INTEGER DEFAULT 0, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER, " +
                "expires_at INTEGER, " +
                "hop_count INTEGER DEFAULT 0, " +
                "max_hops INTEGER DEFAULT 5, " +
                "sync_status TEXT DEFAULT 'pending', " +
                "is_read INTEGER DEFAULT 0, " +
                "is_hidden_locally INTEGER DEFAULT 0" +
                ");");

        db.execSQL("CREATE TABLE sync_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "device_id TEXT, " +
                "sent_count INTEGER DEFAULT 0, " +
                "received_count INTEGER DEFAULT 0, " +
                "duplicate_count INTEGER DEFAULT 0, " +
                "sync_time_ms INTEGER DEFAULT 0, " +
                "created_at INTEGER NOT NULL" +
                ");");

        db.execSQL("CREATE TABLE admins (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password_hash TEXT, " +
                "created_at INTEGER, " +
                "last_login_at INTEGER" +
                ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS items");
        db.execSQL("DROP TABLE IF EXISTS devices");
        db.execSQL("DROP TABLE IF EXISTS sync_logs");
        db.execSQL("DROP TABLE IF EXISTS admins");
        onCreate(db);
    }

    // ================= عمليات جدول items =================

    public boolean insertItem(Item item) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = itemToValues(item);
        long result = db.insert("items", null, values);
        return result != -1;
    }

    private ContentValues itemToValues(Item item) {
        ContentValues values = new ContentValues();
        values.put("id", item.getId());
        values.put("title", item.getTitle());
        values.put("body", item.getBody());
        values.put("type", item.getType());
        values.put("area", item.getArea());
        values.put("priority", item.getPriority());
        values.put("payload_hash", item.getPayloadHash());
        values.put("version", item.getVersion());
        values.put("origin_device_id", item.getOriginDeviceId());
        values.put("origin_admin_id", item.getOriginAdminId());
        values.put("is_admin_broadcast", item.isAdminBroadcast() ? 1 : 0);
        values.put("created_at", item.getCreatedAt());
        values.put("updated_at", item.getUpdatedAt());
        values.put("expires_at", item.getExpiresAt());
        values.put("hop_count", item.getHopCount());
        values.put("max_hops", item.getMaxHops());
        values.put("sync_status", item.getSyncStatus());
        values.put("is_read", item.isRead() ? 1 : 0);
        values.put("is_hidden_locally", item.isHiddenLocally() ? 1 : 0);
        return values;
    }

    private Item cursorToItem(Cursor cursor) {
        Item item = new Item();
        item.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
        item.setTitle(cursor.getString(cursor.getColumnIndexOrThrow("title")));
        item.setBody(cursor.getString(cursor.getColumnIndexOrThrow("body")));
        item.setType(cursor.getString(cursor.getColumnIndexOrThrow("type")));
        item.setArea(cursor.getString(cursor.getColumnIndexOrThrow("area")));
        item.setPriority(cursor.getString(cursor.getColumnIndexOrThrow("priority")));
        item.setPayloadHash(cursor.getString(cursor.getColumnIndexOrThrow("payload_hash")));
        item.setVersion(cursor.getInt(cursor.getColumnIndexOrThrow("version")));
        item.setOriginDeviceId(cursor.getString(cursor.getColumnIndexOrThrow("origin_device_id")));
        item.setOriginAdminId(cursor.getString(cursor.getColumnIndexOrThrow("origin_admin_id")));
        item.setAdminBroadcast(cursor.getInt(cursor.getColumnIndexOrThrow("is_admin_broadcast")) == 1);
        item.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
        item.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")));
        int expiresIdx = cursor.getColumnIndexOrThrow("expires_at");
        item.setExpiresAt(cursor.isNull(expiresIdx) ? null : cursor.getLong(expiresIdx));
        item.setHopCount(cursor.getInt(cursor.getColumnIndexOrThrow("hop_count")));
        item.setMaxHops(cursor.getInt(cursor.getColumnIndexOrThrow("max_hops")));
        item.setSyncStatus(cursor.getString(cursor.getColumnIndexOrThrow("sync_status")));
        item.setRead(cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 1);
        item.setHiddenLocally(cursor.getInt(cursor.getColumnIndexOrThrow("is_hidden_locally")) == 1);
        return item;
    }

    /** يرجع كل المعلومات الظاهرة (مش مخفية محلياً، ومش منتهية الصلاحية)، الأحدث أولاً */
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();

        Cursor cursor = db.query("items", null,
                "is_hidden_locally = 0 AND (expires_at IS NULL OR expires_at > ?)",
                new String[]{String.valueOf(now)},
                null, null, "created_at DESC");

        while (cursor.moveToNext()) {
            items.add(cursorToItem(cursor));
        }
        cursor.close();
        return items;
    }

    public Item getItemById(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("items", null, "id = ?", new String[]{id}, null, null, null);
        Item item = null;
        if (cursor.moveToFirst()) {
            item = cursorToItem(cursor);
        }
        cursor.close();
        return item;
    }

    public boolean itemExistsById(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("items", new String[]{"id"}, "id = ?", new String[]{id}, null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public void markItemAsRead(String id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_read", 1);
        db.update("items", values, "id = ?", new String[]{id});
    }

    public void hideItemLocally(String id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_hidden_locally", 1);
        db.update("items", values, "id = ?", new String[]{id});
    }

    // ================= بروتوكول الملخص/المقارنة/الطلب (بنظام صفحات) =================
    //
    // مهم: بروتوكول BLE بيحدد حد أقصى 512 بايت لأي رسالة وحدة. لو حاولنا نبني
    // ملخص كامل لكل معلوماتنا دفعة وحدة (زي ما كان بالنسخة القديمة)، بمجرد ما
    // يتراكم عدد المعلومات، الملخص كان رح يتجاوز الحد المسموح ويوصل مقطوع/تالف
    // للجهاز التاني بصمت. الحل: نبني الملخص على شكل "صفحات" صغيرة، ونقيس
    // حجمها الفعلي بالبايت (UTF-8) قبل ما نرجعها، بدل ما نفترض عدد ثابت
    // للمدخلات بكل صفحة (لأنه أسماء الأجهزة والـ hash ممكن يختلف طولهم).

    /**
     * يبني صفحة وحدة من "ملخص" معلوماتنا (id + version + hash فقط لكل معلومة)
     * تبلش من ترتيب رقم cursor، وبتوقف تلقائياً قبل ما تتجاوز الحجم الآمن
     * لرسالة BLE وحدة. الجهاز الطالب بيكرر الطلب بـ cursor الجديد (nextCursor)
     * لحد ما توصل القيمة -1 (يعني خلصنا كل المعلومات).
     */
    public String getItemsSummaryPageAsJson(int cursor, String senderDeviceId, String senderDeviceName) {
        List<Item> all = getAllItems();
        org.json.JSONArray entries = new org.json.JSONArray();
        int i = cursor;

        try {
            while (i < all.size()) {
                Item item = all.get(i);
                org.json.JSONObject entry = new org.json.JSONObject();
                entry.put("id", item.getId());
                entry.put("version", item.getVersion());
                entry.put("hash", item.getPayloadHash());

                org.json.JSONArray candidate = new org.json.JSONArray();
                for (int j = 0; j < entries.length(); j++) candidate.put(entries.get(j));
                candidate.put(entry);

                String candidateJson = buildSummaryEnvelope(candidate, i + 1, senderDeviceId, senderDeviceName);
                if (utf8ByteLength(candidateJson) > BleConstants.SAFE_RESPONSE_BYTES && entries.length() > 0) {
                    // إضافة هاي المعلومة رح تخلي الصفحة أكبر من اللازم - نوقف هون ونكملها بالصفحة الجاية
                    break;
                }
                entries.put(entry);
                i++;
            }
        } catch (org.json.JSONException e) {
            android.util.Log.e("DatabaseHelper", "خطأ ببناء صفحة الملخص: " + e.getMessage());
        }

        int nextCursor = i < all.size() ? i : -1;
        return buildSummaryEnvelope(entries, nextCursor, senderDeviceId, senderDeviceName);
    }

    private String buildSummaryEnvelope(org.json.JSONArray entries, int nextCursor,
                                         String senderDeviceId, String senderDeviceName) {
        try {
            org.json.JSONObject envelope = new org.json.JSONObject();
            envelope.put("senderDeviceId", senderDeviceId);
            envelope.put("senderDeviceName", senderDeviceName);
            envelope.put("entries", entries);
            envelope.put("nextCursor", nextCursor);
            return envelope.toString();
        } catch (org.json.JSONException e) {
            return "{\"entries\":[],\"nextCursor\":-1}";
        }
    }

    private static int utf8ByteLength(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /** يتحقق هل معلومة معينة (حسب id ورقم النسخة عند الطرف التاني) ناقصة عنا أو نسخة أقدم */
    public boolean isMissingOrStale(String id, int remoteVersion) {
        Item local = getItemById(id);
        return local == null || local.getVersion() < remoteVersion;
    }

    /** يبني حزمة كاملة (بالتفاصيل) بس للمعلومات يلي أرقام هوياتها موجودة باللائحة المطلوبة */
    public String getItemsByIdsAsJson(List<String> ids, String senderDeviceId, String senderDeviceName) {
        try {
            org.json.JSONArray itemsArray = new org.json.JSONArray();
            for (String id : ids) {
                Item item = getItemById(id);
                if (item != null && item.getHopCount() < item.getMaxHops()) {
                    itemsArray.put(item.toJson());
                }
            }
            org.json.JSONObject envelope = new org.json.JSONObject();
            envelope.put("senderDeviceId", senderDeviceId);
            envelope.put("senderDeviceName", senderDeviceName);
            envelope.put("items", itemsArray);
            return envelope.toString();
        } catch (org.json.JSONException e) {
            return "{}";
        }
    }

    // ================= استيراد المعلومات المستلمة عبر البلوتوث =================

    /** يستورد حزمة معلومات وصلت من جهاز تاني (رداً على طلب "الناقص فقط")، ويرجع [عدد الجديد, عدد المكرر] */
    public int[] importReceivedItems(String json, String immediateSenderDeviceId) {
        int importedCount = 0;
        int duplicateCount = 0;
        try {
            org.json.JSONObject envelope = new org.json.JSONObject(json);
            org.json.JSONArray array = envelope.getJSONArray("items");

            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);

                String id = obj.getString("id");
                if (itemExistsById(id)) {
                    duplicateCount++;
                    continue;
                }

                Item received = jsonToReceivedItem(obj, immediateSenderDeviceId);
                if (insertItem(received)) importedCount++;
            }
        } catch (org.json.JSONException e) {
            android.util.Log.e("DatabaseHelper", "خطأ بقراءة المعلومات المستوردة: " + e.getMessage());
        }
        return new int[]{importedCount, duplicateCount};
    }

    private Item jsonToReceivedItem(org.json.JSONObject obj, String immediateSenderDeviceId) throws org.json.JSONException {
        String id = obj.getString("id");
        String title = obj.getString("title");
        String body = obj.optString("body", "");
        String type = obj.getString("type");
        String area = obj.optString("area", "");
        String priority = obj.optString("priority", "");
        String payloadHash = obj.optString("payloadHash", "");
        int version = obj.optInt("version", 1);
        String originDeviceId = obj.optString("originDeviceId", "");
        if (originDeviceId.isEmpty()) originDeviceId = immediateSenderDeviceId;
        boolean isAdminBroadcast = obj.optBoolean("isAdminBroadcast", false);
        long createdAt = obj.getLong("createdAt");
        long updatedAt = obj.optLong("updatedAt", createdAt);
        long expiresAtRaw = obj.optLong("expiresAt", -1);
        int hopCount = obj.optInt("hopCount", 0);
        int maxHops = obj.optInt("maxHops", 5);

        Item received = new Item();
        received.setId(id);
        received.setTitle(title);
        received.setBody(body);
        received.setType(type);
        received.setArea(area);
        received.setPriority(priority);
        received.setPayloadHash(payloadHash);
        received.setVersion(version);
        received.setOriginDeviceId(originDeviceId);
        received.setOriginAdminId(null);
        received.setAdminBroadcast(isAdminBroadcast);
        received.setCreatedAt(createdAt);
        received.setUpdatedAt(updatedAt);
        received.setExpiresAt(expiresAtRaw == -1 ? null : expiresAtRaw);
        received.setHopCount(hopCount + 1);
        received.setMaxHops(maxHops);
        received.setSyncStatus("pending");
        received.setRead(false);
        received.setHiddenLocally(false);
        return received;
    }

    // ================= عمليات جدول devices =================

    public void upsertDevice(String deviceId, String macAddress, String name, int rssi, long now) {
        if (deviceId == null) return;

        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query("devices", new String[]{"id"}, "id = ?", new String[]{deviceId}, null, null, null);

        if (cursor.moveToFirst()) {
            ContentValues values = new ContentValues();
            values.put("mac_address", macAddress);
            values.put("last_rssi", rssi);
            values.put("last_seen_at", now);
            if (name != null) values.put("name", name);
            db.update("devices", values, "id = ?", new String[]{deviceId});
        } else {
            ContentValues values = new ContentValues();
            values.put("id", deviceId);
            values.put("name", name);
            values.put("mac_address", macAddress);
            values.put("last_rssi", rssi);
            values.put("first_seen_at", now);
            values.put("last_seen_at", now);
            values.put("last_sync_at", 0);
            db.insert("devices", null, values);
        }
        cursor.close();
    }

    public void updateLastSyncAt(String deviceId, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("last_sync_at", timestamp);
        db.update("devices", values, "id = ?", new String[]{deviceId});
    }

    public List<Device> getAllDevices() {
        List<Device> devices = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("devices", null, null, null, null, null, "last_seen_at DESC");

        while (cursor.moveToNext()) {
            Device device = new Device();
            device.setId(cursor.getString(cursor.getColumnIndexOrThrow("id")));
            device.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            device.setMacAddress(cursor.getString(cursor.getColumnIndexOrThrow("mac_address")));
            device.setLastRssi(cursor.getInt(cursor.getColumnIndexOrThrow("last_rssi")));
            device.setFirstSeenAt(cursor.getLong(cursor.getColumnIndexOrThrow("first_seen_at")));
            device.setLastSeenAt(cursor.getLong(cursor.getColumnIndexOrThrow("last_seen_at")));
            device.setLastSyncAt(cursor.getLong(cursor.getColumnIndexOrThrow("last_sync_at")));
            devices.add(device);
        }
        cursor.close();
        return devices;
    }

    public int getActiveDeviceCount() {
        SQLiteDatabase db = getReadableDatabase();
        long twoMinutesAgo = System.currentTimeMillis() - (2 * 60 * 1000);
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM devices WHERE last_seen_at > ?",
                new String[]{String.valueOf(twoMinutesAgo)});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    // ================= عمليات جدول sync_logs =================

    public void logSyncEvent(String deviceId, int sentCount, int receivedCount, int duplicateCount, long syncTimeMs) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("device_id", deviceId);
        values.put("sent_count", sentCount);
        values.put("received_count", receivedCount);
        values.put("duplicate_count", duplicateCount);
        values.put("sync_time_ms", syncTimeMs);
        values.put("created_at", System.currentTimeMillis());
        db.insert("sync_logs", null, values);
    }

    public List<Object[]> getRecentSyncLogs() {
        List<Object[]> logs = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT sync_logs.device_id, devices.name, sync_logs.sent_count, " +
                        "sync_logs.received_count, sync_logs.duplicate_count, sync_logs.created_at " +
                        "FROM sync_logs LEFT JOIN devices ON sync_logs.device_id = devices.id " +
                        "ORDER BY sync_logs.created_at DESC LIMIT 50", null);

        while (cursor.moveToNext()) {
            String deviceName = cursor.getString(1);
            if (deviceName == null) deviceName = "جهاز غير معروف";
            logs.add(new Object[]{
                    cursor.getString(0),
                    deviceName,
                    cursor.getInt(2),
                    cursor.getInt(3),
                    cursor.getInt(4),
                    cursor.getLong(5)
            });
        }
        cursor.close();
        return logs;
    }

    // ================= أدوات مساعدة =================

    public String getDatabaseSizeFormatted() {
        java.io.File dbFile = context.getDatabasePath(DATABASE_NAME);
        if (!dbFile.exists()) return "0KB";
        long sizeBytes = dbFile.length();
        double sizeMB = sizeBytes / (1024.0 * 1024.0);
        if (sizeMB < 0.1) {
            double sizeKB = sizeBytes / 1024.0;
            return String.format(java.util.Locale.US, "%.0fKB", sizeKB);
        }
        return String.format(java.util.Locale.US, "%.1fMB", sizeMB);
    }
}