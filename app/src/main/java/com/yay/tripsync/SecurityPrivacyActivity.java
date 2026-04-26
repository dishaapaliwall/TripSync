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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityPrivacyActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_privacy);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        SwitchCompat switchPrivateProfile = findViewById(R.id.switchPrivateProfile);
        switchPrivateProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, "Profile is now Private", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Profile is now Public", Toast.LENGTH_SHORT).show();
            }
            // Update Firestore preference
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                db.collection("users").document(user.getUid()).update("isPrivate", isChecked);
            }
        });

        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> {
            showDeleteConfirmation();
        });
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? You will be hidden from searches and removed from all trips. Your data will be permanently deleted after 30 days. You can cancel this by logging back in before then.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    performAccountDeletion();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performAccountDeletion() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        String userEmail = user.getEmail().toLowerCase().trim();
        long deletionTimestamp = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000); // 30 days from now

        // 🔥 1. Mark user as deleted in Firestore
        Map<String, Object> deletionData = new HashMap<>();
        deletionData.put("isDeleted", true);
        deletionData.put("deletionScheduledAt", System.currentTimeMillis());
        deletionData.put("permanentDeletionAt", deletionTimestamp);

        db.collection("users").document(userId).update(deletionData)
                .addOnSuccessListener(aVoid -> {
                    // 🔥 2. Remove user from all trips
                    removeFromAllTrips(userId, userEmail);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to schedule deletion: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void removeFromAllTrips(String userId, String userEmail) {
        // Query trips where user is owner or participant
        db.collection("trips").get().addOnSuccessListener(queryDocumentSnapshots -> {
            WriteBatch batch = db.batch();
            boolean foundAny = false;

            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                boolean modified = false;
                
                // If owner, maybe delete the trip or assign to someone else? 
                // For now, let's just mark the user email as removed from participants.
                List<String> participants = (List<String>) doc.get("participants");
                if (participants != null && participants.contains(userEmail)) {
                    batch.update(doc.getReference(), "participants", FieldValue.arrayRemove(userEmail));
                    modified = true;
                }

                // If user is owner
                if (userId.equals(doc.getString("userId"))) {
                    // Option: delete trip or mark owner as deleted
                    batch.update(doc.getReference(), "ownerDeleted", true);
                    modified = true;
                }

                if (modified) foundAny = true;
            }

            if (foundAny) {
                batch.commit().addOnCompleteListener(task -> finalizeDeletionProcess());
            } else {
                finalizeDeletionProcess();
            }
        });
    }

    private void finalizeDeletionProcess() {
        Toast.makeText(this, "Account scheduled for deletion. You have 30 days to recover it.", Toast.LENGTH_LONG).show();
        auth.signOut();
        Intent intent = new Intent(SecurityPrivacyActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
