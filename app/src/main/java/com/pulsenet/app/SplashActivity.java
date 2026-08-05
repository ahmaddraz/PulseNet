package com.pulsenet.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("pulsenet_prefs", MODE_PRIVATE);
            boolean onboardingCompleted = prefs.getBoolean("onboarding_completed", false);

            Class<?> nextScreen = onboardingCompleted ? MainActivity.class : Onboarding1Activity.class;
            startActivity(new Intent(SplashActivity.this, nextScreen));
            finish();
        }, 2000);
    }
}