package com.yay.tripsync;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PhotosFragment extends Fragment {

    private RecyclerView rvPhotos;
    private View tvEmpty, fabAdd;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String tripCode;
    private PhotoAdapter adapter;
    private List<String> photoUrls = new ArrayList<>();

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        uploadPhoto(imageUri);
                    }
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photos, container, false);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance("gs://tripsync-f3f49.firebasestorage.app");
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
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        if (tripCode != null) {
            loadPhotos();
        }

        return v;
    }

    private void loadPhotos() {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                db.collection("trips").document(docId).collection("photos")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .addSnapshotListener((value, error) -> {
                            if (error != null || value == null) return;
                            photoUrls.clear();
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                                photoUrls.add(doc.getString("url"));
                            }
                            adapter.notifyDataSetChanged();
                            tvEmpty.setVisibility(photoUrls.isEmpty() ? View.VISIBLE : View.GONE);
                        });
            }
        });
    }

    private void uploadPhoto(Uri uri) {
        Toast.makeText(getContext(), "Uploading...", Toast.LENGTH_SHORT).show();
        StorageReference ref = storage.getReference().child("trip_photos/" + tripCode + "/" + UUID.randomUUID().toString());
        ref.putFile(uri).addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
            savePhotoToFirestore(downloadUri.toString());
        })).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void savePhotoToFirestore(String url) {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                Map<String, Object> photoData = new HashMap<>();
                photoData.put("url", url);
                photoData.put("timestamp", System.currentTimeMillis());
                db.collection("trips").document(docId).collection("photos").add(photoData);
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
            Glide.with(getContext()).load(photoUrls.get(position)).into(holder.ivPhoto);
        }

        @Override
        public int getItemCount() { return photoUrls.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            ViewHolder(View v) { super(v); ivPhoto = v.findViewById(R.id.ivPhoto); }
        }
    }
}