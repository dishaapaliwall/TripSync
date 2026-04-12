package com.yay.tripsync;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class FriendsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}