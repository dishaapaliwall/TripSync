package com.yay.tripsync;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItineraryFragment extends Fragment {

    private FirebaseFirestore db;
    private String tripCode;
    private String firestoreDocId;
    private String tripStartDate, tripEndDate;

    private RecyclerView  rvItinerary;
    private LinearLayout  layoutEmpty;
    private ItineraryAdapter adapter;

    private final List<Object>  rows      = new ArrayList<>();
    private final List<String>  dayLabels = new ArrayList<>();
    private final List<String>  dayKeys   = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_itinerary, container, false);

        db = FirebaseFirestore.getInstance();

        if (getActivity() != null) {
            tripCode      = getActivity().getIntent().getStringExtra("tripId");
            tripStartDate = getActivity().getIntent().getStringExtra("startDate");
            tripEndDate   = getActivity().getIntent().getStringExtra("endDate");
        }

        rvItinerary = root.findViewById(R.id.rvItinerary);
        layoutEmpty  = root.findViewById(R.id.layoutEmpty);

        adapter = new ItineraryAdapter(rows);
        rvItinerary.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvItinerary.setAdapter(adapter);

        root.findViewById(R.id.fabAdd).setOnClickListener(v -> {
            if (firestoreDocId == null) {
                Toast.makeText(requireContext(), "Loading...", Toast.LENGTH_SHORT).show();
                return;
            }
            showAddActivitySheet();
        });

        buildDayStructure();
        resolveDocAndListen();

        return root;
    }

    // Build day label + key lists from trip date range
    private void buildDayStructure() {
        dayLabels.clear();
        dayKeys.clear();

        if (tripStartDate == null || tripEndDate == null) return;

        SimpleDateFormat[] formats = {
                new SimpleDateFormat("d/M/yyyy",   Locale.getDefault()),
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
                new SimpleDateFormat("M/d/yyyy",   Locale.getDefault())
        };

        Date start = null, end = null;
        for (SimpleDateFormat fmt : formats) {
            try {
                start = fmt.parse(tripStartDate);
                end   = fmt.parse(tripEndDate);
                if (start != null && end != null) break;
            } catch (ParseException ignored) {}
        }

        if (start == null || end == null) return;

        SimpleDateFormat display = new SimpleDateFormat("MMM d", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        cal.setTime(start);
        int dayNum = 1;

        while (!cal.getTime().after(end)) {
            dayLabels.add("Day " + dayNum + " \u2014 " + display.format(cal.getTime()));
            dayKeys.add("day_" + dayNum);
            cal.add(Calendar.DATE, 1);
            dayNum++;
        }
    }

    // Resolve Firestore doc from tripCode, then listen
    private void resolveDocAndListen() {
        if (tripCode == null) return;

        db.collection("trips")
                .whereEqualTo("tripCode", tripCode)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) return;
                    firestoreDocId = snap.getDocuments().get(0).getId();
                    listenToItinerary();
                });
    }

    private void listenToItinerary() {
        db.collection("trips")
                .document(firestoreDocId)
                .collection("itinerary")
                .orderBy("sortKey", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    // Group by dayKey
                    Map<String, List<ActivityItem>> grouped = new HashMap<>();
                    for (QueryDocumentSnapshot doc : value) {
                        String dayKey  = doc.getString("dayKey");
                        String time    = doc.getString("time");
                        String title   = doc.getString("title");
                        String location= doc.getString("location");
                        if (dayKey == null || dayKey.isEmpty()) dayKey = "day_1";

                        ActivityItem item = new ActivityItem(
                                doc.getId(), dayKey, time, title, location);
                        grouped.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(item);
                    }

                    // Sort each day by time
                    for (List<ActivityItem> list : grouped.values()) {
                        Collections.sort(list, (a, b) -> {
                            if (a.time == null) return 1;
                            if (b.time == null) return -1;
                            return a.time.compareTo(b.time);
                        });
                    }

                    // Rebuild flat rows — day header only if that day has activities
                    rows.clear();
                    if (dayKeys.isEmpty()) {
                        for (List<ActivityItem> acts : grouped.values()) {
                            rows.addAll(acts);
                        }
                    } else {
                        for (int i = 0; i < dayKeys.size(); i++) {
                            String key  = dayKeys.get(i);
                            String label= dayLabels.get(i);
                            List<ActivityItem> acts = grouped.get(key);
                            if (acts != null && !acts.isEmpty()) {
                                rows.add(new DayHeader(label));
                                rows.addAll(acts);
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();

                    // Toggle empty state vs list
                    boolean hasActivities = grouped.values()
                            .stream().anyMatch(l -> !l.isEmpty());
                    layoutEmpty.setVisibility(hasActivities ? View.GONE  : View.VISIBLE);
                    rvItinerary.setVisibility(hasActivities ? View.VISIBLE : View.GONE);
                });
    }

    // Bottom Sheet — Add Activity
    private void showAddActivitySheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_add_activity, null);
        sheet.setContentView(v);

        Spinner spinnerDay = v.findViewById(R.id.spinnerDay);
        List<String> spinLabels = dayLabels.isEmpty() ? buildFallbackDays() : dayLabels;
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item, spinLabels);
        spinAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinnerDay.setAdapter(spinAdapter);

        EditText etTime = v.findViewById(R.id.etTime);
        etTime.setOnClickListener(view -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(requireContext(), (tp, hour, min) -> {
                String amPm = hour < 12 ? "AM" : "PM";
                int h = hour % 12;
                if (h == 0) h = 12;
                etTime.setText(String.format(Locale.getDefault(),
                        "%02d:%02d %s", h, min, amPm));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
        });

        EditText etTitle    = v.findViewById(R.id.etActivityTitle);
        EditText etLocation = v.findViewById(R.id.etActivityLocation);

        v.findViewById(R.id.btnAddActivity).setOnClickListener(btn -> {
            String time     = etTime.getText().toString().trim();
            String title    = etTitle.getText().toString().trim();
            String location = etLocation.getText().toString().trim();

            if (title.isEmpty()) { etTitle.setError("Enter activity name"); return; }
            if (time.isEmpty())  { etTime.setError("Pick a time");          return; }

            int idx = spinnerDay.getSelectedItemPosition();
            String selectedKey   = idx < dayKeys.size()    ? dayKeys.get(idx)    : "day_1";
            String selectedLabel = idx < spinLabels.size() ? spinLabels.get(idx) : "Day 1";

            saveActivity(selectedKey, selectedLabel, time, title, location);
            sheet.dismiss();
        });

        sheet.show();
    }

    private List<String> buildFallbackDays() {
        List<String> f = new ArrayList<>();
        f.add("Day 1");
        return f;
    }

    private void saveActivity(String dayKey, String dayLabel,
                              String time, String title, String location) {
        Map<String, Object> data = new HashMap<>();
        data.put("dayKey",   dayKey);
        data.put("dayLabel", dayLabel);
        data.put("time",     time);
        data.put("title",    title);
        data.put("location", location);
        data.put("sortKey",  dayKey + "_" + time);

        db.collection("trips")
                .document(firestoreDocId)
                .collection("itinerary")
                .add(data)
                .addOnSuccessListener(ref ->
                        Toast.makeText(requireContext(),
                                "Activity added!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void deleteActivity(String docId) {
        db.collection("trips")
                .document(firestoreDocId)
                .collection("itinerary")
                .document(docId)
                .delete()
                .addOnSuccessListener(u ->
                        Toast.makeText(requireContext(),
                                "Removed", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // Data models
    static class DayHeader {
        String label;
        DayHeader(String label) { this.label = label; }
    }

    static class ActivityItem {
        String docId, dayKey, time, title, location;
        ActivityItem(String docId, String dayKey,
                     String time, String title, String location) {
            this.docId    = docId;
            this.dayKey   = dayKey;
            this.time     = time;
            this.title    = title;
            this.location = location;
        }
    }

    // Adapter
    private class ItineraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        static final int TYPE_HEADER   = 0;
        static final int TYPE_ACTIVITY = 1;

        private final List<Object> rows;
        ItineraryAdapter(List<Object> rows) { this.rows = rows; }

        @Override
        public int getItemViewType(int pos) {
            return rows.get(pos) instanceof DayHeader ? TYPE_HEADER : TYPE_ACTIVITY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH(inf.inflate(R.layout.item_day_header, parent, false));
            } else {
                return new ActivityVH(inf.inflate(R.layout.item_itinerary, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).tvDayHeader
                        .setText(((DayHeader) rows.get(pos)).label);
            } else {
                ActivityItem item = (ActivityItem) rows.get(pos);
                ActivityVH   vh   = (ActivityVH) holder;

                vh.tvTime.setText(item.time != null ? item.time : "");
                vh.tvTitle.setText(item.title != null ? item.title : "");

                boolean hasLoc = item.location != null && !item.location.isEmpty();
                vh.layoutLocation.setVisibility(hasLoc ? View.VISIBLE : View.GONE);
                if (hasLoc) vh.tvLocation.setText(item.location);

                vh.btnDelete.setOnClickListener(v -> deleteActivity(item.docId));
            }
        }

        @Override public int getItemCount() { return rows.size(); }

        class HeaderVH extends RecyclerView.ViewHolder {
            TextView tvDayHeader;
            HeaderVH(View v) {
                super(v);
                tvDayHeader = v.findViewById(R.id.tvDayHeader);
            }
        }

        class ActivityVH extends RecyclerView.ViewHolder {
            TextView     tvTime, tvTitle, tvLocation;
            LinearLayout layoutLocation;
            View         btnDelete;
            ActivityVH(View v) {
                super(v);
                tvTime         = v.findViewById(R.id.tvTime);
                tvTitle        = v.findViewById(R.id.tvTitle);
                tvLocation     = v.findViewById(R.id.tvLocation);
                layoutLocation = v.findViewById(R.id.layoutLocation);
                btnDelete      = v.findViewById(R.id.btnDelete);
            }
        }
    }
}