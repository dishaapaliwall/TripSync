package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Random;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private TextView txtName, txtEmail, txtUpcomingCount, txtCompletedCount;
    private CircleImageView profileImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        profileImage = findViewById(R.id.profileImage);
        
        // Find stats text views (Assumes you have these IDs or I'll add them to layout if needed, 
        // but looking at your layout, the count is the first child TextView in those stat layouts)
        // I will update the layout to have specific IDs for these counts for easier access.
        txtUpcomingCount = findViewById(R.id.txtUpcomingCount);
        txtCompletedCount = findViewById(R.id.txtCompletedCount);

        // 🔥 BACK BUTTON
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 🔥 SET USER DATA
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            String email = user.getEmail();

            if (name != null && !name.isEmpty()) {
                txtName.setText(capitalize(name));
            } else {
                txtName.setText("User Name");
            }

            if (email != null) {
                txtEmail.setText(email);
            }

            // Persistent Avatar: Use UID as seed (consistent with TripActivity)
            int[] avatars = {
                    R.drawable.panda,
                    R.drawable.jaguar,
                    R.drawable.ganesha,
                    R.drawable.cow,
                    R.drawable.cat,
                    R.drawable.bird,
                    R.drawable.apteryx
            };

            long seed = user.getUid().hashCode();
            int avatarIndex = new Random(seed).nextInt(avatars.length);
            profileImage.setImageResource(avatars[avatarIndex]);

            // 🔥 FETCH STATS FROM FIRESTORE
            fetchTripStats(user.getUid());
        }

        // 🔥 NAV ACTIONS
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, TripActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navTrips).setOnClickListener(v ->
                Toast.makeText(this, "Past Trips coming soon!", Toast.LENGTH_SHORT).show());

        findViewById(R.id.navNotif).setOnClickListener(v ->
                Toast.makeText(this, "Notifications screen coming soon!", Toast.LENGTH_SHORT).show());

        findViewById(R.id.navProfile).setOnClickListener(v -> {
            // Already here
        });
    }

    private void fetchTripStats(String userId) {
        // Count Upcoming trips
        db.collection("trips")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "Upcoming")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (txtUpcomingCount != null) {
                        txtUpcomingCount.setText(String.valueOf(queryDocumentSnapshots.size()));
                    }
                });

        // Count Completed trips
        db.collection("trips")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "Completed")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (txtCompletedCount != null) {
                        txtCompletedCount.setText(String.valueOf(queryDocumentSnapshots.size()));
                    }
                });
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}