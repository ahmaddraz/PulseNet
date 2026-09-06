package com.pulsenet.app.data;

import com.pulsenet.app.ble.BleConstants;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/**
 * يمثل "معلومة" واحدة بالتطبيق، مطابق لمخطط ER الأكاديمي المعتمد.
 */
public class Item {

    // ---------- ثوابت أنواع المعلومة (type) ----------
    public static final String TYPE_ALERT = "alert";       // طوارئ
    public static final String TYPE_SERVICE = "service";   // خدمة
    public static final String TYPE_AID = "aid";            // مساعدة
    public static final String TYPE_LOCATION = "location"; // موقع

    /** يترجم التصنيف العربي لقيمة إنجليزية للتخزين */
    public static String arabicCategoryToType(String arabicCategory) {
        if (arabicCategory == null) return TYPE_ALERT;
        switch (arabicCategory) {
            case "طوارئ": return TYPE_ALERT;
            case "خدمة": return TYPE_SERVICE;
            case "مساعدة": return TYPE_AID;
            case "موقع": return TYPE_LOCATION;
            default: return TYPE_ALERT;
        }
    }

    /** يترجم التصنيف الإنجليزي المخزّن لنص عربي نعرضه بالواجهة */
    public static String typeToArabicCategory(String type) {
        if (type == null) return "طوارئ";
        switch (type) {
            case TYPE_SERVICE: return "خدمة";
            case TYPE_AID: return "مساعدة";
            case TYPE_LOCATION: return "موقع";
            case TYPE_ALERT:
            default: return "طوارئ";
        }
    }

    // ---------- الحقول ----------
    private String id;
    private String title;
    private String body;
    private String type;
    private String area;
    private String priority;
    private String payloadHash;
    private int version;
    private String originDeviceId;
    private String originAdminId;
    private boolean isAdminBroadcast;
    private long createdAt;
    private long updatedAt;
    private Long expiresAt;
    private int hopCount;
    private int maxHops;
    private String syncStatus;
    private boolean isRead;
    private boolean isHiddenLocally;

    public Item() {
    }

    /** Constructor مبسّط لإنشاء معلومة جديدة محلياً */
    public Item(String id, String title, String body, String type,
                String area, String priority, String originDeviceId, long createdAt) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.type = type;
        this.area = area;
        this.priority = priority;
        this.payloadHash = computeHash(title, body, type, area);
        this.version = 1;
        this.originDeviceId = originDeviceId;
        this.originAdminId = null;
        this.isAdminBroadcast = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = createdAt + (72L * 60 * 60 * 1000); // 72 ساعة
        this.hopCount = 0;
        this.maxHops = 5;
        this.syncStatus = "pending";
        this.isRead = false;
        this.isHiddenLocally = false;
    }

    /** يحسب بصمة بسيطة (Hash) من محتوى المعلومة */
    public static String computeHash(String title, String body, String type, String area) {
        String combined = (title == null ? "" : title)
                + "|" + (body == null ? "" : body)
                + "|" + (type == null ? "" : type)
                + "|" + (area == null ? "" : area);
        return String.valueOf(combined.hashCode());
    }

    // ---------- Getters / Setters ----------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getOriginDeviceId() { return originDeviceId; }
    public void setOriginDeviceId(String originDeviceId) { this.originDeviceId = originDeviceId; }

    public String getOriginAdminId() { return originAdminId; }
    public void setOriginAdminId(String originAdminId) { this.originAdminId = originAdminId; }

    public boolean isAdminBroadcast() { return isAdminBroadcast; }
    public void setAdminBroadcast(boolean adminBroadcast) { isAdminBroadcast = adminBroadcast; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    public int getMaxHops() { return maxHops; }
    public void setMaxHops(int maxHops) { this.maxHops = maxHops; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public boolean isHiddenLocally() { return isHiddenLocally; }
    public void setHiddenLocally(boolean hiddenLocally) { isHiddenLocally = hiddenLocally; }

    /**
     * تحويل آمن لمنع الـ NullPointerExceptions عند إنشاء الـ JSON.
     *
     * ملاحظة مهمة: بروتوكول BLE (ATT) بيحدد حد أقصى مطلق 512 بايت لأي
     * "قيمة خاصية" (Characteristic Value) وحدة. لو رجّعنا JSON أكبر من هيك،
     * الجهاز التاني كان يستقبل نسخة مقطوعة/تالفة بصمت (بدون خطأ واضح) —
     * وهذا هو السبب الحقيقي وراء مشاكل "ضياع/تسرب" المعلومات أثناء النقل،
     * خصوصاً مع نصوص طويلة أو نصوص عربية (يلي بتاخد بايتين لكل حرف بترميز UTF-8).
     * لهيك، إذا حسّينا إنه الحجم قارب يتجاوز الحد الآمن، منقصّر التفاصيل
     * (body ثم title) تدريجياً لحد ما توصل لحجم آمن، بدل ما نرسلها ناقصة
     * بطريقة عشوائية يتحكم فيها الـ Bluetooth stack.
     */
    public JSONObject toJson() throws JSONException {
        String safeTitle = title != null ? title : "";
        String safeBody = body != null ? body : "";

        JSONObject obj = buildJsonInternal(safeTitle, safeBody);

        while (utf8ByteLength(obj.toString()) > BleConstants.SAFE_RESPONSE_BYTES && safeBody.length() > 10) {
            safeBody = safeBody.substring(0, safeBody.length() - 10) + "…";
            obj = buildJsonInternal(safeTitle, safeBody);
        }
        while (utf8ByteLength(obj.toString()) > BleConstants.SAFE_RESPONSE_BYTES && safeTitle.length() > 10) {
            safeTitle = safeTitle.substring(0, safeTitle.length() - 5) + "…";
            obj = buildJsonInternal(safeTitle, safeBody);
        }
        return obj;
    }

    private JSONObject buildJsonInternal(String titleValue, String bodyValue) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id != null ? id : "");
        obj.put("title", titleValue);
        obj.put("body", bodyValue);
        obj.put("type", type != null ? type : TYPE_ALERT);
        obj.put("area", area != null ? area : "");
        obj.put("priority", priority != null ? priority : "");
        obj.put("payloadHash", payloadHash != null ? payloadHash : "");
        obj.put("version", version);
        obj.put("originDeviceId", originDeviceId != null ? originDeviceId : "");
        obj.put("isAdminBroadcast", isAdminBroadcast);
        obj.put("createdAt", createdAt);
        obj.put("updatedAt", updatedAt);
        obj.put("expiresAt", expiresAt == null ? -1 : expiresAt);
        obj.put("hopCount", hopCount);
        obj.put("maxHops", maxHops);
        return obj;
    }

    private static int utf8ByteLength(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    /** تحويل آمن من JSON إلى كائن Item */
    public static Item fromJson(JSONObject obj, String defaultSenderId) throws JSONException {
        Item item = new Item();
        item.setId(obj.getString("id"));
        item.setTitle(obj.optString("title", "بدون عنوان"));
        item.setBody(obj.optString("body", ""));
        item.setType(obj.optString("type", TYPE_ALERT));
        item.setArea(obj.optString("area", ""));
        item.setPriority(obj.optString("priority", "عادي"));
        item.setPayloadHash(obj.optString("payloadHash", ""));
        item.setVersion(obj.optInt("version", 1));

        String origin = obj.optString("originDeviceId", "");
        item.setOriginDeviceId(origin.isEmpty() ? defaultSenderId : origin);

        item.setAdminBroadcast(obj.optBoolean("isAdminBroadcast", false));

        long now = System.currentTimeMillis();
        item.setCreatedAt(obj.optLong("createdAt", now));
        item.setUpdatedAt(obj.optLong("updatedAt", now));

        long expires = obj.optLong("expiresAt", -1);
        item.setExpiresAt(expires == -1 ? null : expires);

        int hops = obj.optInt("hopCount", 0);
        item.setHopCount(hops + 1); // زيادة القفزة فور الاستلام
        item.setMaxHops(obj.optInt("maxHops", 5));

        item.setSyncStatus("pending");
        item.setRead(false);
        item.setHiddenLocally(false);

        return item;
    }
}