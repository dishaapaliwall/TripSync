package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private List<QueryDocumentSnapshot> invitationList = new ArrayList<>();
    private NotifAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotifAdapter();
        rvNotifications.setAdapter(adapter);

        setupBottomNavigation();
        loadInvitations();
    }

    private void setupBottomNavigation() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, TripActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        findViewById(R.id.navTrips).setOnClickListener(v -> {
            startActivity(new Intent(this, PastTripsActivity.class));
            finish();
        });
        findViewById(R.id.navNotif).setOnClickListener(v -> {});
        findViewById(R.id.navProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
    }

    private void loadInvitations() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        String email = user.getEmail().toLowerCase().trim();

        db.collection("trips")
                .whereArrayContains("invitedEmails", email)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    invitationList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            invitationList.add(doc);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(invitationList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void acceptInvitation(String tripId, String email) {
        db.collection("trips").document(tripId)
                .update(
                        "participants", FieldValue.arrayUnion(email),
                        "invitedEmails", FieldValue.arrayRemove(email)
                )
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Invitation Accepted!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void rejectInvitation(String tripId, String email) {
        db.collection("trips").document(tripId)
                .update("invitedEmails", FieldValue.arrayRemove(email))
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Invitation Rejected", Toast.LENGTH_SHORT).show());
    }

    private class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            QueryDocumentSnapshot doc = invitationList.get(position);
            String tripName = doc.getString("name");
            holder.tvMessage.setText("You have been invited to join '" + tripName + "'.");

            holder.btnAccept.setOnClickListener(v -> {
                acceptInvitation(doc.getId(), auth.getCurrentUser().getEmail().toLowerCase().trim());
            });

            holder.btnReject.setOnClickListener(v -> {
                rejectInvitation(doc.getId(), auth.getCurrentUser().getEmail().toLowerCase().trim());
            });
        }

        @Override
        public int getItemCount() {
            return invitationList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, btnAccept, btnReject;
            ViewHolder(View v) {
                super(v);
                tvMessage = v.findViewById(R.id.tvNotifMessage);
                btnAccept = v.findViewById(R.id.btnAccept);
                btnReject = v.findViewById(R.id.btnReject);
            }
        }
    }
}