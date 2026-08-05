package com.pulsenet.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class Onboarding3Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding3);

        View buttonStart = findViewById(R.id.button_start);
        buttonStart.setOnClickListener(v -> {
            // سجّل إنه المستخدم خلص شاشات التعريف، حتى ما تظهرله كمان
            SharedPreferences prefs = getSharedPreferences("pulsenet_prefs", MODE_PRIVATE);
            prefs.edit().putBoolean("onboarding_completed", true).apply();

            startActivity(new Intent(Onboarding3Activity.this, MainActivity.class));
            finish();
        });
    }
}
