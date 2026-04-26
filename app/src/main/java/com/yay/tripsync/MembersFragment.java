package com.yay.tripsync;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import de.hdodenhof.circleimageview.CircleImageView;

public class MembersFragment extends Fragment {

    private RecyclerView rvMembers;
    private TextView tvTripCode, btnCopyCode, tvMemberCount;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String tripCode;
    private MemberAdapter adapter;
    private List<Map<String, Object>> memberList = new ArrayList<>();
    private String tripOwnerId;
    private String selectedDocType;
    private ListenerRegistration docsListener;
    private AlertDialog currentDocsDialog;

    private static final String CLOUDINARY_PRESET = "gda3ahqj";

    private final ActivityResultLauncher<Intent> pickDocLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    if (fileUri != null && selectedDocType != null) {
                        uploadToCloudinary(fileUri, selectedDocType);
                    }
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_members, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        
        if (getActivity() != null) {
            tripCode = getActivity().getIntent().getStringExtra("tripId");
        }

        rvMembers = v.findViewById(R.id.rvMembers);
        tvTripCode = v.findViewById(R.id.tvTripCode);
        btnCopyCode = v.findViewById(R.id.btnCopyCode);
        tvMemberCount = v.findViewById(R.id.tvMemberCount);

        tvTripCode.setText(tripCode != null ? tripCode : "N/A");
        btnCopyCode.setOnClickListener(view -> {
            if (tripCode == null) return;
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Trip Code", tripCode);
            clipboard.setPrimaryClip(clip);
        });

        v.findViewById(R.id.btnUploadFlight).setOnClickListener(view -> checkExistingAndOpenPicker("Flight Tickets"));
        v.findViewById(R.id.btnUploadHotel).setOnClickListener(view -> checkExistingAndOpenPicker("Hotel Booking"));
        v.findViewById(R.id.btnUploadID).setOnClickListener(view -> checkExistingAndOpenPicker("ID Copies"));
        v.findViewById(R.id.btnUploadInsurance).setOnClickListener(view -> checkExistingAndOpenPicker("Travel Insurance"));

        rvMembers.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MemberAdapter();
        rvMembers.setAdapter(adapter);

        if (tripCode != null) {
            loadTripAndMembers();
        }

        return v;
    }

    private void checkExistingAndOpenPicker(String type) {
        if (auth.getUid() == null) return;
        
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String tripDocId = queryDocumentSnapshots.getDocuments().get(0).getId();
                db.collection("trips").document(tripDocId).collection("documents")
                        .whereEqualTo("userId", auth.getUid())
                        .whereEqualTo("type", type)
                        .get().addOnSuccessListener(docs -> {
                            if (!docs.isEmpty()) {
                                Toast.makeText(getContext(), "Already uploaded! Delete it first to re-upload.", Toast.LENGTH_LONG).show();
                            } else {
                                openPicker(type);
                            }
                        });
            }
        });
    }

    private void openPicker(String type) {
        selectedDocType = type;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "image/png", "image/jpg", "image/jpeg"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickDocLauncher.launch(intent);
    }

    private void uploadToCloudinary(Uri uri, String type) {
        if (tripCode == null || auth.getUid() == null) return;

        Toast.makeText(getContext(), "Uploading " + type + "...", Toast.LENGTH_SHORT).show();
        
        MediaManager.get().upload(uri)
                .unsigned(CLOUDINARY_PRESET)
                .option("resource_type", "auto") 
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) { Log.d("Cloudinary", "Upload Started"); }
                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        saveDocToFirestore(url, type);
                    }
                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e("Cloudinary", "Upload Error: " + error.getDescription() + " Code: " + error.getCode());
                        Toast.makeText(getContext(), "Error 401: Check if preset is Unsigned", Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
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
        db.collection("users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            memberList.clear();
            List<Map<String, Object>> sortedList = new ArrayList<>();
            Map<String, DocumentSnapshot> foundUsers = new HashMap<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                String email = doc.getString("email");
                if (email != null) foundUsers.put(email.toLowerCase().trim(), doc);
                foundUsers.put(doc.getId(), doc);
            }
            
            DocumentSnapshot ownerDoc = foundUsers.get(ownerId);
            if (ownerDoc != null) {
                addMemberToList(ownerDoc, "Host", sortedList, false);
            } else {
                addMemberToList(null, "Host", sortedList, true);
            }

            for (String email : emails) {
                String cleanEmail = email.toLowerCase().trim();
                DocumentSnapshot userDoc = foundUsers.get(cleanEmail);
                if (userDoc != null) {
                    if (!userDoc.getId().equals(ownerId)) {
                        addMemberToList(userDoc, "Member", sortedList, false);
                    }
                } else {
                    addMemberToList(null, "Member", sortedList, true);
                }
            }
            memberList.addAll(sortedList);
            adapter.notifyDataSetChanged();
            
            if (tvMemberCount != null) {
                int count = memberList.size();
                tvMemberCount.setText(count + (count == 1 ? " Member" : " Members"));
            }
        });
    }

    private void addMemberToList(DocumentSnapshot doc, String role, List<Map<String, Object>> list, boolean isDeleted) {
        Map<String, Object> member = new HashMap<>();
        if (isDeleted) {
            member.put("uid", "deleted_" + new Random().nextInt(1000));
            member.put("email", "");
            member.put("name", generateRandomString(3));
            member.put("isDeleted", true);
        } else {
            member.put("uid", doc.getId());
            member.put("email", doc.getString("email"));
            String name = doc.getString("name");
            if (name == null || name.isEmpty()) {
                String email = doc.getString("email");
                name = (email != null) ? email.split("@")[0] : "Unknown";
            }
            member.put("name", name);
            member.put("isDeleted", false);
        }
        member.put("role", role);
        list.add(member);
    }

    private String generateRandomString(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        while (sb.length() < len) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void showUserDocs(String targetUserId, String targetUserName, boolean isDeleted) {
        if (isDeleted) {
            Toast.makeText(getContext(), "Account deleted. Documents removed.", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String tripDocId = queryDocumentSnapshots.getDocuments().get(0).getId();
                
                // 🔥 Real-time listener for documents
                if (docsListener != null) docsListener.remove();
                
                docsListener = db.collection("trips").document(tripDocId).collection("documents")
                        .whereEqualTo("userId", targetUserId)
                        .addSnapshotListener((value, error) -> {
                            if (error != null) return;
                            if (value == null || value.isEmpty()) {
                                if (currentDocsDialog != null && currentDocsDialog.isShowing()) {
                                    currentDocsDialog.dismiss();
                                }
                                Toast.makeText(getContext(), "No documents found", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            displayDocsDialog(tripDocId, targetUserName, value.getDocuments());
                        });
            }
        });
    }

    private void displayDocsDialog(String tripDocId, String userName, List<DocumentSnapshot> docs) {
        if (currentDocsDialog != null && currentDocsDialog.isShowing()) {
            RecyclerView rvDocs = currentDocsDialog.findViewById(R.id.rvDocs);
            if (rvDocs != null) {
                rvDocs.setAdapter(new DocumentAdapter(docs, (doc, action) -> {
                    if (action.equals("View")) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(doc.getString("url")));
                        startActivity(browserIntent);
                    } else if (action.equals("Delete")) {
                        deleteDoc(tripDocId, doc.getId());
                    }
                }));
            }
            return;
        }

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_member_docs, null);
        currentDocsDialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        if (currentDocsDialog.getWindow() != null) {
            currentDocsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDocDialogTitle);
        tvTitle.setText("Docs: " + userName);

        RecyclerView rvDocs = dialogView.findViewById(R.id.rvDocs);
        rvDocs.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDocs.setAdapter(new DocumentAdapter(docs, (doc, action) -> {
            if (action.equals("View")) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(doc.getString("url")));
                startActivity(browserIntent);
            } else if (action.equals("Delete")) {
                deleteDoc(tripDocId, doc.getId());
            }
        }));

        dialogView.findViewById(R.id.btnDocClose).setOnClickListener(v -> currentDocsDialog.dismiss());
        currentDocsDialog.show();
    }

    private void deleteDoc(String tripDocId, String docId) {
        db.collection("trips").document(tripDocId).collection("documents").document(docId).delete()
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Document Deleted", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (docsListener != null) docsListener.remove();
    }

    private void removeMember(String email) {
        if (email == null || email.isEmpty()) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Remove Member")
                .setMessage("Are you sure?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                            db.collection("trips").document(docId).update("participants", FieldValue.arrayRemove(email));
                        }
                    });
                }).setNegativeButton("Cancel", null).show();
    }

    private class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.ViewHolder> {
        private List<DocumentSnapshot> docs;
        private OnDocClickListener listener;

        DocumentAdapter(List<DocumentSnapshot> docs, OnDocClickListener listener) {
            this.docs = docs;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = docs.get(position);
            String type = doc.getString("type");
            String uploaderId = doc.getString("userId");

            holder.tvType.setText(type);
            boolean isMe = auth.getUid() != null && auth.getUid().equals(uploaderId);
            holder.tvUploader.setText(isMe ? "Uploaded by you" : "Uploaded by member");

            holder.itemView.setOnClickListener(v -> {
                String[] options = isMe ? new String[]{"View", "Delete"} : new String[]{"View"};
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                        .setTitle(type)
                        .setItems(options, (d, which) -> listener.onDocClick(doc, options[which]))
                        .show();
            });
        }

        @Override
        public int getItemCount() { return docs.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvType, tvUploader;
            ViewHolder(View v) {
                super(v);
                tvType = v.findViewById(R.id.tvDocType);
                tvUploader = v.findViewById(R.id.tvDocUploader);
            }
        }
    }

    interface OnDocClickListener {
        void onDocClick(DocumentSnapshot doc, String action);
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
            boolean isDeleted = (boolean) member.getOrDefault("isDeleted", false);

            holder.tvName.setText(name);
            holder.tvEmail.setText(isDeleted ? "Account Deleted" : email);
            holder.tvRole.setText(role);

            int[] avatars = {R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha, R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx};
            if (uid != null) holder.imgMember.setImageResource(avatars[Math.abs(uid.hashCode()) % avatars.length]);

            boolean isCurrentUserHost = auth.getUid() != null && auth.getUid().equals(tripOwnerId);
            holder.btnRemove.setVisibility(isCurrentUserHost && !"Host".equalsIgnoreCase(role) && !isDeleted ? View.VISIBLE : View.GONE);
            
            holder.btnRemove.setOnClickListener(v -> removeMember(email));
            holder.btnDocs.setOnClickListener(v -> showUserDocs(uid, email, isDeleted));
            
            if (isDeleted) {
                holder.tvName.setTypeface(null, android.graphics.Typeface.ITALIC);
                holder.tvEmail.setTextColor(0xFF888888);
            } else {
                holder.tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.tvEmail.setTextColor(0xFFFFFFFF);
            }
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
