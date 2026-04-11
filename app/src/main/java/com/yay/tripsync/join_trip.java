package com.yay.tripsync;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class join_trip extends AppCompatActivity {

    private EditText etCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_trip);

        etCode = findViewById(R.id.etCode);

        // 🔙 BACK BUTTON
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 🔘 JOIN BUTTON
        findViewById(R.id.btnJoin).setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter a trip code", Toast.LENGTH_SHORT).show();
            } else {
                // Handle join trip logic here
                Toast.makeText(this, "Joining trip: " + code, Toast.LENGTH_SHORT).show();
            }
        });
    }
}