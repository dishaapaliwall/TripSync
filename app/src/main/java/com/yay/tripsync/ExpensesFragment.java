package com.yay.tripsync;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpensesFragment extends Fragment {

    private static final String TAG = "ExpensesFragment";
    private static final String CLOUDINARY_PRESET = "gda3ahqj";

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String tripCode;
    private String firestoreDocId;

    private final List<String> memberNames = new ArrayList<>();
    private final List<String> memberEmails = new ArrayList<>();

    private RecyclerView rvExpenses;
    private TextView tvEmpty, tvOwesHeader;
    private LinearLayout layoutOwes;
    private ProgressBar uploadProgress;
    private ExpenseAdapter adapter;
    private final List<ExpenseItem> expenseList = new ArrayList<>();

    private final ActivityResultLauncher<Intent> receiptPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) uploadReceiptToCloudinary(uri);
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_expenses, container, false);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (getActivity() != null) {
            tripCode = getActivity().getIntent().getStringExtra("tripId");
        }

        rvExpenses = root.findViewById(R.id.rvExpenses);
        tvEmpty = root.findViewById(R.id.tvEmpty);
        tvOwesHeader = root.findViewById(R.id.tvOwesHeader);
        layoutOwes = root.findViewById(R.id.layoutOwes);
        uploadProgress = root.findViewById(R.id.uploadProgress);

        adapter = new ExpenseAdapter(expenseList);
        rvExpenses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvExpenses.setNestedScrollingEnabled(false);
        rvExpenses.setAdapter(adapter);

        root.findViewById(R.id.cardUpload).setOnClickListener(v -> openFilePicker());
        root.findViewById(R.id.fabAdd).setOnClickListener(v -> {
            if (firestoreDocId == null) {
                Toast.makeText(requireContext(), "Loading...", Toast.LENGTH_SHORT).show();
                return;
            }
            showAddExpenseSheet(null);
        });

        resolveDocIdAndLoad();
        return root;
    }

    private void resolveDocIdAndLoad() {
        if (tripCode == null) return;
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(snap -> {
            if (snap.isEmpty()) return;
            DocumentSnapshot tripDoc = snap.getDocuments().get(0);
            firestoreDocId = tripDoc.getId();
            loadMembers(tripDoc);
            listenToExpenses();
        });
    }

    private void loadMembers(DocumentSnapshot tripDoc) {
        String hostUid = tripDoc.getString("userId");
        List<String> participantEmails = new ArrayList<>();
        Object raw = tripDoc.get("participants");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) participantEmails.add(o.toString());
        }

        db.collection("users").get().addOnSuccessListener(usersSnap -> {
            memberNames.clear();
            memberEmails.clear();
            Map<String, DocumentSnapshot> byUid = new HashMap<>();
            Map<String, DocumentSnapshot> byEmail = new HashMap<>();
            for (DocumentSnapshot u : usersSnap) {
                byUid.put(u.getId(), u);
                String e = u.getString("email");
                if (e != null) byEmail.put(e.toLowerCase().trim(), u);
            }
            if (hostUid != null && byUid.containsKey(hostUid)) addMember(byUid.get(hostUid));
            for (String email : participantEmails) {
                DocumentSnapshot u = byEmail.get(email.toLowerCase().trim());
                if (u != null && !u.getId().equals(hostUid)) addMember(u);
            }
        });
    }

    private void addMember(DocumentSnapshot doc) {
        String name = doc.getString("name");
        String email = doc.getString("email");
        if (name == null || name.isEmpty()) name = email != null ? email.split("@")[0] : "Unknown";
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        memberNames.add(name);
        memberEmails.add(email != null ? email : "");
    }

    private void listenToExpenses() {
        db.collection("trips").document(firestoreDocId).collection("expenses")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    expenseList.clear();
                    double totalSpent = 0;
                    for (QueryDocumentSnapshot doc : value) {
                        ExpenseItem item = new ExpenseItem();
                        item.docId = doc.getId();
                        item.description = doc.getString("description");
                        item.amount = doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0;
                        item.paidBy = doc.getString("paidBy");
                        item.receiptUrl = doc.getString("receiptUrl");
                        List<?> rawSplit = (List<?>) doc.get("splitAmong");
                        if (rawSplit != null) for (Object o : rawSplit) item.splitAmong.add(o.toString());
                        expenseList.add(item);
                        totalSpent += item.amount;
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(expenseList.isEmpty() ? View.VISIBLE : View.GONE);
                    calculateAndShowOwes();
                    
                    // Saath ke saath update total spent in the main trip document
                    db.collection("trips").document(firestoreDocId).update("spent", totalSpent);
                });
    }

    private void calculateAndShowOwes() {
        Map<String, Map<String, Double>> net = new HashMap<>();
        for (ExpenseItem expense : expenseList) {
            if (expense.paidBy == null || expense.splitAmong.isEmpty()) continue;
            double share = expense.amount / expense.splitAmong.size();
            for (String person : expense.splitAmong) {
                String debtor = person.trim();
                String creditor = expense.paidBy.trim();
                if (debtor.equalsIgnoreCase(creditor)) continue;
                net.computeIfAbsent(debtor, k -> new HashMap<>());
                double cur = net.get(debtor).getOrDefault(creditor, 0.0);
                net.get(debtor).put(creditor, cur + share);
            }
        }
        for (String a : new ArrayList<>(net.keySet())) {
            Map<String, Double> aOwes = net.get(a);
            for (String b : new ArrayList<>(aOwes.keySet())) {
                double aOwesB = aOwes.getOrDefault(b, 0.0);
                double bOwesA = net.containsKey(b) ? net.get(b).getOrDefault(a, 0.0) : 0.0;
                if (aOwesB > bOwesA) {
                    aOwes.put(b, aOwesB - bOwesA);
                    if (net.containsKey(b)) net.get(b).put(a, 0.0);
                } else {
                    aOwes.put(b, 0.0);
                    if (net.containsKey(b)) net.get(b).put(a, bOwesA - aOwesB);
                }
            }
        }
        layoutOwes.removeAllViews();
        boolean anyDebt = false;
        for (Map.Entry<String, Map<String, Double>> entry : net.entrySet()) {
            for (Map.Entry<String, Double> debt : entry.getValue().entrySet()) {
                if (debt.getValue() < 1.0) continue;
                anyDebt = true;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 8);
                TextView row = new TextView(requireContext());
                row.setText(entry.getKey() + " \u2192 " + debt.getKey() + "   \u20B9" + (int) Math.round(debt.getValue()));
                row.setTextColor(0xFFDDDDDD);
                row.setTextSize(14f);
                row.setPadding(32, 24, 32, 24);
                CardView card = new CardView(requireContext());
                card.setLayoutParams(params);
                card.setRadius(12f);
                card.setCardBackgroundColor(0xFF2A0A0A);
                card.setCardElevation(0f);
                card.addView(row);
                layoutOwes.addView(card);
            }
        }
        tvOwesHeader.setVisibility(anyDebt ? View.VISIBLE : View.GONE);
        layoutOwes.setVisibility(anyDebt ? View.VISIBLE : View.GONE);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "application/pdf"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        receiptPickerLauncher.launch(Intent.createChooser(intent, "Select Receipt"));
    }

    private void uploadReceiptToCloudinary(Uri uri) {
        if (firestoreDocId == null) return;
        uploadProgress.setVisibility(View.VISIBLE);
        MediaManager.get().upload(uri).unsigned(CLOUDINARY_PRESET).option("resource_type", "auto").callback(new UploadCallback() {
            @Override public void onStart(String id) {}
            @Override public void onProgress(String id, long b, long t) {}
            @Override public void onSuccess(String id, Map resultData) {
                String url = (String) resultData.get("secure_url");
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    uploadProgress.setVisibility(View.GONE);
                    showAddExpenseSheet(url);
                });
            }
            @Override public void onError(String id, ErrorInfo error) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    uploadProgress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onReschedule(String id, ErrorInfo error) {}
        }).dispatch();
    }

    private void showAddExpenseSheet(@Nullable String receiptUrl) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_add_expense, null);
        sheet.setContentView(v);
        EditText etDesc = v.findViewById(R.id.etDescription);
        EditText etAmount = v.findViewById(R.id.etAmount);
        Spinner spinPaid = v.findViewById(R.id.spinnerPaidBy);
        LinearLayout layoutCheckboxes = v.findViewById(R.id.layoutMemberCheckboxes);
        TextView tvSelectAll = v.findViewById(R.id.tvSelectAll);
        TextView tvSharePreview = v.findViewById(R.id.tvSharePreview);
        List<String> names = memberNames.isEmpty() ? buildFallbackNames() : new ArrayList<>(memberNames);
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinPaid.setAdapter(spinAdapter);
        final List<CheckBox> checkBoxes = new ArrayList<>();
        layoutCheckboxes.removeAllViews();
        for (int i = 0; i < names.size(); i++) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setText(names.get(i));
            cb.setTextColor(0xFFFFFFFF);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFFF2D55));
            cb.setPadding(8, 16, 8, 16);
            cb.setChecked(true);
            cb.setOnCheckedChangeListener((btn, checked) -> updateSharePreview(etAmount, checkBoxes, tvSharePreview));
            checkBoxes.add(cb);
            layoutCheckboxes.addView(cb);
        }
        tvSelectAll.setOnClickListener(sel -> {
            boolean all = tvSelectAll.getText().toString().equals("Select All");
            for (CheckBox cb : checkBoxes) cb.setChecked(all);
            tvSelectAll.setText(all ? "Deselect All" : "Select All");
        });
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { updateSharePreview(etAmount, checkBoxes, tvSharePreview); }
            @Override public void afterTextChanged(Editable s) {}
        });
        v.findViewById(R.id.btnAddExpense).setOnClickListener(btn -> {
            String desc = etDesc.getText().toString().trim();
            String amtStr = etAmount.getText().toString().trim();
            if (desc.isEmpty() || amtStr.isEmpty()) return;
            double amount = Double.parseDouble(amtStr);
            List<String> splitAmong = new ArrayList<>();
            for (CheckBox cb : checkBoxes) if (cb.isChecked()) splitAmong.add(cb.getText().toString());
            saveExpense(desc, amount, names.get(spinPaid.getSelectedItemPosition()), splitAmong, receiptUrl);
            sheet.dismiss();
        });
        sheet.show();
    }

    private void updateSharePreview(EditText etAmount, List<CheckBox> checkBoxes, TextView tvSharePreview) {
        String amtStr = etAmount.getText().toString().trim();
        int count = 0;
        for (CheckBox cb : checkBoxes) if (cb.isChecked()) count++;
        if (!amtStr.isEmpty() && count > 0) {
            double share = Double.parseDouble(amtStr) / count;
            tvSharePreview.setText("Each pays ₹" + String.format("%.0f", share));
            tvSharePreview.setVisibility(View.VISIBLE);
        } else tvSharePreview.setVisibility(View.GONE);
    }

    private List<String> buildFallbackNames() {
        List<String> f = new ArrayList<>();
        f.add("Me");
        return f;
    }

    private void saveExpense(String description, double amount, String paidBy, List<String> splitAmong, @Nullable String receiptUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("description", description);
        data.put("amount", amount);
        data.put("paidBy", paidBy);
        data.put("splitAmong", splitAmong);
        data.put("receiptUrl", receiptUrl != null ? receiptUrl : "");
        data.put("timestamp", FieldValue.serverTimestamp());
        db.collection("trips").document(firestoreDocId).collection("expenses").add(data);
    }

    private void deleteExpense(String docId) {
        db.collection("trips").document(firestoreDocId).collection("expenses").document(docId).delete();
    }

    static class ExpenseItem {
        String docId, description, paidBy, receiptUrl;
        double amount;
        List<String> splitAmong = new ArrayList<>();
    }

    private class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.VH> {
        private final List<ExpenseItem> list;
        ExpenseAdapter(List<ExpenseItem> list) { this.list = list; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_expense, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            ExpenseItem item = list.get(pos);
            h.tvTitle.setText(item.description + " \u2014 \u20B9" + (int)item.amount);
            h.tvPaidBy.setText("Paid by " + item.paidBy);
            h.tvReceiptTag.setVisibility(item.receiptUrl != null && !item.receiptUrl.isEmpty() ? View.VISIBLE : View.GONE);
            if (item.receiptUrl != null && !item.receiptUrl.isEmpty()) {
                h.imgReceipt.setVisibility(View.VISIBLE);
                Glide.with(requireContext()).load(item.receiptUrl).placeholder(R.drawable.card_bg).into(h.imgReceipt);
            } else h.imgReceipt.setVisibility(View.GONE);
            h.btnDelete.setOnClickListener(v -> deleteExpense(item.docId));
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvPaidBy, tvReceiptTag;
            android.widget.ImageView imgReceipt, btnDelete;
            VH(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvExpenseTitle);
                tvPaidBy = v.findViewById(R.id.tvPaidBy);
                tvReceiptTag = v.findViewById(R.id.tvReceiptTag);
                imgReceipt = v.findViewById(R.id.imgReceipt);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }
}