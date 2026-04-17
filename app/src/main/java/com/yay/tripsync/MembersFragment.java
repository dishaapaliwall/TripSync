package com.yay.tripsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

public class MembersFragment extends Fragment {

    private RecyclerView rvMembers;
    private TextView tvTripCode, btnCopyCode;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseAuth auth;
    private String tripCode;
    private MemberAdapter adapter;
    private List<Map<String, Object>> memberList = new ArrayList<>();
    private String tripOwnerId;
    private String selectedDocType;

    private final ActivityResultLauncher<Intent> pickDocLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    if (fileUri != null && selectedDocType != null) {
                        uploadDocument(fileUri, selectedDocType);
                    }
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_members, container, false);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();
        
        if (getActivity() != null) {
            tripCode = getActivity().getIntent().getStringExtra("tripId");
        }

        rvMembers = v.findViewById(R.id.rvMembers);
        tvTripCode = v.findViewById(R.id.tvTripCode);
        btnCopyCode = v.findViewById(R.id.btnCopyCode);

        tvTripCode.setText(tripCode != null ? tripCode : "N/A");
        btnCopyCode.setOnClickListener(view -> {
            if (tripCode == null) return;
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Trip Code", tripCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Code Copied!", Toast.LENGTH_SHORT).show();
        });

        v.findViewById(R.id.btnUploadFlight).setOnClickListener(view -> openPicker("Flight Tickets"));
        v.findViewById(R.id.btnUploadHotel).setOnClickListener(view -> openPicker("Hotel Booking"));
        v.findViewById(R.id.btnUploadID).setOnClickListener(view -> openPicker("ID Copies"));
        v.findViewById(R.id.btnUploadInsurance).setOnClickListener(view -> openPicker("Travel Insurance"));

        rvMembers.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MemberAdapter();
        rvMembers.setAdapter(adapter);

        if (tripCode != null) {
            loadTripAndMembers();
        }

        return v;
    }

    private void openPicker(String type) {
        selectedDocType = type;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        pickDocLauncher.launch(intent);
    }

    private void uploadDocument(Uri uri, String type) {
        if (tripCode == null || auth.getUid() == null) return;

        Toast.makeText(getContext(), "Uploading " + type + "...", Toast.LENGTH_SHORT).show();
        String fileName = type.replace(" ", "_") + "_" + UUID.randomUUID().toString();
        
        StorageReference ref = storage.getReference().child("trip_docs").child(tripCode).child(auth.getUid()).child(fileName);
        
        ref.putFile(uri)
            .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                saveDocToFirestore(downloadUri.toString(), type);
            }))
            .addOnFailureListener(e -> {
                Log.e("UploadError", "Failed: " + e.getMessage());
                Toast.makeText(getContext(), "Upload failed!", Toast.LENGTH_LONG).show();
            });
    }

    private void saveDocToFirestore(String url, String type) {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                Map<String, Object> docData = new HashMap<>();
                docData.put("url", url);
                docData.put("type", type);
                docData.put("userId", auth.getUid());
                docData.put("userName", auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : "Unknown");
                docData.put("timestamp", System.currentTimeMillis());
                
                db.collection("trips").document(docId).collection("documents").add(docData)
                        .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), type + " Uploaded!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadTripAndMembers() {
        db.collection("trips").whereEqualTo("tripCode", tripCode).addSnapshotListener((value, error) -> {
            if (error != null || value == null || value.isEmpty()) return;
            
            DocumentSnapshot doc = value.getDocuments().get(0);
            tripOwnerId = doc.getString("userId");
            List<String> participants = (List<String>) doc.get("participants");
            
            if (participants == null) participants = new ArrayList<>();
            
            fetchUserDetails(participants, tripOwnerId);
        });
    }

    private void fetchUserDetails(List<String> emails, String ownerId) {
        // More robust way to fetch user details for participants
        db.collection("users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            memberList.clear();
            List<Map<String, Object>> sortedList = new ArrayList<>();

            // Build a map of found users for quick lookup
            Map<String, DocumentSnapshot> foundUsers = new HashMap<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                String email = doc.getString("email");
                String uid = doc.getId();
                if (email != null) {
                    foundUsers.put(email.toLowerCase().trim(), doc);
                }
                // Also index by UID to find owner if email mismatch
                foundUsers.put(uid, doc);
            }

            // 1. Add Host first
            DocumentSnapshot ownerDoc = foundUsers.get(ownerId);
            if (ownerDoc != null) {
                addMemberToList(ownerDoc, "Host", sortedList);
            }

            // 2. Add other participants
            for (String email : emails) {
                String cleanEmail = email.toLowerCase().trim();
                DocumentSnapshot userDoc = foundUsers.get(cleanEmail);
                if (userDoc != null && !userDoc.getId().equals(ownerId)) {
                    addMemberToList(userDoc, "Member", sortedList);
                }
            }

            memberList.addAll(sortedList);
            adapter.notifyDataSetChanged();
        });
    }

    private void addMemberToList(DocumentSnapshot doc, String role, List<Map<String, Object>> list) {
        Map<String, Object> userData = doc.getData();
        if (userData == null) return;

        Map<String, Object> member = new HashMap<>();
        member.put("uid", doc.getId());
        member.put("email", doc.getString("email"));
        
        // 🔥 STICK TO THE 'name' FIELD FROM FIRESTORE
        String name = doc.getString("name");
        if (name == null || name.isEmpty()) {
            // Last resort fallback
            name = doc.getString("email") != null ? doc.getString("email").split("@")[0] : "User";
        }
        
        member.put("name", name);
        member.put("role", role);
        list.add(member);
    }

    private void removeMember(String email) {
        new AlertDialog.Builder(getContext())
                .setTitle("Remove Member")
                .setMessage("Are you sure you want to remove " + email + "?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                            db.collection("trips").document(docId).update("participants", FieldValue.arrayRemove(email));
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUserDocs(String userEmail) {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                db.collection("trips").document(docId).collection("documents")
                        .whereEqualTo("userName", userEmail).get().addOnSuccessListener(docs -> {
                            if (docs.isEmpty()) {
                                Toast.makeText(getContext(), "No documents found", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            
                            List<String> items = new ArrayList<>();
                            List<String> urls = new ArrayList<>();
                            for (DocumentSnapshot d : docs) {
                                items.add(d.getString("type"));
                                urls.add(d.getString("url"));
                            }
                            
                            new AlertDialog.Builder(getContext())
                                    .setTitle("Documents for " + userEmail)
                                    .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls.get(which))));
                                    }).show();
                        });
            }
        });
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> member = memberList.get(position);
            String uid = (String) member.get("uid");
            String email = (String) member.get("email");
            String name = (String) member.get("name");
            String role = (String) member.get("role");

            holder.tvName.setText(name);
            holder.tvEmail.setText(email);
            holder.tvRole.setText(role);

            // 🔥 STICK TO THE DETERMINISTIC PERMANENT AVATAR
            int[] avatars = {
                    R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha,
                    R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx
            };
            if (uid != null) {
                int avatarIndex = Math.abs(uid.hashCode()) % avatars.length;
                holder.imgMember.setImageResource(avatars[avatarIndex]);
            }

            boolean isCurrentUserHost = auth.getUid() != null && auth.getUid().equals(tripOwnerId);

            if ("Host".equalsIgnoreCase(role)) {
                holder.btnRemove.setVisibility(View.GONE);
                holder.tvRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                holder.tvRole.setTextColor(0xFF000000);
            } else {
                holder.tvRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33000000));
                holder.tvRole.setTextColor(0xFFFFFFFF);
                holder.btnRemove.setVisibility(isCurrentUserHost ? View.VISIBLE : View.GONE);
            }

            holder.btnRemove.setOnClickListener(v -> removeMember(email));
            holder.btnDocs.setOnClickListener(v -> showUserDocs(email));
        }

        @Override
        public int getItemCount() { return memberList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvEmail, tvRole;
            CircleImageView imgMember;
            View btnRemove, btnDocs;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvMemberName);
                tvEmail = v.findViewById(R.id.tvMemberEmail);
                tvRole = v.findViewById(R.id.tvRole);
                imgMember = v.findViewById(R.id.imgMember);
                btnRemove = v.findViewById(R.id.btnRemove);
                btnDocs = v.findViewById(R.id.btnDocs);
            }
        }
    }
}