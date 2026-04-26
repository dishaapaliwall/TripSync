package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import de.hdodenhof.circleimageview.CircleImageView;

public class TripActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private RecyclerView recyclerView;
    private TripAdapter adapter;
    private List<Trip> tripList;

    private TextView noTripText, welcomeUser;
    private CircleImageView profileImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        recyclerView = findViewById(R.id.recyclerViewTrips);
        noTripText = findViewById(R.id.noTripText);
        welcomeUser = findViewById(R.id.txtUser);
        profileImage = findViewById(R.id.profileImage);

        tripList = new ArrayList<>();
        adapter = new TripAdapter(this, tripList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            repairUserData(user); // 🔥 Ensure name and email are in Firestore
            setupUserProfile(user);
        }

        setupClickListeners();
        loadTrips();
    }

    private void repairUserData(FirebaseUser user) {
        // Ensure email and name are always present in Firestore
        Map<String, Object> data = new HashMap<>();
        data.put("email", user.getEmail().toLowerCase().trim());
        
        // If name is missing, use displayName or email part
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            if (!doc.contains("name") || doc.getString("name") == null || doc.getString("name").isEmpty()) {
                String fallbackName = user.getDisplayName();
                if (fallbackName == null || fallbackName.isEmpty()) {
                    fallbackName = user.getEmail().split("@")[0];
                }
                data.put("name", fallbackName);
                db.collection("users").document(user.getUid()).set(data, SetOptions.merge());
            } else {
                db.collection("users").document(user.getUid()).set(data, SetOptions.merge());
            }
        });
    }

    private void setupUserProfile(FirebaseUser user) {
        // Real-time listener for current user's profile
        db.collection("users").document(user.getUid()).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null || !doc.exists()) return;

            String fullName = doc.getString("name");
            if (fullName == null || fullName.isEmpty()) {
                fullName = user.getDisplayName();
                if (fullName == null || fullName.isEmpty()) fullName = user.getEmail().split("@")[0];
            }
            
            welcomeUser.setText("Welcome " + capitalize(fullName) + "!");

            // 🔥 Use EXACT SAME DETERMINISTIC LOGIC for Avatar as Members section
            int[] avatars = {
                    R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha,
                    R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx
            };
            String uid = user.getUid();
            int avatarIndex = Math.abs(uid.hashCode()) % avatars.length;
            profileImage.setImageResource(avatars[avatarIndex]);
        });
    }

    private void setupClickListeners() {
        findViewById(R.id.navHome).setOnClickListener(v -> {});
        
        findViewById(R.id.navTrips).setOnClickListener(v -> 
            startActivity(new Intent(TripActivity.this, PastTripsActivity.class)));
        
        findViewById(R.id.navNotif).setOnClickListener(v -> 
            startActivity(new Intent(TripActivity.this, NotificationsActivity.class)));
        
        View.OnClickListener toProfile = v -> startActivity(new Intent(TripActivity.this, ProfileActivity.class));
        findViewById(R.id.navProfile).setOnClickListener(toProfile);
        profileImage.setOnClickListener(toProfile);

        findViewById(R.id.btnJoin).setOnClickListener(v -> startActivity(new Intent(TripActivity.this, join_trip.class)));
        findViewById(R.id.btnNew).setOnClickListener(v -> startActivity(new Intent(TripActivity.this, NewTripActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrips();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void loadTrips() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("trips")
                .where(Filter.and(
                        Filter.or(
                                Filter.equalTo("userId", user.getUid()),
                                Filter.arrayContains("participants", user.getEmail().toLowerCase().trim())
                        ),
                        Filter.or(
                                Filter.equalTo("status", "Upcoming"),
                                Filter.equalTo("status", "Ongoing")
                        )
                ))
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    tripList.clear();
                    if (queryDocumentSnapshots.isEmpty()) {
                        noTripText.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        noTripText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Trip trip = doc.toObject(Trip.class);
                            tripList.add(trip);
                        }
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load trips", Toast.LENGTH_SHORT).show());
    }
}