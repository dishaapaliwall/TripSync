package com.yay.tripsync;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class PersonalInformationActivity extends AppCompatActivity {

    private EditText editName, editEmail, editPhone, editDOB, editLocation;
    private Spinner spinGender;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_information);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize Views
        ImageView btnBack = findViewById(R.id.btnBack);
        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editPhone = findViewById(R.id.editPhone);
        editDOB = findViewById(R.id.editDOB);
        editLocation = findViewById(R.id.editLocation);
        spinGender = findViewById(R.id.spinGender);
        Button btnSave = findViewById(R.id.btnSave);

        btnBack.setOnClickListener(v -> finish());

        // Setup Gender Spinner with Hint
        setupGenderSpinner();

        // Date Picker for DOB
        editDOB.setOnClickListener(v -> showDatePicker());

        loadUserData();

        btnSave.setOnClickListener(v -> saveUserData());
    }

    private void setupGenderSpinner() {
        String[] genders = {"Gender","Male", "Female", "Other"};

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                genders
        ) {
            @Override
            public boolean isEnabled(int position) {
                return position != 0; // disable "Gender"
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v;

                if (position == 0) {
                    tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                } else {
                    tv.setTextColor(getResources().getColor(android.R.color.white));
                }

                return v;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) v;

                v.setBackgroundColor(getResources().getColor(android.R.color.black));

                if (position == 0) {
                    tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
                } else {
                    tv.setTextColor(getResources().getColor(android.R.color.white));
                }

                return v;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinGender.setAdapter(adapter);
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String date = year1 + "/" + (monthOfYear + 1) + "/" + dayOfMonth;
                    editDOB.setText(date);
                }, year, month, day);
        datePickerDialog.show();
    }

    private void loadUserData() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            editEmail.setText(user.getEmail());
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            editName.setText(documentSnapshot.getString("name"));
                            editPhone.setText(documentSnapshot.getString("phone"));
                            editDOB.setText(documentSnapshot.getString("dob"));
                            editLocation.setText(documentSnapshot.getString("location"));
                            
                            String gender = documentSnapshot.getString("gender");
                            if (gender != null && !gender.isEmpty()) {
                                ArrayAdapter adapter = (ArrayAdapter) spinGender.getAdapter();
                                int pos = adapter.getPosition(gender);
                                if (pos >= 0) spinGender.setSelection(pos);
                            }
                        } else {
                            editName.setText(user.getDisplayName());
                        }
                    });
        }
    }

    private void saveUserData() {
        String name = editName.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();
        String dob = editDOB.getText().toString().trim();
        String location = editLocation.getText().toString().trim();
        String gender = spinGender.getSelectedItem().toString();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("name", name);
            userData.put("phone", phone);
            userData.put("dob", dob);
            userData.put("location", location);
            
            // Don't save "Gender" if it's the hint
            userData.put("gender", gender.equals("Gender") ? "" : gender);

            db.collection("users").document(user.getUid()).set(userData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(PersonalInformationActivity.this, "Changes Saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(PersonalInformationActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }
}