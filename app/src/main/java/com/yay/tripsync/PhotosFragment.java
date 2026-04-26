package com.yay.tripsync;

import android.app.Activity;
import android.app.AlertDialog;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhotosFragment extends Fragment {

    private RecyclerView rvPhotos;
    private View tvEmpty, fabAdd;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String tripCode;
    private PhotoAdapter adapter;
    private List<Map<String, String>> mediaList = new ArrayList<>();

    private final ActivityResultLauncher<Intent> pickMediaLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri mediaUri = result.getData().getData();
                    if (mediaUri != null) {
                        uploadToCloudinary(mediaUri);
                    }
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photos, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        if (getActivity() != null) {
            tripCode = getActivity().getIntent().getStringExtra("tripId");
        }

        rvPhotos = v.findViewById(R.id.rvPhotos);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        fabAdd = v.findViewById(R.id.fabAddPhoto);

        rvPhotos.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        adapter = new PhotoAdapter();
        rvPhotos.setAdapter(adapter);

        fabAdd.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/* video/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "image/jpg", "video/*"});
            pickMediaLauncher.launch(intent);
        });

        if (tripCode != null) {
            loadMedia();
        }

        return v;
    }

    private void loadMedia() {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                db.collection("trips").document(docId).collection("photos")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .addSnapshotListener((value, error) -> {
                            if (error != null || value == null) return;
                            mediaList.clear();
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                                Map<String, String> media = new HashMap<>();
                                media.put("id", doc.getId());
                                media.put("url", doc.getString("url"));
                                media.put("type", doc.getString("type"));
                                media.put("userId", doc.getString("userId"));
                                mediaList.add(media);
                            }
                            adapter.notifyDataSetChanged();
                            tvEmpty.setVisibility(mediaList.isEmpty() ? View.VISIBLE : View.GONE);
                        });
            }
        });
    }

    private void uploadToCloudinary(Uri uri) {
        Toast.makeText(getContext(), "Uploading...", Toast.LENGTH_SHORT).show();
        MediaManager.get().upload(uri)
                .unsigned("gda3ahqj")
                .option("resource_type", "auto")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}
                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        saveMediaToFirestore((String) resultData.get("secure_url"), (String) resultData.get("resource_type"));
                    }
                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(getContext(), "Upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void saveMediaToFirestore(String url, String type) {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                Map<String, Object> mediaData = new HashMap<>();
                mediaData.put("url", url);
                mediaData.put("type", type);
                mediaData.put("userId", auth.getUid());
                mediaData.put("timestamp", System.currentTimeMillis());
                db.collection("trips").document(docId).collection("photos").add(mediaData);
            }
        });
    }

    private void showDeleteDialog(String mediaId) {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_delete_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView btnDelete = dialogView.findViewById(R.id.btnDelete);

        tvTitle.setText("Delete Media");
        tvMessage.setText("Are you sure you want to delete this photo/video permanently?");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDelete.setOnClickListener(v -> {
            deleteMedia(mediaId);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void deleteMedia(String mediaId) {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String tripDocId = queryDocumentSnapshots.getDocuments().get(0).getId();
                db.collection("trips").document(tripDocId).collection("photos").document(mediaId).delete()
                        .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Media deleted", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, String> media = mediaList.get(position);
            String url = media.get("url");
            String type = media.get("type");
            String uploaderId = media.get("userId");

            if ("video".equals(type)) {
                String thumbnailUrl = url.substring(0, url.lastIndexOf(".")) + ".jpg";
                Glide.with(getContext()).load(thumbnailUrl).placeholder(R.drawable.ic_play_circle).into(holder.ivPhoto);
            } else {
                Glide.with(getContext()).load(url).into(holder.ivPhoto);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setDataAndType(Uri.parse(url), "video".equals(type) ? "video/*" : "image/*");
                startActivity(intent);
            });

            // Long click to delete
            holder.itemView.setOnLongClickListener(v -> {
                String currentUserId = auth.getUid();
                boolean isUploader = currentUserId != null && currentUserId.equals(uploaderId);
                
                if (isUploader) {
                    showDeleteDialog(media.get("id"));
                } else {
                    Toast.makeText(getContext(), "You can only delete your own uploads", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        @Override
        public int getItemCount() { return mediaList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            ViewHolder(View v) { super(v); ivPhoto = v.findViewById(R.id.ivPhoto); }
        }
    }
}