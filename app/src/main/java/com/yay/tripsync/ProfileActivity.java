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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private TextView txtName, txtEmail, txtUpcomingCount, txtCompletedCount;
    private CircleImageView profileImage;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());

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
        txtEmail.setText(user.getEmail());

        // 🔥 Real-time listener for current user's profile to keep name and avatar consistent
        db.collection("users").document(user.getUid()).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null || !doc.exists()) {
                String name = user.getDisplayName();
                if (name == null || name.isEmpty()) name = user.getEmail().split("@")[0];
                txtName.setText(capitalize(name));
                return;
            }

            String fullName = doc.getString("name");
            if (fullName == null || fullName.isEmpty()) fullName = user.getDisplayName();
            if (fullName == null || fullName.isEmpty()) fullName = user.getEmail().split("@")[0];
            
            txtName.setText(capitalize(fullName));

            // 🔥 CONSISTENT AVATAR LOGIC (Same as TripActivity and MembersFragment)
            int[] avatars = {
                    R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha,
                    R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx
            };
            String uid = user.getUid();
            int avatarIndex = Math.abs(uid.hashCode()) % avatars.length;
            profileImage.setImageResource(avatars[avatarIndex]);
        });
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
                        Date today = Calendar.getInstance().getTime();
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(today);
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        today = cal.getTime();

                        for (DocumentSnapshot doc : value.getDocuments()) {
                            // 🔥 Check if trip is hidden by current user
                            List<String> hiddenBy = (List<String>) doc.get("hiddenBy");
                            if (hiddenBy != null && hiddenBy.contains(userId)) {
                                continue; // Skip hidden trips
                            }

                            String startDateStr = doc.getString("startDate");
                            String endDateStr = doc.getString("endDate");

                            if (startDateStr != null && endDateStr != null) {
                                try {
                                    Date startDate = dateFormat.parse(startDateStr);
                                    Date endDate = dateFormat.parse(endDateStr);

                                    if (today.before(startDate)) {
                                        upcoming++;
                                    } else if (today.after(endDate)) {
                                        completed++;
                                        
                                        // Update status to Completed in backend if it wasn't already
                                        String currentStatus = doc.getString("status");
                                        if (currentStatus == null || !currentStatus.equalsIgnoreCase("Completed")) {
                                            doc.getReference().update("status", "Completed");
                                        }
                                    }
                                } catch (ParseException e) {
                                    Log.e("ProfileStats", "Date parse error: " + e.getMessage());
                                }
                            }
                        }
                    }

                    txtUpcomingCount.setText(String.valueOf(upcoming));
                    txtCompletedCount.setText(String.valueOf(completed));
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