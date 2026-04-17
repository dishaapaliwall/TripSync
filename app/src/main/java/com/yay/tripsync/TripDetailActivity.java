package com.yay.tripsync;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class TripDetailActivity extends AppCompatActivity {

    private ImageView tripImage;
    private TextView tripName, tripLocation, tripDate, tripMembers;
    private TextView tvTotalBudget, tvSpent, tvRemaining;
    private ProgressBar budgetProgress;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔥 Fix: Standard status bar (Black) and app starting below it
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.BLACK);
        // Reset full screen flags if they were set
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

        setContentView(R.layout.activity_trip_detail);

        db = FirebaseFirestore.getInstance();

        // Initialize Views
        ImageView backBtn = findViewById(R.id.backBtn);
        tripImage = findViewById(R.id.tripImage);
        tripName = findViewById(R.id.tripName);
        tripLocation = findViewById(R.id.tripLocation);
        tripDate = findViewById(R.id.tripDate);
        tripMembers = findViewById(R.id.tripMembers);

        tvTotalBudget = findViewById(R.id.tvTotalBudget);
        tvSpent = findViewById(R.id.tvSpent);
        tvRemaining = findViewById(R.id.tvRemaining);
        budgetProgress = findViewById(R.id.budgetProgress);

        backBtn.setOnClickListener(v -> finish());

        // Initial data from Intent
        Intent intent = getIntent();
        String name = intent.getStringExtra("name");
        String location = intent.getStringExtra("location");
        String startDate = intent.getStringExtra("startDate");
        String endDate = intent.getStringExtra("endDate");
        String tripCode = intent.getStringExtra("tripId"); // We passed tripCode as tripId in adapter
        int imageRes = intent.getIntExtra("imageRes", -1);
        String imageUrl = intent.getStringExtra("imageUrl");

        // Set Placeholder data
        tripName.setText(name);
        tripLocation.setText(location);
        tripDate.setText(startDate + " - " + endDate);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).into(tripImage);
        } else if (imageRes != -1) {
            tripImage.setImageResource(imageRes);
        }

        // 🔥 Fetch Latest Data from Firebase
        if (tripCode != null) {
            fetchTripData(tripCode);
        }

        // Setup Tabs
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TripPagerAdapter adapter = new TripPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("Itinerary"); break;
                        case 1: tab.setText("Expenses"); break;
                        case 2: tab.setText("Checklist"); break;
                        case 3: tab.setText("Members"); break;
                        case 4: tab.setText("Photos"); break;
                        case 5: tab.setText("Chat"); break;
                    }
                }
        ).attach();
    }

    private void fetchTripData(String tripCode) {
        db.collection("trips")
                .whereEqualTo("tripCode", tripCode)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null || value.isEmpty()) return;

                    com.google.firebase.firestore.DocumentSnapshot doc = value.getDocuments().get(0);
                    
                    double budget = doc.getDouble("budget") != null ? doc.getDouble("budget") : 0.0;
                    double spent = doc.getDouble("spent") != null ? doc.getDouble("spent") : 0.0;
                    List<String> participants = (List<String>) doc.get("participants");
                    int memberCount = participants != null ? participants.size() : 1;

                    // Update UI
                    tvTotalBudget.setText("Total: ₹ " + (int)budget);
                    tvSpent.setText("Spent: ₹ " + (int)spent);
                    tvRemaining.setText("Remaining: ₹ " + (int)(budget - spent));
                    tripMembers.setText(memberCount + " Members");

                    if (budget > 0) {
                        int progress = (int) ((spent / budget) * 100);
                        budgetProgress.setProgress(progress);
                    } else {
                        budgetProgress.setProgress(0);
                    }
                });
    }
}