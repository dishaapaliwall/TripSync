package com.yay.tripsync;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
import java.util.Random;

public class NewTripActivity extends AppCompatActivity {

    EditText etName, etLocation, etStart, etEnd, etBudget, etInvite;

    FirebaseFirestore db;
    FirebaseAuth auth;

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
        etInvite = findViewById(R.id.etInvite);

        etStart.setOnClickListener(v -> showDatePicker(etStart));
        etEnd.setOnClickListener(v -> showDatePicker(etEnd));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreate).setOnClickListener(v -> createTrip());
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
        String inviteStr = etInvite.getText().toString().trim();

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

        // 🔥 Handle Invite Emails
        List<String> invitedEmails = new ArrayList<>();
        if (!inviteStr.isEmpty()) {
            // Split by comma or space
            String[] splitEmails = inviteStr.split("[,\\s]+");
            for (String email : splitEmails) {
                String cleanEmail = email.trim().toLowerCase();
                if (!cleanEmail.isEmpty()) {
                    invitedEmails.add(cleanEmail);
                }
            }
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
                    Toast.makeText(this, "Trip Created! Invites sent to " + invitedEmails.size() + " members.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}