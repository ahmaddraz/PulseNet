package com.pulsenet.app.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.DeviceIdentity;
import com.pulsenet.app.data.Item;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * مسؤول عن التزامن مع سيرفر الباك اند (Laravel) لما يتوفر إنترنت.
 * هذا اختياري بالكامل - التطبيق أصلاً بيشتغل بدون هالسيرفر عبر البلوتوث.
 * إذا ما تحدد رابط سيرفر بعد بالإعدادات، كل الدوال هون ما بتعمل شي (بأمان تام).
 */
public class BackendSyncManager {

    private static final String TAG = "BackendSyncManager";
    private static final String PREFS_NAME = "pulsenet_prefs";
    private static final String KEY_BASE_URL = "backend_base_url";
    private static final String KEY_LAST_PULL = "backend_last_pull_at";

    private final Context context;
    private final DatabaseHelper databaseHelper;

    public BackendSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.databaseHelper = new DatabaseHelper(context);
    }

    /** يرجع رابط السيرفر المحفوظ بالإعدادات، أو null لو ما تحدد بعد */
    private String getBaseUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_BASE_URL, "");
        return url.isEmpty() ? null : url;
    }

    public static void setBaseUrl(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    public static String getBaseUrlStatic(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BASE_URL, "");
    }

    /** يبلش عملية تزامن كاملة مع السيرفر (بالخلفية) - ما بيعمل شي لو ما في رابط محدد */
    public void syncWithBackendIfConfigured() {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) {
            Log.i(TAG, "ما في رابط سيرفر محدد بعد - تخطينا التزامن مع الباك اند");
            return;
        }

        new SyncTask(baseUrl).execute();
    }

    private class SyncTask extends AsyncTask<Void, Void, Boolean> {
        private final String baseUrl;

        SyncTask(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                pullNewItemsFromServer(baseUrl);
                pushDeviceStatus(baseUrl);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "فشل التزامن مع الباك اند: " + e.getMessage());
                return false;
            }
        }
    }

    /** يسحب المعلومات الجديدة من السيرفر (خصوصاً تنبيهات الأدمن) ويحفظها محلياً */
    private void pullNewItemsFromServer(String baseUrl) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastPull = prefs.getLong(KEY_LAST_PULL, 0);

        URL url = new URL(baseUrl + "/api/items?since=" + lastPull);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);

        String response = readResponse(connection);
        connection.disconnect();

        org.json.JSONObject json = new org.json.JSONObject(response);
        org.json.JSONArray items = json.optJSONArray("items");
        if (items == null) return;

        for (int i = 0; i < items.length(); i++) {
            org.json.JSONObject obj = items.getJSONObject(i);
            String id = obj.getString("id");

            if (databaseHelper.itemExistsById(id)) continue;

            Item item = new Item();
            item.setId(id);
            item.setTitle(obj.getString("title"));
            item.setBody(obj.optString("body", ""));
            item.setType(obj.getString("type"));
            item.setArea(obj.optString("area", ""));
            item.setPriority(obj.optString("priority", ""));
            item.setPayloadHash(obj.optString("payload_hash", ""));
            item.setVersion(obj.optInt("version", 1));
            item.setOriginDeviceId(obj.optString("origin_device_id", null));
            item.setOriginAdminId(obj.optString("origin_admin_id", null));
            item.setAdminBroadcast(obj.optBoolean("is_admin_broadcast", true));
            long now = System.currentTimeMillis();
            item.setCreatedAt(obj.optLong("created_at", now));
            item.setUpdatedAt(obj.optLong("updated_at", now));
            item.setExpiresAt(now + (72L * 60 * 60 * 1000));
            item.setHopCount(0);
            item.setMaxHops(5);
            item.setSyncStatus("synced");
            item.setRead(false);
            item.setHiddenLocally(false);

            databaseHelper.insertItem(item);
        }

        prefs.edit().putLong(KEY_LAST_PULL, System.currentTimeMillis()).apply();
    }

    /** يرفع حالة جهازنا للسيرفر (اسم، آخر ظهور) - لصفحة "الأجهزة" بلوحة التحكم */
    private void pushDeviceStatus(String baseUrl) throws Exception {
        String deviceId = DeviceIdentity.getDeviceId(context);
        String deviceName = DeviceIdentity.getDeviceName(context);
        long now = System.currentTimeMillis();

        org.json.JSONObject body = new org.json.JSONObject();
        body.put("id", deviceId);
        body.put("name", deviceName);
        body.put("last_seen_at", now);
        body.put("last_sync_at", now);

        URL url = new URL(baseUrl + "/api/devices");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        readResponse(connection);
        connection.disconnect();
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }
}