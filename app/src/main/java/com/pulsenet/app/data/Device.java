package com.pulsenet.app.data;

/**
 * يمثل جهاز PulseNet واحد، مطابق لمخطط ER الأكاديمي.
 * الـ id هو نفسه الهوية الثابتة للجهاز (device_uuid)، مش رقم AUTOINCREMENT منفصل.
 */
public class Device {

    private String id;             // الهوية الثابتة (device_uuid) - هي نفسها الـ PK متل المخطط
    private String name;
    private long lastSeenAt;
    private long lastSyncAt;       // آخر مرة تزامنّا فعلياً مع هالجهاز (جديد - مطابق للمخطط)

    // حقول إضافية عملية لازمة لتشغيل BLE فعلياً (مش موجودة بالمخطط الأكاديمي، بس ضرورية تقنياً)
    private String macAddress;
    private int lastRssi;
    private long firstSeenAt;

    public Device() {
    }

    public Device(String id, String macAddress, String name, int rssi, long timestamp) {
        this.id = id;
        this.macAddress = macAddress;
        this.name = name;
        this.lastRssi = rssi;
        this.firstSeenAt = timestamp;
        this.lastSeenAt = timestamp;
        this.lastSyncAt = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(long lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public long getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(long lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public int getLastRssi() { return lastRssi; }
    public void setLastRssi(int lastRssi) { this.lastRssi = lastRssi; }

    public long getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(long firstSeenAt) { this.firstSeenAt = firstSeenAt; }
}