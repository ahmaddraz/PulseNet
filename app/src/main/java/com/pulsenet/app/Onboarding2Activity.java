package com.pulsenet.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class Onboarding2Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding2);

        View buttonNext = findViewById(R.id.button_next);
        buttonNext.setOnClickListener(v -> {
            startActivity(new Intent(Onboarding2Activity.this, Onboarding3Activity.class));
        });
    }
}
