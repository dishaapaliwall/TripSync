package com.yay.tripsync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        findViewById(R.id.btnDeactivateAccount).setOnClickListener(v -> {
            showSecurityDialog(false);
        });

        findViewById(R.id.btnDeleteAccountPermanent).setOnClickListener(v -> {
            showSecurityDialog(true);
        });
    }

    private void showSecurityDialog(boolean isPermanent) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView btnAction = dialogView.findViewById(R.id.btnDelete);
        ImageView ivIcon = dialogView.findViewById(R.id.ivIcon);

        if (isPermanent) {
            tvTitle.setText("DELETE PERMANENTLY");
            tvMessage.setText("DANGER: This will delete everything forever. Your trips, photos, and messages will be gone. Are you absolutely sure?");
            btnAction.setText("DELETE FOREVER");
            btnAction.setBackgroundResource(R.drawable.dialog_btn_primary); 
            btnAction.setOnClickListener(v -> {
                performPermanentDeletion();
                dialog.dismiss();
            });
        } else {
            tvTitle.setText("Deactivate Account");
            tvMessage.setText("Your trips and data will be saved safely. You can return anytime by simply logging back in.");
            btnAction.setText("Deactivate Now");
            btnAction.setOnClickListener(v -> {
                auth.signOut();
                Toast.makeText(this, "Account deactivated. See you soon!", Toast.LENGTH_SHORT).show();
                navigateToMain();
                dialog.dismiss();
            });
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void performPermanentDeletion() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        String userEmail = user.getEmail() != null ? user.getEmail().toLowerCase().trim() : "";

        Toast.makeText(this, "Cleaning up your data...", Toast.LENGTH_SHORT).show();

        db.collection("trips").get().addOnSuccessListener(queryDocumentSnapshots -> {
            WriteBatch batch = db.batch();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                List<String> participants = (List<String>) doc.get("participants");
                if (participants != null && participants.contains(userEmail)) {
                    batch.update(doc.getReference(), "participants", FieldValue.arrayRemove(userEmail));
                }
                if (userId.equals(doc.getString("userId"))) {
                    batch.delete(doc.getReference());
                }
            }
            batch.delete(db.collection("users").document(userId));

            batch.commit().addOnSuccessListener(aVoid -> {
                SharedPreferences prefs = getSharedPreferences("TripSyncAccounts", MODE_PRIVATE);
                Set<String> savedEmails = new HashSet<>(prefs.getStringSet("saved_emails", new HashSet<>()));
                savedEmails.remove(userEmail);
                prefs.edit()
                        .putStringSet("saved_emails", savedEmails)
                        .remove("pwd_" + userEmail)
                        .apply();

                user.delete().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account deleted permanently.", Toast.LENGTH_LONG).show();
                        navigateToMain();
                    } else {
                        Toast.makeText(this, "Please re-authenticate to verify it's you, then try deleting again.", Toast.LENGTH_LONG).show();
                        auth.signOut();
                        navigateToMain();
                    }
                });
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Error deleting data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(SecurityPrivacyActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
