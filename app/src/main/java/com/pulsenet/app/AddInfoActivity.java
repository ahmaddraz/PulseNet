package com.pulsenet.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pulsenet.app.data.DatabaseHelper;
import com.pulsenet.app.data.DeviceIdentity;
import com.pulsenet.app.data.Item;

import java.util.UUID;

public class AddInfoActivity extends AppCompatActivity {

    private TextView optionLocation, optionHelp, optionService, optionEmergency;
    private String selectedType = Item.TYPE_ALERT; // نفس الاختيار المفعّل افتراضياً بالتصميم

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_info);

        optionLocation = findViewById(R.id.option_location);
        optionHelp = findViewById(R.id.option_help);
        optionService = findViewById(R.id.option_service);
        optionEmergency = findViewById(R.id.option_emergency);

        optionLocation.setOnClickListener(v -> selectType(Item.TYPE_LOCATION));
        optionHelp.setOnClickListener(v -> selectType(Item.TYPE_AID));
        optionService.setOnClickListener(v -> selectType(Item.TYPE_SERVICE));
        optionEmergency.setOnClickListener(v -> selectType(Item.TYPE_ALERT));

        findViewById(R.id.button_publish).setOnClickListener(v -> publishItem());
    }

    /** لما يدوس المستخدم على أي زر تصنيف، نلوّنه هو بس ونطفي الباقي */
    private void selectType(String type) {
        selectedType = type;

        optionLocation.setBackgroundResource(R.drawable.bg_option_inactive);
        optionHelp.setBackgroundResource(R.drawable.bg_option_inactive);
        optionService.setBackgroundResource(R.drawable.bg_option_inactive);
        optionEmergency.setBackgroundResource(R.drawable.bg_option_inactive);
        optionLocation.setTextColor(getColor(R.color.color_text_secondary));
        optionHelp.setTextColor(getColor(R.color.color_text_secondary));
        optionService.setTextColor(getColor(R.color.color_text_secondary));
        optionEmergency.setTextColor(getColor(R.color.color_text_secondary));

        TextView selected;
        switch (type) {
            case Item.TYPE_LOCATION: selected = optionLocation; break;
            case Item.TYPE_AID: selected = optionHelp; break;
            case Item.TYPE_SERVICE: selected = optionService; break;
            default: selected = optionEmergency; break;
        }
        selected.setBackgroundResource(R.drawable.bg_option_active);
        selected.setTextColor(getColor(R.color.color_background));
    }

    /** يقرأ الحقول، يتحقق منها، ويحفظ المعلومة فعلياً بقاعدة البيانات بالشكل الجديد المطابق للمخطط */
    private void publishItem() {
        EditText inputTitle = findViewById(R.id.input_title);
        EditText inputDetails = findViewById(R.id.input_details);
        EditText inputRegion = findViewById(R.id.input_region);
        EditText inputPriority = findViewById(R.id.input_priority);

        String title = inputTitle.getText().toString().trim();
        String body = inputDetails.getText().toString().trim();
        String area = inputRegion.getText().toString().trim();
        String priority = inputPriority.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "لازم تكتب عنوان المعلومة أول", Toast.LENGTH_SHORT).show();
            return;
        }

        String myDeviceId = DeviceIdentity.getDeviceId(this);

        Item item = new Item(
                UUID.randomUUID().toString(),
                title,
                body,
                selectedType,
                area,
                priority,
                myDeviceId,
                System.currentTimeMillis()
        );

        DatabaseHelper databaseHelper = new DatabaseHelper(this);
        boolean saved = databaseHelper.insertItem(item);

        if (saved) {
            Toast.makeText(this, "تم نشر المعلومة بنجاح", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "صار خطأ، حاول كمان مرة", Toast.LENGTH_SHORT).show();
        }
    }
}