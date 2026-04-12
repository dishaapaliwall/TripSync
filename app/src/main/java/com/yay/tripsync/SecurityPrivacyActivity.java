package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SecurityPrivacyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_privacy);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        SwitchCompat switchPrivateProfile = findViewById(R.id.switchPrivateProfile);
        switchPrivateProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, "Profile is now Private", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Profile is now Public", Toast.LENGTH_SHORT).show();
            }
            // Yaha aap Firestore me preference save kar sakte hain
        });

        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> {
            showDeleteConfirmation();
        });
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? Your data will be permanently deleted after 30 days. You can cancel this by logging back in before then.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    performAccountDeletion();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performAccountDeletion() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Toast.makeText(this, "Account scheduled for deletion. You have 30 days to recover it.", Toast.LENGTH_LONG).show();
            
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(SecurityPrivacyActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
        }
    }
}