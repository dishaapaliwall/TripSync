package com.yay.tripsync;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class NewTripActivity extends AppCompatActivity {

    EditText etName, etLocation, etStart, etEnd, etBudget;
    RelativeLayout btnSelectFriends;
    TextView tvSelectedCount;

    FirebaseFirestore db;
    FirebaseAuth auth;

    List<Map<String, String>> friendList = new ArrayList<>();
    List<Map<String, String>> selectedFriends = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_trip);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.etName);
        etLocation = findViewById(R.id.etLocation);
        etStart = findViewById(R.id.etStart);
        etEnd = findViewById(R.id.etEnd);
        etBudget = findViewById(R.id.etBudget);
        btnSelectFriends = findViewById(R.id.btnSelectFriends);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);

        etStart.setOnClickListener(v -> showDatePicker(etStart));
        etEnd.setOnClickListener(v -> showDatePicker(etEnd));

        btnSelectFriends.setOnClickListener(v -> showFriendsDialog());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreate).setOnClickListener(v -> createTrip());

        loadFriends();
    }

    private void loadFriends() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<Map<String, String>> friends = (List<Map<String, String>>) documentSnapshot.get("friends_list");
                        if (friends != null) {
                            friendList.clear();
                            friendList.addAll(friends);
                        }
                    }
                });
    }

    private void showFriendsDialog() {
        if (friendList.isEmpty()) {
            Toast.makeText(this, "You have no friends to invite. Add some from Profile!", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_select_friends, null);
        bottomSheetDialog.setContentView(view);

        RecyclerView rvSelectFriends = view.findViewById(R.id.rvSelectFriends);
        rvSelectFriends.setLayoutManager(new LinearLayoutManager(this));
        
        FriendSelectAdapter adapter = new FriendSelectAdapter(friendList, selectedFriends);
        rvSelectFriends.setAdapter(adapter);

        view.findViewById(R.id.btnDone).setOnClickListener(v -> {
            selectedFriends = adapter.getSelectedFriends();
            if (selectedFriends.isEmpty()) {
                tvSelectedCount.setText("Select Friends");
                tvSelectedCount.setTextColor(0xFF888888);
            } else {
                tvSelectedCount.setText(selectedFriends.size() + " friends selected");
                tvSelectedCount.setTextColor(0xFFFFFFFF);
            }
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void showDatePicker(final EditText editText) {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String date = dayOfMonth + "/" + (monthOfYear + 1) + "/" + year1;
                    editText.setText(date);
                }, year, month, day);
        
        // 🔥 Optional: Set minimum date to today so user can't even select past dates in the picker
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        
        datePickerDialog.show();
    }

    private String generateTripCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return "TRIP-" + sb.toString();
    }

    private void createTrip() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String name = etName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String start = etStart.getText().toString().trim();
        String end = etEnd.getText().toString().trim();
        String budgetStr = etBudget.getText().toString().trim();

        if (name.isEmpty() || location.isEmpty() || start.isEmpty() || end.isEmpty() || budgetStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔥 Determine Status based on Date logic
        String calculatedStatus = "Upcoming";
        SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
        try {
            Date startDate = sdf.parse(start.replace(".", "/"));
            Date endDate = sdf.parse(end.replace(".", "/"));
            
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date todayMidnight = cal.getTime();

            // 🔥 Loophole Fix: Check if start date is in the past
            if (startDate != null && startDate.before(todayMidnight)) {
                Toast.makeText(this, "Start date cannot be in the past!", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Also ensure end date is not before start date
            if (endDate != null && startDate != null && endDate.before(startDate)) {
                Toast.makeText(this, "End date cannot be before start date!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (endDate != null && endDate.before(todayMidnight)) {
                calculatedStatus = "Completed";
            } else if (startDate != null && !startDate.after(todayMidnight)) {
                calculatedStatus = "Ongoing";
            } else {
                calculatedStatus = "Upcoming";
            }
        } catch (ParseException e) {
            Log.e("NewTrip", "Date parse error.", e);
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show();
            return;
        }

        final String status = calculatedStatus;

        double budgetValue;
        try {
            budgetValue = Double.parseDouble(budgetStr);
        } catch (NumberFormatException e) {
            budgetValue = 0.0;
        }

        String tripCode = generateTripCode();

        // 🔥 Handle Invited Friends
        List<String> invitedEmails = new ArrayList<>();
        for (Map<String, String> friend : selectedFriends) {
            invitedEmails.add(friend.get("email").toLowerCase().trim());
        }

        HashMap<String, Object> trip = new HashMap<>();
        trip.put("name", name);
        trip.put("location", location);
        trip.put("startDate", start);
        trip.put("endDate", end);
        trip.put("status", status);
        trip.put("budget", budgetValue);
        trip.put("spent", 0.0);
        trip.put("userId", user.getUid());
        trip.put("imageUrl", "");
        trip.put("tripCode", tripCode);
        trip.put("invitedEmails", invitedEmails); // 🔥 Save list of invited emails
        trip.put("participants", Arrays.asList(user.getEmail().toLowerCase().trim())); // Creator is first participant

        db.collection("trips")
                .add(trip)
                .addOnSuccessListener(documentReference -> {
                    sendInvites(documentReference.getId(), name, invitedEmails);
                    Toast.makeText(this, "Trip Created! Invites sent to " + invitedEmails.size() + " friends.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void sendInvites(String tripId, String tripName, List<String> emails) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        for (String email : emails) {
            Map<String, Object> invite = new HashMap<>();
            invite.put("type", "trip_invite");
            invite.put("tripId", tripId);
            invite.put("tripName", tripName);
            invite.put("fromName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0]);
            invite.put("fromEmail", user.getEmail());
            invite.put("toEmail", email);
            invite.put("status", "pending");
            invite.put("timestamp", FieldValue.serverTimestamp());

            db.collection("notifications").add(invite);
        }
    }

    private class FriendSelectAdapter extends RecyclerView.Adapter<FriendSelectAdapter.ViewHolder> {
        private List<Map<String, String>> friends;
        private List<Map<String, String>> selected;

        public FriendSelectAdapter(List<Map<String, String>> friends, List<Map<String, String>> selected) {
            this.friends = friends;
            this.selected = new ArrayList<>(selected);
        }

        public List<Map<String, String>> getSelectedFriends() {
            return selected;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_select, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, String> friend = friends.get(position);
            holder.tvName.setText(friend.get("name"));
            holder.tvEmail.setText(friend.get("email"));

            boolean isSelected = false;
            for (Map<String, String> s : selected) {
                if (s.get("email").equals(friend.get("email"))) {
                    isSelected = true;
                    break;
                }
            }
            holder.cbSelect.setChecked(isSelected);

            holder.itemView.setOnClickListener(v -> {
                holder.cbSelect.setChecked(!holder.cbSelect.isChecked());
                updateSelected(friend, holder.cbSelect.isChecked());
            });

            holder.cbSelect.setOnClickListener(v -> {
                updateSelected(friend, holder.cbSelect.isChecked());
            });
        }

        private void updateSelected(Map<String, String> friend, boolean isChecked) {
            if (isChecked) {
                boolean alreadyIn = false;
                for (Map<String, String> s : selected) {
                    if (s.get("email").equals(friend.get("email"))) {
                        alreadyIn = true;
                        break;
                    }
                }
                if (!alreadyIn) selected.add(friend);
            } else {
                selected.removeIf(s -> s.get("email").equals(friend.get("email")));
            }
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvEmail;
            CheckBox cbSelect;

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvFriendName);
                tvEmail = v.findViewById(R.id.tvFriendEmail);
                cbSelect = v.findViewById(R.id.cbSelect);
            }
        }
    }
}