package com.pulsenet.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import androidx.lifecycle.ViewModelProvider;

import com.pulsenet.app.ble.PermissionHelper;
import com.pulsenet.app.data.Item;
import com.pulsenet.app.service.PulseNetService;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private MainViewModel viewModel;
    private TextView textDeviceCount;
    private TextView textEmptyItems;
    private LinearLayout containerItems;

    // معالج تفعيل البلوتوث
    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    startPulseNetService();
                } else {
                    Toast.makeText(this, "يجب تفعيل البلوتوث لعمل تطبيق PulseNet", Toast.LENGTH_LONG).show();
                }
            });

    // معالج طلب الصلاحيات
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
                    Toast.makeText(this, "التطبيق يحتاج لصلاحيات البلوتوث والموقع لاكتشاف الأجهزة", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // إعداد الحواف (Edge-to-Edge)
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), systemBars.bottom);
                return insets;
            });
        }

        // ربط العناصر
        textDeviceCount = findViewById(R.id.text_device_count);
        textEmptyItems = findViewById(R.id.text_empty_items);
        containerItems = findViewById(R.id.container_items);

        // 1. تهيئة الـ ViewModel (المسؤول عن البيانات)
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // 2. مراقبة البيانات (التحديث التلقائي اللحظي)
        viewModel.getItems().observe(this, this::displayItems);

        viewModel.getDeviceCount().observe(this, count -> {
            if (textDeviceCount != null) {
                textDeviceCount.setText(String.format("%d أجهزة متصلة الآن", count));
            }
        });

        // إعداد البلوتوث
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        // إعداد الأزرار
        setupButtons();

        // بدء طلب الصلاحيات وتشغيل الخدمة
        requestPermissionsThenStart();
    }

    private void setupButtons() {
        View btnAdd = findViewById(R.id.button_add);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> startActivity(new Intent(this, AddInfoActivity.class)));

        View btnSettings = findViewById(R.id.button_settings);
        if (btnSettings != null) btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        View btnNotif = findViewById(R.id.button_notifications);
        if (btnNotif != null) btnNotif.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
    }

    private void requestPermissionsThenStart() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "هذا الجهاز لا يدعم البلوتوث", Toast.LENGTH_LONG).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        // تحديث البيانات عند العودة للتطبيق لضمان المزامنة
        viewModel.refreshData();
    }

    private void displayItems(List<Item> items) {
        if (containerItems == null) return;

        containerItems.removeAllViews();
        if (items == null || items.isEmpty()) {
            textEmptyItems.setVisibility(View.VISIBLE);
            return;
        }

        textEmptyItems.setVisibility(View.GONE);
        for (Item item : items) {
            if (item != null) {
                containerItems.addView(buildItemCard(item));
            }
        }
    }

    private View buildItemCard(Item item) {
        int borderDrawable;
        int iconDrawable;
        int iconChipBg;

        // تحديد التصميم بناءً على نوع العنصر
        switch (item.getType()) {
            case Item.TYPE_ALERT:
                borderDrawable = R.drawable.bg_card_border_orange;
                iconDrawable = R.drawable.ic_alert;
                iconChipBg = R.drawable.bg_icon_circle_orange;
                break;
            case Item.TYPE_AID:
                borderDrawable = R.drawable.bg_card_border_cyan;
                iconDrawable = R.drawable.ic_aid;
                iconChipBg = R.drawable.bg_icon_circle_cyan;
                break;
            default:
                borderDrawable = R.drawable.bg_card_border_grey;
                iconDrawable = R.drawable.ic_location;
                iconChipBg = R.drawable.bg_icon_circle_grey;
                break;
        }

        // بناء الـ Card الرئيسي
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(borderDrawable);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(12);
        card.setLayoutParams(params);
        card.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        // عمود النصوص (العنوان والوقت)
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.END);
        textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(item.getTitle());
        title.setTextColor(ContextCompat.getColor(this, R.color.white));
        title.setTextSize(14f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.END);

        TextView subtitle = new TextView(this);
        long diff = System.currentTimeMillis() - item.getCreatedAt();
        long minutes = Math.max(0, diff / 60000);
        String info = "منذ " + minutes + " د · " + (item.getHopCount() == 0 ? "مباشر" : item.getHopCount() + " قفزات");
        subtitle.setText(info);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.color_text_secondary));
        subtitle.setTextSize(11f);
        subtitle.setGravity(Gravity.END);

        textColumn.addView(title);
        textColumn.addView(subtitle);

        // أيقونة الحالة
        FrameLayout iconChip = new FrameLayout(this);
        iconChip.setBackgroundResource(iconChipBg);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        chipParams.setMarginStart(dpToPx(10));
        iconChip.setLayoutParams(chipParams);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconDrawable);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dpToPx(18), dpToPx(18));
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        iconChip.addView(icon);

        card.addView(textColumn);
        card.addView(iconChip);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, InfoDetailActivity.class);
            intent.putExtra("item_id", item.getId());
            startActivity(intent);
        });

        return card;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
