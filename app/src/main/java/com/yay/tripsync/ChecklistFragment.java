package com.yay.tripsync;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChecklistFragment extends Fragment {

    private RecyclerView rvChecklist;
    private TextView tvEmpty, tvProgressCount;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private String tripCode;
    private String tripDocId;

    private List<ChecklistItem> checklistItems = new ArrayList<>();
    private ChecklistAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_checklist, container, false);

        db = FirebaseFirestore.getInstance();
        if (getActivity() != null) {
            tripCode = getActivity().getIntent().getStringExtra("tripId");
        }

        rvChecklist = v.findViewById(R.id.rvChecklist);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvProgressCount = v.findViewById(R.id.tvProgressCount);
        progressBar = v.findViewById(R.id.progressBar);

        v.findViewById(R.id.fabAdd).setOnClickListener(view -> showAddDialog());

        rvChecklist.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChecklistAdapter();
        rvChecklist.setAdapter(adapter);

        if (tripCode != null) {
            resolveTripDocId();
        }

        return v;
    }

    // Step 1: get the Firestore document ID from the tripCode
    private void resolveTripDocId() {
        db.collection("trips")
                .whereEqualTo("tripCode", tripCode)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        tripDocId = query.getDocuments().get(0).getId();
                        loadChecklist();
                    }
                });
    }

    // Step 2: listen for realtime updates on the checklist subcollection
    private void loadChecklist() {
        db.collection("trips").document(tripDocId)
                .collection("checklist")
                .orderBy("category")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    checklistItems.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        ChecklistItem item = new ChecklistItem(
                                doc.getId(),
                                doc.getString("name"),
                                doc.getString("category"),
                                doc.getLong("quantity") != null ? doc.getLong("quantity").intValue() : 1,
                                Boolean.TRUE.equals(doc.getBoolean("checked"))
                        );
                        checklistItems.add(item);
                    }

                    adapter.updateData();
                    updateProgress();
                    tvEmpty.setVisibility(checklistItems.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void updateProgress() {
        int total = checklistItems.size();
        int packed = 0;
        for (ChecklistItem item : checklistItems) {
            if (item.isChecked()) packed++;
        }

        tvProgressCount.setText(packed + " / " + total + " items are packed");

        if (total > 0) {
            progressBar.setMax(total);
            progressBar.setProgress(packed);
        } else {
            progressBar.setProgress(0);
        }
    }

    private void showAddDialog() {
        // Inflate our fully custom dialog layout
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_checklist_item, null);

        // Build the dialog with a transparent background so our layout's
        // rounded corners and dark color show cleanly
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // ── Item Name: AutoCompleteTextView with preset suggestions ──
        AutoCompleteTextView actvItemName = dialogView.findViewById(R.id.actvItemName);

        String[] itemSuggestions = {
                // Clothing
                "T-Shirts", "Shorts", "Jeans", "Flip Flops", "Sandals", "Sneakers",
                "Swimwear", "Sunglasses", "Cap / Hat", "Jacket", "Raincoat",
                // Electronics
                "Phone Charger", "Power Bank", "Earphones / AirPods", "Camera",
                "Laptop", "Laptop Charger", "Travel Adapter",
                // Documents
                "Aadhar Card", "Passport", "Driving License", "Hotel Booking",
                "Flight Tickets", "Travel Insurance", "Visa Documents",
                // Toiletries
                "Toothbrush", "Toothpaste", "Shampoo", "Body Wash", "Deodorant",
                "Face Wash", "Sunscreen", "Moisturizer", "Razor",
                // Medicines
                "Paracetamol", "ORS Sachets", "Band-Aids", "Antiseptic Cream",
                "Motion Sickness Pills", "Personal Prescription Medicines",
                // Food & Snacks
                "Water Bottle", "Snack Bars", "Instant Noodles", "Dry Fruits",
                // Other
                "Travel Pillow", "Eye Mask", "Umbrella", "Luggage Lock", "Cash"
        };

        ArrayAdapter<String> nameAdapter = new ArrayAdapter<String>(
                getContext(), android.R.layout.simple_dropdown_item_1line, itemSuggestions) {

            // Override getView so dropdown items match our dark theme
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setBackgroundColor(0xFF2A0808);
                tv.setPadding(32, 20, 32, 20);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setBackgroundColor(0xFF2A0808);
                tv.setPadding(32, 20, 32, 20);
                return tv;
            }
        };

        actvItemName.setAdapter(nameAdapter);
        actvItemName.setDropDownBackgroundResource(android.R.color.transparent);
        actvItemName.setThreshold(1); // Show suggestions after 1 character

        // Show all suggestions when field is tapped (like a dropdown)
        actvItemName.setOnClickListener(v -> actvItemName.showDropDown());
        actvItemName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvItemName.showDropDown();
        });

        // ── Quantity EditText ──
        EditText etQuantity = dialogView.findViewById(R.id.etQuantity);

        // ── Category Spinner ──
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinnerCategory);

        String[] categories = {
                "Clothing", "Electronics", "Documents",
                "Toiletries", "Medicines", "Food & Snacks", "Other"
        };

        // Custom spinner adapter to match dark theme
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<String>(
                getContext(), android.R.layout.simple_spinner_item, categories) {

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setPadding(0, 0, 0, 0);
                tv.setTextSize(14);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setBackgroundColor(0xFF2A0808);
                tv.setPadding(32, 20, 32, 20);
                tv.setTextSize(14);
                return tv;
            }
        };

        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        // ── Buttons ──
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btnAdd).setOnClickListener(v -> {
            String name = actvItemName.getText().toString().trim();
            String qtyStr = etQuantity.getText().toString().trim();
            String category = spinnerCategory.getSelectedItem().toString();

            if (name.isEmpty()) {
                actvItemName.setError("Please enter an item name");
                actvItemName.requestFocus();
                return;
            }

            int qty = 1;
            try {
                if (!qtyStr.isEmpty()) qty = Integer.parseInt(qtyStr);
            } catch (NumberFormatException e) {
                etQuantity.setError("Invalid quantity");
                return;
            }
            saveItem(name, category, qty);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveItem(String name, String category, int qty) {
        if (tripDocId == null) return;

        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("category", category);
        item.put("quantity", qty);
        item.put("checked", false);

        db.collection("trips").document(tripDocId)
                .collection("checklist")
                .add(item)
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to add item", Toast.LENGTH_SHORT).show());
    }

    private void toggleChecked(ChecklistItem item) {
        if (tripDocId == null) return;
        db.collection("trips").document(tripDocId)
                .collection("checklist")
                .document(item.getId())
                .update("checked", !item.isChecked());
    }

    private void deleteItem(ChecklistItem item) {
        if (tripDocId == null) return;
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete Item")
                .setMessage("Remove \"" + item.getName() + "\" from checklist?")
                .setPositiveButton("Delete", (d, w) ->
                        db.collection("trips").document(tripDocId)
                                .collection("checklist")
                                .document(item.getId())
                                .delete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ──────────────────────────────────────────────
    // Adapter with category header grouping
    // ──────────────────────────────────────────────
    private class ChecklistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        // Flat list mixing headers and items
        private List<Object> flatList = new ArrayList<>();

        public void updateData() {
            buildFlatList();
            notifyDataSetChanged();
        }

        private void buildFlatList() {
            flatList.clear();
            String lastCategory = null;

            for (ChecklistItem item : checklistItems) {
                if (!item.getCategory().equals(lastCategory)) {
                    flatList.add(item.getCategory()); // String = header
                    lastCategory = item.getCategory();
                }
                flatList.add(item);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return flatList.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View v = inflater.inflate(R.layout.item_checklist_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_checklist, parent, false);
                return new ItemViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvCategory.setText((String) flatList.get(position));
            } else {
                ChecklistItem item = (ChecklistItem) flatList.get(position);
                ItemViewHolder h = (ItemViewHolder) holder;

                h.tvItemName.setText(item.getName());
                h.tvQuantity.setText("Qty: " + item.getQuantity());

                // Prevent listener firing during bind
                h.cbCheck.setOnCheckedChangeListener(null);
                h.cbCheck.setChecked(item.isChecked());

                // Strikethrough when checked
                if (item.isChecked()) {
                    h.tvItemName.setPaintFlags(h.tvItemName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    h.tvItemName.setTextColor(0xFF666666);
                } else {
                    h.tvItemName.setPaintFlags(h.tvItemName.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                    h.tvItemName.setTextColor(0xFFFFFFFF);
                }

                h.cbCheck.setOnCheckedChangeListener((btn, isChecked) -> toggleChecked(item));

                // Long press to delete
                h.itemView.setOnLongClickListener(v -> {
                    deleteItem(item);
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return flatList.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvCategory;
            HeaderViewHolder(View v) {
                super(v);
                tvCategory = v.findViewById(R.id.tvCategory);
            }
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbCheck;
            TextView tvItemName, tvQuantity;
            ItemViewHolder(View v) {
                super(v);
                cbCheck = v.findViewById(R.id.cbCheck);
                tvItemName = v.findViewById(R.id.tvItemName);
                tvQuantity = v.findViewById(R.id.tvQuantity);
            }
        }
    }
}
