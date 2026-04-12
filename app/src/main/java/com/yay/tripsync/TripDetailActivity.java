package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class TripDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trip_detail);
        ImageView backBtn = findViewById(R.id.backBtn);

        backBtn.setOnClickListener(v -> {
            finish();
        });
        ImageView tripImage = findViewById(R.id.tripImage);

        int imageRes = getIntent().getIntExtra("imageRes", -1);
        String imageUrl = getIntent().getStringExtra("imageUrl");

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).into(tripImage);
        } else if (imageRes != -1) {
            tripImage.setImageResource(imageRes);
        }

        // Get all views
        TextView tripName = findViewById(R.id.tripName);
        TextView tripLocation = findViewById(R.id.tripLocation);
        TextView tripDate = findViewById(R.id.tripDate);
        TextView tripMembers = findViewById(R.id.tripMembers); // optional if you add later

        // Receive data from Intent
        Intent intent = getIntent();
        String name = intent.getStringExtra("name");
        String location = intent.getStringExtra("location");
        String startDate = intent.getStringExtra("startDate");
        String endDate = intent.getStringExtra("endDate");

        //  Set data to UI
        tripName.setText(name);
        tripLocation.setText(location);
        tripDate.setText(startDate + " - " + endDate);

        // Set Image
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).into(tripImage);
        } else if (imageRes != -1) {
            tripImage.setImageResource(imageRes);
        }



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
}