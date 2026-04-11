package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

        // 🔥 VIEWS
        recyclerView = findViewById(R.id.recyclerViewTrips);
        noTripText = findViewById(R.id.noTripText);
        welcomeUser = findViewById(R.id.txtUser);
        profileImage = findViewById(R.id.profileImage);

        // 🔥 SETUP RECYCLERVIEW
        tripList = new ArrayList<>();
        adapter = new TripAdapter(this, tripList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 🔥 USER DATA
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            setupUserProfile(user);
        }

        // 🔥 NAVIGATION & BUTTONS
        setupClickListeners();

        loadTrips();
    }

    private void setupUserProfile(FirebaseUser user) {
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

        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            welcomeUser.setText("Welcome " + capitalize(displayName) + "!");
        } else if (user.getEmail() != null) {
            String name = user.getEmail().split("@")[0];
            welcomeUser.setText("Welcome " + capitalize(name) + "!");
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.navHome).setOnClickListener(v -> {});
        findViewById(R.id.navTrips).setOnClickListener(v -> Toast.makeText(this, "You are on Trips screen", Toast.LENGTH_SHORT).show());
        findViewById(R.id.navNotif).setOnClickListener(v -> Toast.makeText(this, "Notifications coming soon!", Toast.LENGTH_SHORT).show());
        
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
                .where(Filter.or(
                        Filter.equalTo("userId", user.getUid()),
                        Filter.arrayContains("members", user.getUid())
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
                            Trip trip = new Trip();
                            trip.setName(doc.getString("name"));
                            trip.setLocation(doc.getString("location"));
                            trip.setStartDate(doc.getString("startDate"));
                            trip.setEndDate(doc.getString("endDate"));
                            trip.setStatus(doc.getString("status"));
                            trip.setTripCode(doc.getString("tripCode"));
                            
                            Double budget = doc.getDouble("budget");
                            trip.setBudget(budget != null ? budget : 0.0);
                            
                            Double spent = doc.getDouble("spent");
                            trip.setSpent(spent != null ? spent : 0.0);
                            
                            trip.setImageUrl(doc.getString("imageUrl"));

                            tripList.add(trip);
                        }

                        // 🔥 Sort trips: Latest (Upcoming) first, Oldest (Completed) last
                        Collections.sort(tripList, (t1, t2) -> {
                            SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                            try {
                                Date d1 = sdf.parse(t1.getStartDate().replace(".", "/"));
                                Date d2 = sdf.parse(t2.getStartDate().replace(".", "/"));
                                if (d1 == null || d2 == null) return 0;
                                // Changed to d2.compareTo(d1) for Descending order (Future first)
                                return d2.compareTo(d1);
                            } catch (ParseException | NullPointerException e) {
                                return 0;
                            }
                        });

                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load trips", Toast.LENGTH_SHORT).show());
    }
}