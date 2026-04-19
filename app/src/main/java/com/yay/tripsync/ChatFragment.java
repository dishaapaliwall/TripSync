package com.yay.tripsync;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatFragment extends Fragment {

    private static final String ARG_TRIP_ID = "tripId";
    private String tripId;

    private RecyclerView recyclerView;
    private EditText input;
    private android.widget.ImageButton sendBtn;

    private List<Message> messageList;
    private ChatAdapter adapter;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    public ChatFragment() {
        // Required empty public constructor
    }

    public static ChatFragment newInstance(String tripId) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TRIP_ID, tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tripId = getArguments().getString(ARG_TRIP_ID);
        }
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        recyclerView = view.findViewById(R.id.chatRecyclerView);
        input = view.findViewById(R.id.messageInput);
        sendBtn = view.findViewById(R.id.sendBtn);

        messageList = new ArrayList<>();
        adapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        if (tripId != null) {
            listenForMessages();
        } else {
            Toast.makeText(getContext(), "Error: Trip ID not found", Toast.LENGTH_SHORT).show();
        }

        sendBtn.setOnClickListener(v -> sendMessage());

        return view;
    }

    private void listenForMessages() {
        db.collection("chats")
                .whereEqualTo("tripId", tripId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            Log.e("ChatFragment", "Listen failed.", error);
                            return;
                        }

                        if (value != null) {
                            for (DocumentChange dc : value.getDocumentChanges()) {
                                if (dc.getType() == DocumentChange.Type.ADDED) {
                                    Message message = dc.getDocument().toObject(Message.class);
                                    // Prevent duplicates from optimistic UI
                                    if (!messageList.contains(message)) {
                                        messageList.add(message);
                                        adapter.notifyItemInserted(messageList.size() - 1);
                                        recyclerView.scrollToPosition(messageList.size() - 1);
                                    } else {
                                        // Update the optimistic message with the server timestamp if needed
                                        int index = messageList.indexOf(message);
                                        messageList.set(index, message);
                                        adapter.notifyItemChanged(index);
                                    }
                                }
                            }
                        }
                    }
                });
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || currentUser == null) return;

        String senderName = currentUser.getDisplayName();
        if (senderName == null || senderName.isEmpty()) {
            senderName = currentUser.getEmail().split("@")[0];
        }

        // Optimistic UI: Create message with temporary ID
        String tempId = UUID.randomUUID().toString();
        Message optimisticMessage = new Message(
                currentUser.getUid(),
                senderName,
                text,
                tripId,
                Timestamp.now()
        );
        optimisticMessage.setMessageId(tempId);

        // Add to UI immediately
        messageList.add(optimisticMessage);
        adapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        input.setText("");

        // Send to Firestore
        db.collection("chats").document(tempId).set(optimisticMessage)
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to send message", Toast.LENGTH_SHORT).show();
                    // Optional: remove from list on failure
                    messageList.remove(optimisticMessage);
                    adapter.notifyDataSetChanged();
                });
    }
}
