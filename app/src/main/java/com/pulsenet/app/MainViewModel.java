package com.pulsenet.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.Item;
import java.util.List;

public class MainViewModel extends AndroidViewModel {
    private final DatabaseHelper databaseHelper;
    private final MutableLiveData<List<Item>> items = new MutableLiveData<>();
    private final MutableLiveData<Integer> deviceCount = new MutableLiveData<>();

    // هذا الجزء يستقبل الإشارة من الخدمة ويحدث البيانات فوراً
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshData();
        }
    };

    public MainViewModel(@NonNull Application application) {
        super(application);
        databaseHelper = new DatabaseHelper(application);

        // تسجيل المستمع (الرسيفر)
        // ملاحظة مهمة: Context.registerReceiver(receiver, filter, int) بأربعة معاملات
        // موجودة فقط من أندرويد 13 (API 33) وفوق. استخدامها مباشرة كان رح يسبب
        // NoSuchMethodError ويوقّف التطبيق على أي جهاز بنسخة أقدم.
        // ContextCompat.registerReceiver من AndroidX بتشتغل صح على كل النسخ.
        // واستخدمنا RECEIVER_NOT_EXPORTED لأنه هاد البث داخلي بس بين مكونات
        // تطبيقنا (الخدمة بترسله بـ setPackage)، مش لازم أي تطبيق تاني يقدر يشغّله.
        IntentFilter filter = new IntentFilter("com.pulsenet.UPDATE_UI");
        ContextCompat.registerReceiver(application, updateReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        refreshData(); // تحميل البيانات لأول مرة
    }

    public LiveData<List<Item>> getItems() { return items; }
    public LiveData<Integer> getDeviceCount() { return deviceCount; }

    public void refreshData() {
        // جلب البيانات من قاعدة البيانات وإرسالها للواجهة
        items.postValue(databaseHelper.getAllItems());
        deviceCount.postValue(databaseHelper.getActiveDeviceCount());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().unregisterReceiver(updateReceiver);
    }
}