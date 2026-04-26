package com.yay.tripsync;

import android.app.AlertDialog;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChecklistFragment extends Fragment {

    private RecyclerView rvChecklist;
    private TextView tvEmpty, tvProgressCount, tvReadyBadge;
    private ProgressBar progressBar;
    private LinearLayout layoutTeamMembers;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String tripCode;
    private String tripDocId;
    private String currentUid;

    private List<ChecklistItem> checklistItems = new ArrayList<>();
    private ChecklistAdapter adapter;

    // Real-time listener for team ready statuses
    private ListenerRegistration teamStatusListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_checklist, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUid = auth.getUid();

        if (getActivity() != null) {
            tripCode = getActivity().getIntent().getStringExtra("tripId");
        }

        rvChecklist        = v.findViewById(R.id.rvChecklist);
        tvEmpty            = v.findViewById(R.id.tvEmpty);
        tvProgressCount    = v.findViewById(R.id.tvProgressCount);
        progressBar        = v.findViewById(R.id.progressBar);
        tvReadyBadge       = v.findViewById(R.id.tvReadyBadge);
        layoutTeamMembers  = v.findViewById(R.id.layoutTeamMembers);

        v.findViewById(R.id.fabAdd).setOnClickListener(view -> showAddDialog());

        rvChecklist.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChecklistAdapter();
        rvChecklist.setAdapter(adapter);

        if (tripCode != null) {
            resolveTripDocId();
        }

        return v;
    }

    // ── Firestore resolution ──────────────────────────────────────────────────

    private void resolveTripDocId() {
        db.collection("trips")
                .whereEqualTo("tripCode", tripCode)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        tripDocId = query.getDocuments().get(0).getId();
                        loadChecklist();
                        listenToTeamStatus();
                    }
                });
    }

    // ── Private checklist for this user ──────────────────────────────────────
    // Path: trips/{tripDocId}/checklists/{uid}/items

    private void loadChecklist() {
        if (currentUid == null) return;

        db.collection("trips").document(tripDocId)
                .collection("checklists").document(currentUid)
                .collection("items")
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
                                Boolean.TRUE.equals(doc.getBoolean("checked")),
                                currentUid
                        );
                        checklistItems.add(item);
                    }

                    adapter.updateData();
                    updateProgress();
                    tvEmpty.setVisibility(checklistItems.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    // ── Progress bar + ready badge + sync to Firestore ────────────────────────

    private void updateProgress() {
        int total = checklistItems.size();
        int packed = 0;
        for (ChecklistItem item : checklistItems) {
            if (item.isChecked()) packed++;
        }

        tvProgressCount.setText(packed + " / " + total + " items packed");

        if (total > 0) {
            progressBar.setMax(total);
            progressBar.setProgress(packed);
        } else {
            progressBar.setProgress(0);
        }

        boolean allDone = total > 0 && packed == total;
        tvReadyBadge.setVisibility(allDone ? View.VISIBLE : View.GONE);

        // Publish this user's ready status so teammates can see it
        syncReadyStatus(allDone);
    }

    /**
     * Writes { ready, displayName } to trips/{tripDocId}/checklists/{uid}.
     * This parent doc is shared/readable; only the items subcollection is private.
     */
    private void syncReadyStatus(boolean isReady) {
        if (tripDocId == null || currentUid == null) return;

        String displayName = "Member";
        if (auth.getCurrentUser() != null && auth.getCurrentUser().getEmail() != null) {
            String email = auth.getCurrentUser().getEmail();
            displayName = email.contains("@") ? email.split("@")[0] : email;
        }

        Map<String, Object> status = new HashMap<>();
        status.put("ready", isReady);
        status.put("uid", currentUid);
        status.put("displayName", displayName);

        db.collection("trips").document(tripDocId)
                .collection("checklists").document(currentUid)
                .set(status, SetOptions.merge());
    }

    // ── Team status panel ─────────────────────────────────────────────────────

    /**
     * Listens to all documents in trips/{tripDocId}/checklists (one per member who
     * has opened their checklist at least once). Each doc has { ready, displayName }.
     * Renders the team status panel in real-time — entirely inside ChecklistFragment.
     */
    private void listenToTeamStatus() {
        if (tripDocId == null) return;

        teamStatusListener = db.collection("trips").document(tripDocId)
                .collection("checklists")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || getContext() == null) return;
                    renderTeamStatus(snapshots.getDocuments());
                });
    }

    private void renderTeamStatus(List<DocumentSnapshot> docs) {
        layoutTeamMembers.removeAllViews();

        for (DocumentSnapshot doc : docs) {
            Boolean isReady = doc.getBoolean("ready");
            String displayName = doc.getString("displayName");
            if (displayName == null || displayName.isEmpty()) displayName = "Member";

            // Build one row per member: [dot] [name]   [badge or waiting]
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(5);
            row.setLayoutParams(rowParams);

            // Colored dot
            TextView dot = new TextView(getContext());
            dot.setText("●");
            dot.setTextSize(8);
            dot.setTextColor(Boolean.TRUE.equals(isReady) ? 0xFF4CAF50 : 0xFF555555);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dotParams.setMarginEnd(dpToPx(7));
            dot.setLayoutParams(dotParams);

            // Name — highlight "you" slightly
            TextView tvName = new TextView(getContext());
            boolean isMe = doc.getId().equals(currentUid);
            tvName.setText(isMe ? "You" : capitalize(displayName));
            tvName.setTextSize(12);
            tvName.setTextColor(isMe ? 0xFFFFFFFF : 0xFFCCCCCC);
            tvName.setTypeface(null, isMe ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameParams);

            // Status label on the right
            TextView tvStatus = new TextView(getContext());
            if (Boolean.TRUE.equals(isReady)) {
                tvStatus.setText("✓ Ready");
                tvStatus.setTextColor(0xFF4CAF50);
                tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tvStatus.setText("Packing…");
                tvStatus.setTextColor(0xFF555555);
            }
            tvStatus.setTextSize(11);

            row.addView(dot);
            row.addView(tvName);
            row.addView(tvStatus);

            layoutTeamMembers.addView(row);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Add dialog ────────────────────────────────────────────────────────────

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_checklist_item, null);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        AutoCompleteTextView actvItemName = dialogView.findViewById(R.id.actvItemName);

        String[] itemSuggestions = {
                "T-Shirts", "Shorts", "Jeans", "Flip Flops", "Sandals", "Sneakers",
                "Swimwear", "Sunglasses", "Cap / Hat", "Jacket", "Raincoat",
                "Phone Charger", "Power Bank", "Earphones / AirPods", "Camera",
                "Laptop", "Laptop Charger", "Travel Adapter",
                "Aadhar Card", "Passport", "Driving License", "Hotel Booking",
                "Flight Tickets", "Travel Insurance", "Visa Documents",
                "Toothbrush", "Toothpaste", "Shampoo", "Body Wash", "Deodorant",
                "Face Wash", "Sunscreen", "Moisturizer", "Razor",
                "Paracetamol", "ORS Sachets", "Band-Aids", "Antiseptic Cream",
                "Motion Sickness Pills", "Personal Prescription Medicines",
                "Water Bottle", "Snack Bars", "Instant Noodles", "Dry Fruits",
                "Travel Pillow", "Eye Mask", "Umbrella", "Luggage Lock", "Cash"
        };

        ArrayAdapter<String> nameAdapter = new ArrayAdapter<String>(
                getContext(), android.R.layout.simple_dropdown_item_1line, itemSuggestions) {

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
        actvItemName.setThreshold(1);
        actvItemName.setOnClickListener(vv -> actvItemName.showDropDown());
        actvItemName.setOnFocusChangeListener((vv, hasFocus) -> {
            if (hasFocus) actvItemName.showDropDown();
        });

        EditText etQuantity = dialogView.findViewById(R.id.etQuantity);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinnerCategory);

        String[] categories = {
                "Clothing", "Electronics", "Documents",
                "Toiletries", "Medicines", "Food & Snacks", "Other"
        };

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

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(vv -> dialog.dismiss());
        dialogView.findViewById(R.id.btnAdd).setOnClickListener(vv -> {
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

    // ── Firestore writes ──────────────────────────────────────────────────────

    private void saveItem(String name, String category, int qty) {
        if (tripDocId == null || currentUid == null) return;

        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("category", category);
        item.put("quantity", qty);
        item.put("checked", false);
        item.put("addedByUid", currentUid);

        db.collection("trips").document(tripDocId)
                .collection("checklists").document(currentUid)
                .collection("items")
                .add(item)
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to add item", Toast.LENGTH_SHORT).show());
    }

    private void toggleChecked(ChecklistItem item) {
        if (tripDocId == null || currentUid == null) return;
        db.collection("trips").document(tripDocId)
                .collection("checklists").document(currentUid)
                .collection("items")
                .document(item.getId())
                .update("checked", !item.isChecked());
    }

    private void deleteItem(ChecklistItem item) {
        if (tripDocId == null || currentUid == null) return;
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete Item")
                .setMessage("Remove \"" + item.getName() + "\" from your checklist?")
                .setPositiveButton("Delete", (d, w) ->
                        db.collection("trips").document(tripDocId)
                                .collection("checklists").document(currentUid)
                                .collection("items")
                                .document(item.getId())
                                .delete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (teamStatusListener != null) teamStatusListener.remove();
    }

    // ── RecyclerView Adapter ──────────────────────────────────────────────────

    private class ChecklistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM   = 1;

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
                    flatList.add(item.getCategory());
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
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View vv = inf.inflate(R.layout.item_checklist_header, parent, false);
                return new HeaderViewHolder(vv);
            } else {
                View vv = inf.inflate(R.layout.item_checklist, parent, false);
                return new ItemViewHolder(vv);
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

                h.cbCheck.setOnCheckedChangeListener(null);
                h.cbCheck.setChecked(item.isChecked());

                if (item.isChecked()) {
                    h.tvItemName.setPaintFlags(h.tvItemName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    h.tvItemName.setTextColor(0xFF666666);
                } else {
                    h.tvItemName.setPaintFlags(h.tvItemName.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                    h.tvItemName.setTextColor(0xFFFFFFFF);
                }

                h.cbCheck.setOnCheckedChangeListener((btn, isChecked) -> toggleChecked(item));

                h.itemView.setOnLongClickListener(vv -> {
                    deleteItem(item);
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() { return flatList.size(); }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvCategory;
            HeaderViewHolder(View vv) {
                super(vv);
                tvCategory = vv.findViewById(R.id.tvCategory);
            }
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbCheck;
            TextView tvItemName, tvQuantity;
            ItemViewHolder(View vv) {
                super(vv);
                cbCheck    = vv.findViewById(R.id.cbCheck);
                tvItemName = vv.findViewById(R.id.tvItemName);
                tvQuantity = vv.findViewById(R.id.tvQuantity);
            }
        }
    }
}
