package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Filter;
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
        
        txtUpcomingCount = findViewById(R.id.txtUpcomingCount);
        txtCompletedCount = findViewById(R.id.txtCompletedCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 🔥 OPTION BUTTONS
        findViewById(R.id.btnSettings).setOnClickListener(v -> 
                startActivity(new Intent(this, SettingsActivity.class)));
        
        findViewById(R.id.btnFriends).setOnClickListener(v -> 
                startActivity(new Intent(this, FriendsActivity.class)));
        
        findViewById(R.id.btnHelp).setOnClickListener(v -> 
                startActivity(new Intent(this, HelpSupportActivity.class)));

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            setupUserProfile(user);
            fetchTripStats(user);
        }

        // 🔥 BOTTOM NAVIGATION
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, TripActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navTrips).setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, PastTripsActivity.class));
            finish();
        });

        findViewById(R.id.navNotif).setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, NotificationsActivity.class));
            finish();
        });

        findViewById(R.id.navProfile).setOnClickListener(v -> {});
    }

    private void setupUserProfile(FirebaseUser user) {
        String name = user.getDisplayName();
        String email = user.getEmail();

        if (name != null && !name.isEmpty()) {
            txtName.setText(capitalize(name));
        } else if (email != null) {
            txtName.setText(capitalize(email.split("@")[0]));
        }

        if (email != null) {
            txtEmail.setText(email);
        }

        int[] avatars = {
                R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha,
                R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx
        };

        long seed = user.getUid().hashCode();
        Random random = new Random(seed);
        random.nextInt();
        random.nextInt();
        int avatarIndex = random.nextInt(avatars.length);
        profileImage.setImageResource(avatars[avatarIndex]);
    }

    private void fetchTripStats(FirebaseUser user) {
        String userId = user.getUid();
        String userEmail = user.getEmail() != null ? user.getEmail().toLowerCase().trim() : "";

        db.collection("trips")
                .where(Filter.or(
                        Filter.equalTo("userId", userId),
                        Filter.arrayContains("participants", userEmail)
                ))
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("ProfileStats", "Listen failed: " + error.getMessage());
                        return;
                    }

                    int upcoming = 0;
                    int completed = 0;

                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            String status = doc.getString("status");
                            if (status != null) {
                                String cleanStatus = status.trim();
                                // 🔥 Strictly counting only "Upcoming" trips now
                                if (cleanStatus.equalsIgnoreCase("Upcoming")) {
                                    upcoming++;
                                } else if (cleanStatus.equalsIgnoreCase("Completed")) {
                                    completed++;
                                }
                            }
                        }
                    }

                    txtUpcomingCount.setText(String.valueOf(upcoming));
                    txtCompletedCount.setText(String.valueOf(completed));
                    
                    Log.d("ProfileStats", "Updated counts - Upcoming: " + upcoming + ", Completed: " + completed);
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