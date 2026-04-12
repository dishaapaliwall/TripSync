package com.yay.tripsync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
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

public class FriendsActivity extends AppCompatActivity {

    private EditText etSearchEmail;
    private CardView cardSearchResult;
    private TextView tvSearchName, tvSearchEmail, tvNoRequests;
    private Button btnAddFriend;
    private RecyclerView rvRequests, rvFriends;
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId, currentUserEmail, currentUserName;
    
    private List<QueryDocumentSnapshot> requestList = new ArrayList<>();
    private List<Map<String, String>> friendList = new ArrayList<>();
    private RequestAdapter requestAdapter;
    private FriendAdapter friendAdapter;

    private String foundUserUid, foundUserEmail, foundUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            currentUserEmail = user.getEmail().toLowerCase().trim();
            currentUserName = user.getDisplayName();
        }

        // Initialize Views
        etSearchEmail = findViewById(R.id.etSearchEmail);
        cardSearchResult = findViewById(R.id.cardSearchResult);
        tvSearchName = findViewById(R.id.tvSearchName);
        tvSearchEmail = findViewById(R.id.tvSearchEmail);
        btnAddFriend = findViewById(R.id.btnAddFriend);
        rvRequests = findViewById(R.id.rvRequests);
        rvFriends = findViewById(R.id.rvFriends);
        tvNoRequests = findViewById(R.id.tvNoRequests);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Setup RecyclerViews
        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        requestAdapter = new RequestAdapter();
        rvRequests.setAdapter(requestAdapter);

        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        friendAdapter = new FriendAdapter();
        rvFriends.setAdapter(friendAdapter);

        // Search Logic
        findViewById(R.id.btnSearchFriend).setOnClickListener(v -> searchUser());

        btnAddFriend.setOnClickListener(v -> sendFriendRequest());

        loadRequests();
        loadFriends();
    }

    private void searchUser() {
        String email = etSearchEmail.getText().toString().trim().toLowerCase();
        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.equals(currentUserEmail)) {
            Toast.makeText(this, "You cannot add yourself!", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        foundUserUid = doc.getId();
                        foundUserEmail = doc.getString("email");
                        foundUserName = doc.getString("name");

                        tvSearchName.setText(foundUserName != null ? foundUserName : foundUserEmail.split("@")[0]);
                        tvSearchEmail.setText(foundUserEmail);
                        cardSearchResult.setVisibility(View.VISIBLE);
                    } else {
                        cardSearchResult.setVisibility(View.GONE);
                        Toast.makeText(this, "User not found. Ensure the user has logged in once.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Search error", Toast.LENGTH_SHORT).show());
    }

    private void sendFriendRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("fromUid", currentUserId);
        request.put("fromEmail", currentUserEmail);
        request.put("fromName", currentUserName != null ? currentUserName : currentUserEmail.split("@")[0]);
        request.put("toUid", foundUserUid);
        request.put("toEmail", foundUserEmail);
        request.put("status", "pending");
        request.put("type", "friend_request");

        db.collection("friend_requests").add(request)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Request Sent!", Toast.LENGTH_SHORT).show();
                    cardSearchResult.setVisibility(View.GONE);
                    etSearchEmail.setText("");
                });
    }

    private void loadRequests() {
        db.collection("friend_requests")
                .whereEqualTo("toEmail", currentUserEmail)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    requestList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            requestList.add(doc);
                        }
                    }
                    requestAdapter.notifyDataSetChanged();
                    tvNoRequests.setVisibility(requestList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void loadFriends() {
        db.collection("users").document(currentUserId).addSnapshotListener((value, error) -> {
            if (error != null || value == null) return;
            List<Map<String, String>> friends = (List<Map<String, String>>) value.get("friends_list");
            friendList.clear();
            if (friends != null) {
                friendList.addAll(friends);
            }
            friendAdapter.notifyDataSetChanged();
        });
    }

    private void acceptRequest(QueryDocumentSnapshot doc) {
        String fromUid = doc.getString("fromUid");
        String fromEmail = doc.getString("fromEmail");
        String fromName = doc.getString("fromName");

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

    private void rejectRequest(QueryDocumentSnapshot doc) {
        db.collection("friend_requests").document(doc.getId()).update("status", "rejected");
    }

    private class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            QueryDocumentSnapshot doc = requestList.get(position);
            holder.tvNotifTitle.setText("Friend Request");
            holder.tvMessage.setText(doc.getString("fromName") + " wants to be your friend.");
            holder.btnAccept.setOnClickListener(v -> acceptRequest(doc));
            holder.btnReject.setOnClickListener(v -> rejectRequest(doc));
        }

        @Override
        public int getItemCount() { return requestList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, tvNotifTitle, btnAccept, btnReject;
            ViewHolder(View v) {
                super(v);
                tvNotifTitle = v.findViewById(R.id.tvNotifTitle);
                tvMessage = v.findViewById(R.id.tvNotifMessage);
                btnAccept = v.findViewById(R.id.btnAccept);
                btnReject = v.findViewById(R.id.btnReject);
            }
        }
    }

    private class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_country, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, String> friend = friendList.get(position);
            holder.tvName.setText(friend.get("name"));
            holder.ivCheck.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() { return friendList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivCheck;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvCountryName);
                ivCheck = v.findViewById(R.id.ivCheck);
            }
        }
    }
}