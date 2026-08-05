package com.pulsenet.app;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pulsenet.app.data.Device;
import com.pulsenet.app.data.DatabaseHelper;

import java.util.List;

public class NearbyDevicesActivity extends AppCompatActivity {

    private LinearLayout containerDevices;
    private TextView textEmptyState;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_devices);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop() + systemBars.top,
                    v.getPaddingRight(), v.getPaddingBottom() + systemBars.bottom);
            return insets;
        });

        containerDevices = findViewById(R.id.container_devices);
        textEmptyState = findViewById(R.id.text_empty_state);
        databaseHelper = new DatabaseHelper(this);

        findViewById(R.id.button_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDevices();
    }

    /** يقرأ كل الأجهزة من القاعدة، ويبني بطاقة (Card) لكل واحد منهم برمجياً */
    private void loadDevices() {
        containerDevices.removeAllViews();

        List<Device> devices = databaseHelper.getAllDevices();

        if (devices.isEmpty()) {
            textEmptyState.setVisibility(android.view.View.VISIBLE);
            return;
        }
        textEmptyState.setVisibility(android.view.View.GONE);

        for (Device device : devices) {
            containerDevices.addView(buildDeviceCard(device));
        }
    }

    /** ينشئ بطاقة واحدة تعرض معلومات جهاز واحد */
    private LinearLayout buildDeviceCard(Device device) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_border_grey);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        TextView name = new TextView(this);
        String deviceName = device.getName() != null ? device.getName() : "جهاز PulseNet غير مسمّى";
        name.setText(deviceName);
        name.setTextColor(getColor(R.color.white));
        name.setTextSize(11.5f);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setGravity(android.view.Gravity.END);

        TextView details = new TextView(this);
        long secondsAgo = (System.currentTimeMillis() - device.getLastSeenAt()) / 1000;
        String lastSyncText = device.getLastSyncAt() > 0
                ? " · آخر تزامن: منذ " + ((System.currentTimeMillis() - device.getLastSyncAt()) / 1000 / 60) + " دقيقة"
                : " · ما صار تزامن بعد";
        details.setText("قوة الإشارة: " + device.getLastRssi() + " dBm · آخر ظهور: منذ " + secondsAgo + " ثانية" + lastSyncText);
        details.setTextColor(getColor(R.color.color_text_secondary));
        details.setTextSize(9.5f);
        details.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailsParams.topMargin = dpToPx(3);
        details.setLayoutParams(detailsParams);

        card.addView(name);
        card.addView(details);
        return card;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}