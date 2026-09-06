package com.pulsenet.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pulsenet.app.data.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * دور "العميل" (Central) بشبكة PulseNet - يتصل بجهاز مكتشف ويسحب منه
 * المعلومات الناقصة عنا فقط.
 *
 * بروتوكول التبادل (بنظام صفحات - Pagination)، خطوة خطوة:
 *   1. نطلب صفحة من ملخص الجهاز التاني (action=summary_page, cursor=0).
 *   2. نقارن كل معلومة بالصفحة مع قاعدتنا المحلية فوراً، ونجمّع الـ id
 *      الناقصة بلائحة missingIds.
 *   3. إذا في صفحة كمان (nextCursor != -1)، نكرر الخطوة 1 بالـ cursor الجديد.
 *   4. لما تخلص كل صفحات الملخص: إذا ما عنا نقص، منخلّص فوراً. إذا عنا
 *      نقص، نطلب معلومة وحدة بكل مرة (action=items_page) لحد ما نجيبهم كلهم.
 *   5. نجمع كل المعلومات يلي وصلتنا بمصفوفة وحدة، ونسلّمها دفعة وحدة
 *      للمستمع (ItemsReceivedListener) بالنهاية - فنفس واجهة الاستخدام
 *      القديمة (PulseNetService) ظلّت بدون أي تغيير.
 *
 * ليش هيك بدل قراءة كل شي دفعة وحدة زي قبل؟ لأنه بروتوكول BLE (ATT) بيمنع
 * أي رسالة وحدة توصل لأكتر من 512 بايت. لو طلبنا "كل الملخص" أو "كل
 * المعلومات الناقصة" دفعة وحدة، بمجرد ما يكبر عدد المعلومات أو يطول نص
 * أي وحدة منهم، كانت النتيجة توصل مقطوعة/تالفة بصمت - وهاد هو السبب
 * الحقيقي وراء مشاكل "ضياع/تسرب" المعلومات أثناء النقل. بتقسيم الطلب
 * لخطوات صغيرة (صفحة ملخص، معلومة وحدة بكل مرة)، كل رسالة لحالها صغيرة
 * ومضمونة، بغض النظر كم إجمالي المعلومات المتراكمة.
 */
public class BleGattClient {

    private static final String TAG = "BleGattClient";

    public interface ItemsReceivedListener {
        void onItemsReceived(String json);
        void onConnectionFailed();
    }

    private final Context context;
    private final DatabaseHelper databaseHelper;
    private final String myDeviceId;
    private final String myDeviceName;
    private BluetoothGatt bluetoothGatt;
    private ItemsReceivedListener listener;

    private volatile boolean isServicesDiscovered = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable mtuTimeoutRunnable;

    // ---------- حالة جولة التبادل الحالية (تتصفر بكل اتصال جديد) ----------
    private String pendingAction; // "summary_page" أو "items_page" - شو طلبنا آخر مرة
    private final List<String> missingIds = new ArrayList<>();
    private int nextMissingIndex = 0;
    private final JSONArray collectedItems = new JSONArray();
    private String remoteDeviceId;
    private String remoteDeviceName = "جهاز PulseNet";

    public BleGattClient(Context context, String myDeviceId, String myDeviceName) {
        this.context = context.getApplicationContext();
        this.databaseHelper = new DatabaseHelper(this.context);
        this.myDeviceId = myDeviceId;
        this.myDeviceName = myDeviceName;
    }

    @SuppressLint("MissingPermission")
    public void connectAndFetchItems(BluetoothDevice device, ItemsReceivedListener listener) {
        this.listener = listener;
        this.isServicesDiscovered = false;
        resetExchangeState();
        Log.i(TAG, "عم نبدأ اتصال بـ " + device.getAddress());
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private void resetExchangeState() {
        pendingAction = null;
        missingIds.clear();
        nextMissingIndex = 0;
        while (collectedItems.length() > 0) collectedItems.remove(0);
        remoteDeviceId = null;
        remoteDeviceName = "جهاز PulseNet";
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "اتصلنا - عم نطلب حجم رسالة أكبر (MTU)");

                // صمام أمان في حال عدم استجابة الجهاز لـ MTU خلال 1.5 ثانية
                mtuTimeoutRunnable = () -> {
                    if (!isServicesDiscovered && bluetoothGatt != null) {
                        Log.w(TAG, "تأخر الـ MTU، جاري اكتشاف الخدمات تلقائياً...");
                        isServicesDiscovered = true;
                        gatt.discoverServices();
                    }
                };
                mainHandler.postDelayed(mtuTimeoutRunnable, 1500);

                if (!gatt.requestMtu(512)) {
                    // إذا فشل طلب الـ MTU فوراً، اكتشف الخدمات مباشرة
                    mainHandler.removeCallbacks(mtuTimeoutRunnable);
                    isServicesDiscovered = true;
                    gatt.discoverServices();
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "انقطع الاتصال بالجهاز");
                closeGattSilently();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(TAG, "onMtuChanged - mtu=" + mtu);
            if (mtuTimeoutRunnable != null) mainHandler.removeCallbacks(mtuTimeoutRunnable);

            if (!isServicesDiscovered) {
                isServicesDiscovered = true;
                gatt.discoverServices();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                notifyFailed();
                return;
            }

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                notifyFailed();
                return;
            }

            Log.i(TAG, "عم نبلش تبادل الملخص صفحة-صفحة...");
            requestSummaryPage(gatt, 0);
        }

        // --- دعم أجهزة Android 12 وما دون (API < 33) ---
        @Override
        @Deprecated
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                byte[] value = characteristic.getValue();
                processCharacteristicRead(gatt, value, status);
            }
        }

        // --- دعم أجهزة Android 13 وما فوق (API >= 33) ---
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                processCharacteristicRead(gatt, value, status);
            }
        }

        @SuppressLint("MissingPermission")
        private void processCharacteristicRead(BluetoothGatt gatt, byte[] value, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS || value == null) {
                notifyFailed();
                return;
            }

            String json = new String(value, StandardCharsets.UTF_8);

            if ("summary_page".equals(pendingAction)) {
                processSummaryPageResponse(gatt, json);
            } else if ("items_page".equals(pendingAction)) {
                processItemsPageResponse(gatt, json);
            } else {
                Log.w(TAG, "رد وصل بدون ما نكون منتظرين شي - تجاهلناه");
                finishAndDeliver();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (!BleConstants.REQUEST_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) return;

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "فشل إرسال رسالة التحكم (" + pendingAction + ")");
                notifyFailed();
                return;
            }

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                notifyFailed();
                return;
            }

            // حسب شو طلبنا آخر مرة، منقرأ من الخاصية المناسبة
            java.util.UUID targetUuid = "summary_page".equals(pendingAction)
                    ? BleConstants.SUMMARY_CHARACTERISTIC_UUID
                    : BleConstants.ITEMS_CHARACTERISTIC_UUID;

            BluetoothGattCharacteristic targetCharacteristic = service.getCharacteristic(targetUuid);
            if (targetCharacteristic == null) {
                notifyFailed();
                return;
            }

            gatt.readCharacteristic(targetCharacteristic);
        }
    };

    /** يعالج صفحة ملخص وصلتنا: يقارنها مع قاعدتنا المحلية فوراً، وبعدين يقرر الخطوة الجاية */
    private void processSummaryPageResponse(BluetoothGatt gatt, String json) {
        try {
            JSONObject envelope = new JSONObject(json);
            remoteDeviceId = envelope.optString("senderDeviceId", remoteDeviceId);
            remoteDeviceName = envelope.optString("senderDeviceName", remoteDeviceName);

            JSONArray entries = envelope.optJSONArray("entries");
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    String id = entry.getString("id");
                    int remoteVersion = entry.optInt("version", 1);
                    if (databaseHelper.isMissingOrStale(id, remoteVersion)) {
                        missingIds.add(id);
                    }
                }
            }

            int nextCursor = envelope.optInt("nextCursor", -1);
            if (nextCursor >= 0) {
                requestSummaryPage(gatt, nextCursor); // في صفحة كمان من الملخص
                return;
            }

            Log.i(TAG, "خلصنا الملخص كامل - عنا نقص بـ " + missingIds.size() + " معلومة");
            if (missingIds.isEmpty()) {
                // هويتنا اتسجّلت أصلاً عند الطرف التاني من أول رسالة تحكم بعتناها
                finishAndDeliver();
            } else {
                requestNextMissingItem(gatt);
            }
        } catch (JSONException e) {
            Log.e(TAG, "خطأ بقراءة صفحة الملخص: " + e.getMessage());
            notifyFailed();
        }
    }

    /** يعالج رد "معلومة وحدة" وصلتنا، ويطلب التالية لحد ما نخلص اللائحة */
    private void processItemsPageResponse(BluetoothGatt gatt, String json) {
        try {
            JSONObject envelope = new JSONObject(json);
            JSONArray items = envelope.optJSONArray("items");
            int got = (items != null) ? items.length() : 0;
            String expectedId = nextMissingIndex < missingIds.size() ? missingIds.get(nextMissingIndex) : "?";
            Log.i(TAG, "وصلتنا معلومة رقم " + (nextMissingIndex + 1) + "/" + missingIds.size()
                    + " (id=" + expectedId + ", عدد بالرد=" + got + ")");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    collectedItems.put(items.getJSONObject(i));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "خطأ بقراءة رد المعلومة - نتجاوزها ونكمل الباقي: " + e.getMessage());
        }
        nextMissingIndex++;
        requestNextMissingItem(gatt);
    }

    private void requestNextMissingItem(BluetoothGatt gatt) {
        if (nextMissingIndex >= missingIds.size()) {
            finishAndDeliver();
            return;
        }
        String id = missingIds.get(nextMissingIndex);
        JSONArray ids = new JSONArray();
        ids.put(id);
        sendControlMessage(gatt, "items_page", ids, -1);
    }

    private void requestSummaryPage(BluetoothGatt gatt, int cursor) {
        sendControlMessage(gatt, "summary_page", null, cursor);
    }

    /** يبني رسالة التحكم الصغيرة (دايماً تحت حد الـ 512 بايت) ويرسلها لخاصية "الطلب" */
    @SuppressLint("MissingPermission")
    private void sendControlMessage(BluetoothGatt gatt, String action, JSONArray requestedIdsOrNull, int cursorOrMinusOne) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", action);
            if (requestedIdsOrNull != null) obj.put("requestedIds", requestedIdsOrNull);
            if (cursorOrMinusOne >= 0) obj.put("cursor", cursorOrMinusOne);
            obj.put("requesterDeviceId", myDeviceId);
            obj.put("requesterDeviceName", myDeviceName);

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                notifyFailed();
                return;
            }
            BluetoothGattCharacteristic requestCharacteristic =
                    service.getCharacteristic(BleConstants.REQUEST_CHARACTERISTIC_UUID);
            if (requestCharacteristic == null) {
                notifyFailed();
                return;
            }

            pendingAction = action;
            writeCharacteristicData(gatt, requestCharacteristic, obj.toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException e) {
            Log.e(TAG, "خطأ ببناء رسالة التحكم: " + e.getMessage());
            notifyFailed();
        }
    }

    @SuppressLint("MissingPermission")
    private void writeCharacteristicData(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            characteristic.setValue(data);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            gatt.writeCharacteristic(characteristic);
        }
    }

    /** لمّا نخلص كل الجولات: نبني الرد النهائي المجمّع ونسلّمه للمستمع، ونقطع الاتصال */
    private void finishAndDeliver() {
        try {
            JSONObject finalEnvelope = new JSONObject();
            finalEnvelope.put("senderDeviceId", remoteDeviceId != null ? remoteDeviceId : "");
            finalEnvelope.put("senderDeviceName", remoteDeviceName);
            finalEnvelope.put("items", collectedItems);
            if (listener != null) listener.onItemsReceived(finalEnvelope.toString());
        } catch (JSONException e) {
            Log.e(TAG, "خطأ ببناء الرد النهائي: " + e.getMessage());
            if (listener != null) listener.onItemsReceived("{\"items\":[]}");
        }
        disconnect();
    }

    /**
     * تُستخدم من الخارج (PulseNetService) عند انتهاء مهلة الاتصال. سابقاً كان
     * الكود ينادي forceClose() مباشرة عند التايم أوت، وهذا كان يرمي بالكامل
     * أي معلومات نجحنا نجمعها فعلاً بهاي الجولة بالذات (حتى لو جبنا 8 من
     * أصل 10 معلومات ناقصة، كانت الـ 8 هاي تنرمى ولازم تنطلب من الصفر
     * بالاتصال الجاي) - وهذا سبب رئيسي محتمل وراء "بعض المعلومات ما بتوصل"
     * بشكل دائم لما يكون عدد النقص كبير: نفس المعلومات الأخيرة بترتيب
     * الملخص كانت تضل عالقة بعد آخر معلومة نوصلها قبل التايم أوت بكل مرة.
     *
     * الحل: نسلّم أي معلومات نجحنا نجمعها فعلاً للمستمع أولاً (بيتم حفظها
     * بقاعدة البيانات فوراً)، وبعدين نقفل الاتصال. الباقي (يلي لسا ناقص)
     * رح ينطلب تلقائياً بالاتصال الجاي لنفس الجهاز.
     */
    public void finishWithPartialResultsAndForceClose() {
        Log.w(TAG, "انتهت مهلة الاتصال - جمعنا " + collectedItems.length() + " من أصل "
                + missingIds.size() + " معلومة ناقصة. رح نسلّم يلي وصلنا ونقفل.");
        if (collectedItems.length() > 0) {
            try {
                JSONObject finalEnvelope = new JSONObject();
                finalEnvelope.put("senderDeviceId", remoteDeviceId != null ? remoteDeviceId : "");
                finalEnvelope.put("senderDeviceName", remoteDeviceName);
                finalEnvelope.put("items", collectedItems);
                if (listener != null) listener.onItemsReceived(finalEnvelope.toString());
            } catch (JSONException e) {
                Log.e(TAG, "خطأ ببناء الرد الجزئي عند انتهاء المهلة: " + e.getMessage());
            }
        }
        forceClose();
    }

    private void notifyFailed() {
        if (listener != null) listener.onConnectionFailed();
        disconnect();
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
            } catch (Exception ignored) {}
        }
    }

    /**
     * إغلاق فوري وأكيد للاتصال، حتى لو ما وصل Callback الفصل من نظام أندرويد.
     * لازم نستخدمها كصمام أمان (مثلاً بعد انتهاء مهلة الاتصال بالخدمة)، لأنه
     * الاعتماد فقط على onConnectionStateChange ممكن -بحالات نادرة- ما يصير،
     * وهيك يضل الـ GATT handle محجوز لحد ما نوصل للحد الأقصى المسموح
     * (حوالي 32 اتصال GATT بنفس الوقت لكل تطبيق) ويوقف أي اتصال جديد.
     */
    @SuppressLint("MissingPermission")
    public void forceClose() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception ignored) {}
            bluetoothGatt = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void closeGattSilently() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (Exception ignored) {}
            bluetoothGatt = null;
        }
    }
}
