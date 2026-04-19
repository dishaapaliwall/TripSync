package com.yay.tripsync;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItineraryFragment extends Fragment {

    private RecyclerView rvItinerary;
    private TextView tvEmpty;
    private List<Map<String, Object>> itineraryList = new ArrayList<>();
    private ItineraryAdapter adapter;
    private FirebaseFirestore db;
    private String tripCode;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_itinerary, container, false);

        db = FirebaseFirestore.getInstance();
        if (getActivity() != null) {
            tripCode = getActivity().getIntent().getStringExtra("tripId");
        }

        rvItinerary = v.findViewById(R.id.rvItinerary);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        v.findViewById(R.id.fabAdd).setOnClickListener(view -> showAddDialog());

        rvItinerary.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ItineraryAdapter();
        rvItinerary.setAdapter(adapter);

        if (tripCode != null) {
            loadItinerary();
        }

        return v;
    }

    private void loadItinerary() {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                db.collection("trips").document(docId).collection("itinerary")
                        .orderBy("time", Query.Direction.ASCENDING)
                        .addSnapshotListener((value, error) -> {
                            if (error != null) return;
                            itineraryList.clear();
                            if (value != null) {
                                for (QueryDocumentSnapshot doc : value) {
                                    itineraryList.add(doc.getData());
                                }
                            }
                            adapter.notifyDataSetChanged();
                            tvEmpty.setVisibility(itineraryList.isEmpty() ? View.VISIBLE : View.GONE);
                        });
            }
        });
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.activity_join_trip, null); // Reusing a simple layout structure or creating a quick one
        // For simplicity, let's use a basic AlertDialog with custom views
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Add Itinerary");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText etTime = new EditText(getContext());
        etTime.setHint("Time (click to set)");
        etTime.setFocusable(false);
        etTime.setTextColor(0xFFFFFFFF);
        etTime.setOnClickListener(view -> {
            Calendar mcurrentTime = Calendar.getInstance();
            int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
            int minute = mcurrentTime.get(Calendar.MINUTE);
            TimePickerDialog mTimePicker = new TimePickerDialog(getContext(), (timePicker, selectedHour, selectedMinute) -> {
                etTime.setText(String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute));
            }, hour, minute, false);
            mTimePicker.show();
        });

        final EditText etTitle = new EditText(getContext());
        etTitle.setHint("Activity Name");
        etTitle.setTextColor(0xFFFFFFFF);

        final EditText etLoc = new EditText(getContext());
        etLoc.setHint("Location");
        etLoc.setTextColor(0xFFFFFFFF);

        layout.addView(etTime);
        layout.addView(etTitle);
        layout.addView(etLoc);
        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String time = etTime.getText().toString();
            String title = etTitle.getText().toString();
            String loc = etLoc.getText().toString();

            if (!time.isEmpty() && !title.isEmpty()) {
                saveToFirebase(time, title, loc);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveToFirebase(String time, String title, String loc) {
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                Map<String, Object> item = new HashMap<>();
                item.put("time", time);
                item.put("title", title);
                item.put("location", loc);

                db.collection("trips").document(docId).collection("itinerary").add(item);
            }
        });
    }

    private class ItineraryAdapter extends RecyclerView.Adapter<ItineraryAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_itinerary, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> item = itineraryList.get(position);
            holder.tvTime.setText((String)item.get("time"));
            holder.tvTitle.setText((String)item.get("title"));
            holder.tvLocation.setText((String)item.get("location"));
        }

        @Override
        public int getItemCount() { return itineraryList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTime, tvTitle, tvLocation;
            ViewHolder(View v) {
                super(v);
                tvTime = v.findViewById(R.id.tvTime);
                tvTitle = v.findViewById(R.id.tvTitle);
                tvLocation = v.findViewById(R.id.tvLocation);
            }
        }
    }
}
