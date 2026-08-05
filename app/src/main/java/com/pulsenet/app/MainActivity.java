package com.pulsenet.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pulsenet.app.ble.PermissionHelper;
import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.Item;
import com.pulsenet.app.service.PulseNetService;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private DatabaseHelper databaseHelper;
    private TextView textDeviceCount;
    private TextView textEmptyItems;
    private LinearLayout containerItems;

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    startPulseNetService();
                } else {
                    Toast.makeText(this, "لازم تفعّل البلوتوث حتى يشتغل PulseNet", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantResults -> {
                boolean allGranted = true;
                for (Boolean granted : grantResults.values()) {
                    if (granted == null || !granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    ensureBluetoothEnabledThenStart();
                } else {
                    Toast.makeText(this,
                            "بدون هالصلاحيات ما بيقدر PulseNet يكتشف الأجهزة القريبة",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        textDeviceCount = findViewById(R.id.text_device_count);
        textEmptyItems = findViewById(R.id.text_empty_items);
        containerItems = findViewById(R.id.container_items);
        databaseHelper = new DatabaseHelper(this);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        findViewById(R.id.button_add).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AddInfoActivity.class));
        });

        findViewById(R.id.button_settings).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        findViewById(R.id.button_notifications).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, NotificationsActivity.class));
        });

        requestPermissionsThenStart();
    }

    private void requestPermissionsThenStart() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "هذا الجهاز ما بيدعم البلوتوث", Toast.LENGTH_LONG).show();
            return;
        }
        if (PermissionHelper.hasAllBlePermissions(this)) {
            ensureBluetoothEnabledThenStart();
        } else {
            permissionLauncher.launch(PermissionHelper.getRequiredBlePermissions());
        }
    }

    private void ensureBluetoothEnabledThenStart() {
        if (bluetoothAdapter.isEnabled()) {
            startPulseNetService();
        } else {
            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    private void startPulseNetService() {
        android.content.SharedPreferences prefs = getSharedPreferences("pulsenet_prefs", MODE_PRIVATE);
        boolean broadcastEnabled = prefs.getBoolean("broadcast_enabled", true);
        if (!broadcastEnabled) return;

        Intent serviceIntent = new Intent(this, PulseNetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void updateDeviceCountDisplay() {
        int count = databaseHelper.getActiveDeviceCount();
        textDeviceCount.setText(count + " أجهزة متصلة الآن");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDeviceCountDisplay();
        loadItems();
    }

    private void loadItems() {
        containerItems.removeAllViews();

        List<Item> items = databaseHelper.getAllItems();

        if (items.isEmpty()) {
            textEmptyItems.setVisibility(View.VISIBLE);
            return;
        }
        textEmptyItems.setVisibility(View.GONE);

        for (Item item : items) {
            containerItems.addView(buildItemCard(item));
        }
    }

    private LinearLayout buildItemCard(Item item) {
        int borderDrawable;
        String emoji;
        int iconColor;

        switch (item.getType()) {
            case Item.TYPE_ALERT:
                borderDrawable = R.drawable.bg_card_border_orange;
                emoji = "⚠";
                iconColor = getColor(R.color.color_accent_orange);
                break;
            case Item.TYPE_SERVICE:
            case Item.TYPE_AID:
                borderDrawable = R.drawable.bg_card_border_cyan;
                emoji = "✚";
                iconColor = getColor(R.color.color_button_cyan);
                break;
            default: // location أو أي شي تاني
                borderDrawable = R.drawable.bg_card_border_grey;
                emoji = "📍";
                iconColor = getColor(R.color.color_text_secondary);
                break;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(borderDrawable);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(14);
        card.setLayoutParams(cardParams);
        card.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textColumn.setLayoutParams(columnParams);

        TextView title = new TextView(this);
        title.setText(item.getTitle());
        title.setTextColor(getColor(R.color.white));
        title.setTextSize(11.5f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);

        TextView subtitle = new TextView(this);
        long minutesAgo = (System.currentTimeMillis() - item.getCreatedAt()) / 1000 / 60;
        String hopsText = item.getHopCount() == 0 ? "بدون قفزات" : ("قفزات: " + item.getHopCount());
        subtitle.setText("منذ " + minutesAgo + " دقيقة · " + hopsText);
        subtitle.setTextColor(getColor(R.color.color_text_secondary));
        subtitle.setTextSize(9.5f);
        subtitle.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dpToPx(3);
        subtitle.setLayoutParams(subtitleParams);

        textColumn.addView(title);
        textColumn.addView(subtitle);

        TextView icon = new TextView(this);
        icon.setText(emoji);
        icon.setTextColor(iconColor);
        icon.setTextSize(14f);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.setMarginStart(dpToPx(8));
        icon.setLayoutParams(iconParams);

        card.addView(textColumn);
        card.addView(icon);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, InfoDetailActivity.class);
            intent.putExtra("item_id", item.getId());
            startActivity(intent);
        });

        return card;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}