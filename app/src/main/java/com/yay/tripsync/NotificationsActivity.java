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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private List<NotificationItem> combinedList = new ArrayList<>();
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
        loadAllNotifications();
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

    private void loadAllNotifications() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        String email = user.getEmail().toLowerCase().trim();

        // Listener for Trip Invitations
        db.collection("trips")
                .whereArrayContains("invitedEmails", email)
                .addSnapshotListener((tripValue, tripError) -> {
                    // Listener for Friend Requests
                    db.collection("friend_requests")
                            .whereEqualTo("toEmail", email)
                            .whereEqualTo("status", "pending")
                            .addSnapshotListener((friendValue, friendError) -> {
                                combinedList.clear();

                                // Add Trip Invites
                                if (tripValue != null) {
                                    for (QueryDocumentSnapshot doc : tripValue) {
                                        combinedList.add(new NotificationItem(doc, "trip_invite"));
                                    }
                                }

                                // Add Friend Requests
                                if (friendValue != null) {
                                    for (QueryDocumentSnapshot doc : friendValue) {
                                        combinedList.add(new NotificationItem(doc, "friend_request"));
                                    }
                                }

                                adapter.notifyDataSetChanged();
                                tvEmpty.setVisibility(combinedList.isEmpty() ? View.VISIBLE : View.GONE);
                            });
                });
    }

    private void acceptTrip(String tripId, String email) {
        db.collection("trips").document(tripId)
                .update(
                        "participants", FieldValue.arrayUnion(email),
                        "invitedEmails", FieldValue.arrayRemove(email)
                )
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Joined Trip!", Toast.LENGTH_SHORT).show());
    }

    private void rejectTrip(String tripId, String email) {
        db.collection("trips").document(tripId)
                .update("invitedEmails", FieldValue.arrayRemove(email))
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Invite Rejected", Toast.LENGTH_SHORT).show());
    }

    private void acceptFriend(QueryDocumentSnapshot doc) {
        String fromUid = doc.getString("fromUid");
        String fromEmail = doc.getString("fromEmail");
        String fromName = doc.getString("fromName");
        String currentUserId = auth.getUid();
        String currentUserEmail = auth.getCurrentUser().getEmail().toLowerCase().trim();
        String currentUserName = auth.getCurrentUser().getDisplayName();

        Map<String, String> myFriendEntry = new HashMap<>();
        myFriendEntry.put("uid", fromUid);
        myFriendEntry.put("email", fromEmail);
        myFriendEntry.put("name", fromName);

        db.collection("users").document(currentUserId)
                .update("friends_list", FieldValue.arrayUnion(myFriendEntry))
                .addOnSuccessListener(aVoid -> {
                    Map<String, String> theirFriendEntry = new HashMap<>();
                    theirFriendEntry.put("uid", currentUserId);
                    theirFriendEntry.put("email", currentUserEmail);
                    theirFriendEntry.put("name", currentUserName != null ? currentUserName : currentUserEmail.split("@")[0]);

                    db.collection("users").document(fromUid)
                            .update("friends_list", FieldValue.arrayUnion(theirFriendEntry));

                    db.collection("friend_requests").document(doc.getId()).update("status", "accepted");
                    Toast.makeText(this, "Friend Added!", Toast.LENGTH_SHORT).show();
                });
    }

    private void rejectFriend(String requestId) {
        db.collection("friend_requests").document(requestId).update("status", "rejected");
    }

    private static class NotificationItem {
        QueryDocumentSnapshot doc;
        String type;
        NotificationItem(QueryDocumentSnapshot doc, String type) {
            this.doc = doc;
            this.type = type;
        }
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
            NotificationItem item = combinedList.get(position);
            String email = auth.getCurrentUser().getEmail().toLowerCase().trim();

            if (item.type.equals("trip_invite")) {
                holder.tvTitle.setText("Trip Invitation");
                holder.tvMessage.setText("Invited to join '" + item.doc.getString("name") + "'.");
                holder.btnAccept.setOnClickListener(v -> acceptTrip(item.doc.getId(), email));
                holder.btnReject.setOnClickListener(v -> rejectTrip(item.doc.getId(), email));
            } else {
                holder.tvTitle.setText("Friend Request");
                holder.tvMessage.setText(item.doc.getString("fromName") + " wants to be your friend.");
                holder.btnAccept.setOnClickListener(v -> acceptFriend(item.doc));
                holder.btnReject.setOnClickListener(v -> rejectFriend(item.doc.getId()));
            }
        }

        @Override
        public int getItemCount() { return combinedList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMessage, btnAccept, btnReject;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvNotifTitle);
                tvMessage = v.findViewById(R.id.tvNotifMessage);
                btnAccept = v.findViewById(R.id.btnAccept);
                btnReject = v.findViewById(R.id.btnReject);
            }
        }
    }
}