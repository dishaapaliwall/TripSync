package com.yay.tripsync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPersonalInfo).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, PersonalInformationActivity.class));
        });

        findViewById(R.id.btnSecurityPrivacy).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, SecurityPrivacyActivity.class));
        });

        // 🔥 Country/Region - Ab ye sahi screen open karega
        findViewById(R.id.btnCountryRegion).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, CountryRegionActivity.class));
        });

        findViewById(R.id.btnAccountManagement).setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("TripSyncAccounts", MODE_PRIVATE);
            Set<String> savedEmailsSet = prefs.getStringSet("saved_emails", new HashSet<>());
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String currentEmail = currentUser != null ? currentUser.getEmail() : "";

            List<String> options = new ArrayList<>(savedEmailsSet);
            options.add("+ Add New Account");

            String[] items = options.toArray(new String[0]);

            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Switch or Add Account")
                    .setItems(items, (dialog, which) -> {
                        String selected = items[which];
                        if (selected.equals("+ Add New Account")) {
                            Intent intent = new Intent(this, MainActivity.class);
                            startActivity(intent);
                        } else if (selected.equals(currentEmail)) {
                            Toast.makeText(this, "Already logged in as " + selected, Toast.LENGTH_SHORT).show();
                        } else {
                            FirebaseAuth.getInstance().signOut();
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.putExtra("switch_email", selected);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }
                    })
                    .show();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                String email = user.getEmail().toLowerCase().trim();
                SharedPreferences prefs = getSharedPreferences("TripSyncAccounts", MODE_PRIVATE);
                
                // 🔥 Remove account from saved list COMPLETELY
                Set<String> savedEmails = new HashSet<>(prefs.getStringSet("saved_emails", new HashSet<>()));
                savedEmails.remove(email);
                
                prefs.edit()
                        .putStringSet("saved_emails", savedEmails)
                        .remove("pwd_" + email)
                        .apply();
            }

            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}