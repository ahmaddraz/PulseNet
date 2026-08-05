package com.pulsenet.app;

import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.DeviceIdentity;
import com.pulsenet.app.service.PulseNetService;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        int horizontalPadding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(horizontalPadding, v.getPaddingTop() + systemBars.top,
                    horizontalPadding, v.getPaddingBottom() + systemBars.bottom);
            return insets;
        });

        android.content.SharedPreferences prefs = getSharedPreferences("pulsenet_prefs", MODE_PRIVATE);

        // ---------- مفتاح البث المستمر ----------
        android.widget.Switch switchBroadcast = findViewById(R.id.switch_broadcast);
        switchBroadcast.setChecked(prefs.getBoolean("broadcast_enabled", true));

        switchBroadcast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("broadcast_enabled", isChecked).apply();

            android.content.Intent serviceIntent = new android.content.Intent(this, PulseNetService.class);

            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "تم تفعيل البث والاكتشاف", Toast.LENGTH_SHORT).show();
            } else {
                stopService(serviceIntent);
                Toast.makeText(this, "تم إيقاف البث والاكتشاف", Toast.LENGTH_SHORT).show();
            }
        });

        // ---------- مفتاح الإشعارات المحلية ----------
        android.widget.Switch switchNotifications = findViewById(R.id.switch_notifications);
        switchNotifications.setChecked(prefs.getBoolean("notifications_enabled", true));

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply();
        });

        // ---------- الأجهزة المتصلة ----------
        findViewById(R.id.row_devices).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, NearbyDevicesActivity.class));
        });

        // ---------- اسم الجهاز الثابت ----------
        TextView textDeviceName = findViewById(R.id.text_device_name_value);
        textDeviceName.setText(DeviceIdentity.getDeviceName(this));

        // ---------- مساحة التخزين الحقيقية ----------
        DatabaseHelper databaseHelper = new DatabaseHelper(this);
        TextView textStorageSize = findViewById(R.id.text_storage_size);
        textStorageSize.setText(databaseHelper.getDatabaseSizeFormatted());

        // ---------- زر إعادة تشغيل الخدمة ----------
        findViewById(R.id.button_restart_service).setOnClickListener(v -> {
            android.content.Intent stopIntent = new android.content.Intent(this, PulseNetService.class);
            stopService(stopIntent);

            android.content.Intent startIntent = new android.content.Intent(this, PulseNetService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, startIntent);
            } else {
                startService(startIntent);
            }

            Toast.makeText(this, "تم إعادة تشغيل PulseNet", Toast.LENGTH_SHORT).show();
        });
    }
}