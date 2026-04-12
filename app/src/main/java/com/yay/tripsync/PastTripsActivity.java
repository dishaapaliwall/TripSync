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

import java.util.ArrayList;
import java.util.List;

public class PastTripsActivity extends AppCompatActivity {

    private RecyclerView rvPastTrips;
    private TextView tvEmpty;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private List<Trip> pastTripList = new ArrayList<>();
    private TripAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_past_trips);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        rvPastTrips = findViewById(R.id.rvPastTrips);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvPastTrips.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(this, pastTripList);
        rvPastTrips.setAdapter(adapter);

        setupBottomNavigation();
        loadPastTrips();
    }

    private void setupBottomNavigation() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, TripActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        
        findViewById(R.id.navTrips).setOnClickListener(v -> {}); // Current page
        
        findViewById(R.id.navNotif).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
            finish();
        });
        
        findViewById(R.id.navProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
    }

    private void loadPastTrips() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("trips")
                .where(Filter.and(
                        Filter.or(
                                Filter.equalTo("userId", user.getUid()),
                                Filter.arrayContains("participants", user.getEmail().toLowerCase().trim())
                        ),
                        Filter.equalTo("status", "Completed")
                ))
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    pastTripList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Trip trip = doc.toObject(Trip.class);
                        pastTripList.add(trip);
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(pastTripList.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}