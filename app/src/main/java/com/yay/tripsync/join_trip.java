package com.yay.tripsync;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class join_trip extends AppCompatActivity {

    private EditText etCode;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_trip);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        etCode = findViewById(R.id.etCode);

        // 🔙 BACK BUTTON
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 🔘 JOIN BUTTON
        findViewById(R.id.btnJoin).setOnClickListener(v -> {
            String code = etCode.getText().toString().trim().toUpperCase();
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter a trip code", Toast.LENGTH_SHORT).show();
            } else {
                handleJoinTrip(code);
            }
        });
    }

    private void handleJoinTrip(String code) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // 1. Find the trip with this code
        db.collection("trips")
                .whereEqualTo("tripCode", code)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "Invalid Trip Code", Toast.LENGTH_SHORT).show();
                    } else {
                        // 2. Add current user to the trip
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String tripId = doc.getId();
                            
                            // Check if user is already the owner
                            String ownerId = doc.getString("userId");
                            if (user.getUid().equals(ownerId)) {
                                Toast.makeText(this, "You are already in this trip!", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Add user ID to a new 'members' array in the trip document
                            db.collection("trips").document(tripId)
                                    .update("members", FieldValue.arrayUnion(user.getUid()))
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Successfully joined trip!", Toast.LENGTH_SHORT).show();
                                        finish(); // Go back to main screen
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to join: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}