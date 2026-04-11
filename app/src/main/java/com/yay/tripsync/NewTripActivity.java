package com.yay.tripsync;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;

public class NewTripActivity extends AppCompatActivity {

    EditText etName, etLocation, etStart, etEnd, etBudget, etInvite;

    FirebaseFirestore db;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_trip);

        // 🔥 INIT
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.etName);
        etLocation = findViewById(R.id.etLocation);
        etStart = findViewById(R.id.etStart);
        etEnd = findViewById(R.id.etEnd);
        etBudget = findViewById(R.id.etBudget);
        etInvite = findViewById(R.id.etInvite);

        // 🔙 BACK
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 🔥 CREATE BUTTON
        findViewById(R.id.btnCreate).setOnClickListener(v -> createTrip());
    }

    private void createTrip() {

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String start = etStart.getText().toString().trim();
        String end = etEnd.getText().toString().trim();
        String budget = etBudget.getText().toString().trim();

        // 🔥 VALIDATION
        if (name.isEmpty() || location.isEmpty() || start.isEmpty() || end.isEmpty() || budget.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔥 FIREBASE DATA
        HashMap<String, Object> trip = new HashMap<>();
        trip.put("name", name);
        trip.put("location", location);
        trip.put("date", start + " - " + end);
        trip.put("status", "Upcoming");
        trip.put("budget", budget);
        trip.put("spent", "0");
        trip.put("userId", user.getUid());

        // 🔥 SAVE TO FIRESTORE
        db.collection("trips")
                .add(trip)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Trip Created!", Toast.LENGTH_SHORT).show();
                    finish(); // back to previous activity
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}