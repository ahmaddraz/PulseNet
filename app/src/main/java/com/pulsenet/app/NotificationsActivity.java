package com.pulsenet.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pulsenet.app.data.DatabaseHelper;

import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private LinearLayout containerNotifications;
    private TextView textEmptyNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop() + systemBars.top,
                    v.getPaddingRight(), v.getPaddingBottom() + systemBars.bottom);
            return insets;
        });

        databaseHelper = new DatabaseHelper(this);
        containerNotifications = findViewById(R.id.container_notifications);
        textEmptyNotifications = findViewById(R.id.text_empty_notifications);

        findViewById(R.id.button_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSyncLogs();
    }

    /** يقرأ إحصائيات التزامن الحقيقية من جدول sync_logs، ويبني بطاقة لكل عملية تزامن */
    private void loadSyncLogs() {
        containerNotifications.removeAllViews();

        List<Object[]> logs = databaseHelper.getRecentSyncLogs();

        if (logs.isEmpty()) {
            textEmptyNotifications.setVisibility(View.VISIBLE);
            return;
        }
        textEmptyNotifications.setVisibility(View.GONE);

        for (Object[] log : logs) {
            containerNotifications.addView(buildSyncLogCard(log));
        }
    }

    /** ينشئ بطاقة واحدة تعرض إحصائية تزامن واحدة: اسم الجهاز + كم أرسلنا/استقبلنا/تكرر */
    private LinearLayout buildSyncLogCard(Object[] log) {
        String deviceName = (String) log[1];
        int sentCount = (int) log[2];
        int receivedCount = (int) log[3];
        int duplicateCount = (int) log[4];
        long createdAt = (long) log[5];

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_border_grey);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textColumn.setLayoutParams(columnParams);

        TextView titleText = new TextView(this);
        titleText.setText("تزامن مع " + deviceName);
        titleText.setTextColor(getColor(R.color.white));
        titleText.setTextSize(11.5f);
        titleText.setGravity(Gravity.RIGHT);

        TextView statsText = new TextView(this);
        statsText.setText("استقبلنا: " + receivedCount + " · أرسلنا: " + sentCount + " · مكرر: " + duplicateCount);
        statsText.setTextColor(getColor(R.color.color_text_secondary));
        statsText.setTextSize(9.5f);
        statsText.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dpToPx(3);
        statsText.setLayoutParams(statsParams);

        TextView timeText = new TextView(this);
        long minutesAgo = (System.currentTimeMillis() - createdAt) / 1000 / 60;
        timeText.setText(minutesAgo < 1 ? "الآن" : "منذ " + minutesAgo + " دقيقة");
        timeText.setTextColor(getColor(R.color.color_placeholder_text));
        timeText.setTextSize(8.5f);
        timeText.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dpToPx(2);
        timeText.setLayoutParams(timeParams);

        textColumn.addView(titleText);
        textColumn.addView(statsText);
        textColumn.addView(timeText);

        TextView icon = new TextView(this);
        icon.setText(receivedCount > 0 ? "🔔" : "📡");
        icon.setTextSize(16f);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.setMarginStart(dpToPx(8));
        icon.setLayoutParams(iconParams);

        card.addView(textColumn);
        card.addView(icon);

        return card;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
