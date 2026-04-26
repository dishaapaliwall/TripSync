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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // The preset must be set to "Unsigned" in your Cloudinary Dashboard
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
            // Toast removed to avoid double notification on newer Android versions
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
        
        // This targets https://api.cloudinary.com/v1_1/dsuwxepmx/auto/upload
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
            if (ownerDoc != null) addMemberToList(ownerDoc, "Host", sortedList);
            for (String email : emails) {
                DocumentSnapshot userDoc = foundUsers.get(email.toLowerCase().trim());
                if (userDoc != null && !userDoc.getId().equals(ownerId)) {
                    addMemberToList(userDoc, "Member", sortedList);
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

    private void addMemberToList(DocumentSnapshot doc, String role, List<Map<String, Object>> list) {
        Map<String, Object> member = new HashMap<>();
        member.put("uid", doc.getId());
        member.put("email", doc.getString("email"));
        String name = doc.getString("name");
        if (name == null || name.isEmpty()) {
            String email = doc.getString("email");
            name = (email != null) ? email.split("@")[0] : "Unknown";
        }
        member.put("name", name);
        member.put("role", role);
        list.add(member);
    }

    private void showUserDocs(String targetUserId, String targetUserName) {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String tripDocId = queryDocumentSnapshots.getDocuments().get(0).getId();
                db.collection("trips").document(tripDocId).collection("documents")
                        .whereEqualTo("userId", targetUserId).get()
                        .addOnSuccessListener(docs -> {
                            if (docs.isEmpty()) {
                                db.collection("trips").document(tripDocId).collection("documents")
                                    .whereEqualTo("userName", targetUserName).get().addOnSuccessListener(docs2 -> {
                                        if (docs2.isEmpty()) {
                                            Toast.makeText(getContext(), "No documents found", Toast.LENGTH_SHORT).show();
                                        } else {
                                            displayDocsDialog(tripDocId, targetUserName, docs2.getDocuments());
                                        }
                                    });
                                return;
                            }
                            displayDocsDialog(tripDocId, targetUserName, docs.getDocuments());
                        });
            }
        });
    }

    private void displayDocsDialog(String tripDocId, String userName, List<DocumentSnapshot> docs) {
        List<String> items = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        List<String> uploaderIds = new ArrayList<>();

        for (DocumentSnapshot d : docs) {
            items.add(d.getString("type"));
            urls.add(d.getString("url"));
            docIds.add(d.getId());
            uploaderIds.add(d.getString("userId"));
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Documents: " + userName)
                .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
                    String currentDocId = docIds.get(which);
                    String currentUrl = urls.get(which);
                    String currentUploaderId = uploaderIds.get(which);

                    boolean isOwner = auth.getUid() != null && auth.getUid().equals(currentUploaderId);
                    String[] options = isOwner ? new String[]{"View", "Delete"} : new String[]{"View"};

                    new AlertDialog.Builder(getContext())
                        .setTitle(items.get(which))
                        .setItems(options, (d2, w2) -> {
                            if (options[w2].equals("View")) {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl));
                                startActivity(browserIntent);
                            } else {
                                deleteDoc(tripDocId, currentDocId);
                            }
                        }).show();
                }).show();
    }

    private void deleteDoc(String tripDocId, String docId) {
        db.collection("trips").document(tripDocId).collection("documents").document(docId).delete()
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Document Deleted", Toast.LENGTH_SHORT).show());
    }

    private void removeMember(String email) {
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

            int[] avatars = {R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha, R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx};
            if (uid != null) holder.imgMember.setImageResource(avatars[Math.abs(uid.hashCode()) % avatars.length]);

            boolean isCurrentUserHost = auth.getUid() != null && auth.getUid().equals(tripOwnerId);
            holder.btnRemove.setVisibility(isCurrentUserHost && !"Host".equalsIgnoreCase(role) ? View.VISIBLE : View.GONE);
            
            holder.btnRemove.setOnClickListener(v -> removeMember(email));
            holder.btnDocs.setOnClickListener(v -> showUserDocs(uid, email));
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