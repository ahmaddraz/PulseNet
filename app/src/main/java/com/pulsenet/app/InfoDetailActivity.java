package com.pulsenet.app;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.Item;

public class InfoDetailActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private String itemId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop() + systemBars.top,
                    v.getPaddingRight(), v.getPaddingBottom() + systemBars.bottom);
            return insets;
        });

        databaseHelper = new DatabaseHelper(this);
        itemId = getIntent().getStringExtra("item_id"); // نص UUID، مش رقم

        findViewById(R.id.button_back).setOnClickListener(v -> finish());

        loadItemDetails();

        findViewById(R.id.button_mark_read).setOnClickListener(v -> {
            databaseHelper.markItemAsRead(itemId);
            databaseHelper.hideItemLocally(itemId);
            Toast.makeText(this, "تم إخفاؤها من شاشتك (بس رح تضل تنتقل لأجهزة تانية)", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    /** يقرأ المعلومة المحددة (حسب item_id) من القاعدة، ويعرض بياناتها الحقيقية */
    private void loadItemDetails() {
        if (itemId == null) {
            Toast.makeText(this, "ما قدرنا نلاقي هاي المعلومة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Item item = databaseHelper.getItemById(itemId);

        if (item == null) {
            Toast.makeText(this, "ما قدرنا نلاقي هاي المعلومة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView textCategory = findViewById(R.id.text_category);
        TextView textTitle = findViewById(R.id.text_title);
        TextView textDetails = findViewById(R.id.text_details);
        TextView textRegion = findViewById(R.id.text_region);
        TextView textTime = findViewById(R.id.text_time);
        TextView textHops = findViewById(R.id.text_hops);

        // نترجم التصنيف الإنجليزي المخزّن (alert/service/aid/location) لعربي بالعرض
        textCategory.setText(Item.typeToArabicCategory(item.getType()));
        textTitle.setText(item.getTitle());

        String body = item.getBody();
        textDetails.setText(body == null || body.isEmpty() ? "بدون تفاصيل إضافية" : body);

        String area = item.getArea();
        textRegion.setText("📍 " + (area == null || area.isEmpty() ? "منطقة غير محددة" : area));

        long minutesAgo = (System.currentTimeMillis() - item.getCreatedAt()) / 1000 / 60;
        textTime.setText("🕒 منذ " + minutesAgo + " دقيقة");

        int hops = item.getHopCount();
        textHops.setText(hops == 0 ? "🔀 معلومة من عندك (بدون قفزات)" : "🔀 انتقلت عبر " + hops + " قفزة");
    }
}