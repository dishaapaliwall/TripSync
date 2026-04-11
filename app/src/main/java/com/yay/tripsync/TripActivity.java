package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Random;

import de.hdodenhof.circleimageview.CircleImageView;

public class TripActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private LinearLayout tripContainer;
    private TextView noTripText, welcomeUser;
    private ScrollView tripScroll;
    private CircleImageView profileImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // 🔥 VIEWS
        tripContainer = findViewById(R.id.tripContainer);
        noTripText = findViewById(R.id.noTripText);
        welcomeUser = findViewById(R.id.txtUser);
        tripScroll = findViewById(R.id.tripScroll);
        profileImage = findViewById(R.id.profileImage);

        // 🔥 USER DATA
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            // Persistent Avatar: Use UID as seed so it's the same every time for this user
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

            // Set User Name
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                welcomeUser.setText("Welcome " + capitalize(displayName) + "!");
            } else if (user.getEmail() != null) {
                String name = user.getEmail().split("@")[0];
                welcomeUser.setText("Welcome " + capitalize(name) + "!");
            }
        }

        // 🔥 NAVIGATION
        findViewById(R.id.navHome).setOnClickListener(v -> {
            // already here
        });

        findViewById(R.id.navTrips).setOnClickListener(v ->
                Toast.makeText(this, "You are on Trips screen", Toast.LENGTH_SHORT).show());

        findViewById(R.id.navNotif).setOnClickListener(v ->
                Toast.makeText(this, "Notifications coming soon!", Toast.LENGTH_SHORT).show());

        findViewById(R.id.navProfile).setOnClickListener(v ->
                Toast.makeText(this, "Profile screen coming soon!", Toast.LENGTH_SHORT).show());

        // 🔥 BUTTONS
        findViewById(R.id.btnJoin).setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, join_trip.class);
            startActivity(intent);
        });

        findViewById(R.id.btnNew).setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, NewTripActivity.class);
            startActivity(intent);
        });

        loadTrips();
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
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void loadTrips() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("trips")
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    tripContainer.removeAllViews();

                    if (queryDocumentSnapshots.isEmpty()) {
                        noTripText.setVisibility(View.VISIBLE);
                        tripScroll.setVisibility(View.GONE);
                        return;
                    }

                    noTripText.setVisibility(View.GONE);
                    tripScroll.setVisibility(View.VISIBLE);

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        String name = doc.getString("name");
                        String location = doc.getString("location");
                        String date = doc.getString("date");
                        String status = doc.getString("status");

                        View card = getLayoutInflater().inflate(R.layout.card_trip, null);

                        TextView tName = card.findViewById(R.id.tripName);
                        TextView tLoc = card.findViewById(R.id.tripLocation);
                        TextView tDate = card.findViewById(R.id.tripDate);
                        TextView tStatus = card.findViewById(R.id.tripStatus);

                        tName.setText(name);
                        tLoc.setText(location);
                        tDate.setText(date);
                        tStatus.setText(status);

                        tripContainer.addView(card);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load trips", Toast.LENGTH_SHORT).show());
    }
}