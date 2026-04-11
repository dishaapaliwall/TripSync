package com.yay.tripsync;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class profile extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // EdgeToEdge removed to start app after status bar
        setContentView(R.layout.activity_profile);
    }
}