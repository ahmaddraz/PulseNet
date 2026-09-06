package com.pulsenet.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pulsenet.app.data.DeviceIdentity;

public class Onboarding3Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding3);

        View buttonStart = findViewById(R.id.button_start);
        buttonStart.setOnClickListener(v -> showChooseNameDialog());
    }

    /**
     * قبل ما نكمل لأول مرة، منطلب من المستخدم يختار اسمه بنفسه - متل فكرة
     * تطبيق Bitchat - بدل ما نعطيه اسم عشوائي (PulseNet-XXXX) ما بيقدر
     * يتذكره أو يعيد اختياره بنفسه لاحقاً (مثلاً لو أعاد تثبيت التطبيق).
     * الحقل معبّى مسبقاً بالاسم التلقائي، فالمستخدم يقدر يكمل بدون ما
     * يغيّر شي إذا حاب.
     */
    private void showChooseNameDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("مثلاً: أحمد");
        input.setText(DeviceIdentity.getDeviceName(this));
        input.setSelection(input.getText().length());
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DeviceIdentity.MAX_NAME_LENGTH)});

        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        input.setPadding(padding, padding / 2, padding, padding / 2);

        new AlertDialog.Builder(this)
                .setTitle("شو اسمك؟")
                .setMessage("هاد الاسم رح يشوفه الأشخاص التانين لما تتبادلوا معلومات عبر PulseNet. تقدر تغيّره لاحقاً من الإعدادات.")
                .setCancelable(false)
                .setView(input)
                .setPositiveButton("ابدأ", (dialog, which) -> {
                    String name = input.getText().toString();
                    if (!name.trim().isEmpty()) {
                        DeviceIdentity.setDeviceName(this, name);
                    }
                    finishOnboarding();
                })
                .show();
    }

    private void finishOnboarding() {
        // سجّل إنه المستخدم خلص شاشات التعريف، حتى ما تظهرله كمان
        SharedPreferences prefs = getSharedPreferences("pulsenet_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarding_completed", true).apply();

        startActivity(new Intent(Onboarding3Activity.this, MainActivity.class));
        finish();
    }
}
