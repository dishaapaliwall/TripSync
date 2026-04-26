package com.yay.tripsync;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class HelpSupportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        ImageView btnBack = findViewById(R.id.btnBack);
        Button btnEmail = findViewById(R.id.btnEmailSupport);
        Button btnCall = findViewById(R.id.btnCallSupport);

        btnBack.setOnClickListener(v -> finish());

        // 🔥 EMAIL SUPPORT
        btnEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@tripsync.com")); // Replace with actual owner email
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request - TripSync");
            startActivity(Intent.createChooser(intent, "Send Email..."));
        });

        // 🔥 CALL HELPLINE
        btnCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:+919034000000")); // Replace with actual number
            startActivity(intent);
        });
    }
}