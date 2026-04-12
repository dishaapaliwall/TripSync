package com.yay.tripsync;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CountryRegionActivity extends AppCompatActivity {

    private List<String> countryList;
    private List<String> filteredList;
    private CountryAdapter adapter;
    private String selectedCountry = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_country_region);

        initCountryList();

        ImageView btnBack = findViewById(R.id.btnBack);
        TextView btnSave = findViewById(R.id.btnSave);
        EditText searchEditText = findViewById(R.id.searchEditText);
        RecyclerView rvCountries = findViewById(R.id.rvCountries);

        btnBack.setOnClickListener(v -> finish());

        rvCountries.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CountryAdapter();
        rvCountries.setAdapter(adapter);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Fetch current location from Firestore (consistent with Personal Info)
        fetchCurrentLocation();

        btnSave.setOnClickListener(v -> {
            if (selectedCountry.isEmpty()) {
                Toast.makeText(this, "Please select a country", Toast.LENGTH_SHORT).show();
            } else {
                saveToFirebase(selectedCountry);
            }
        });
    }

    private void fetchCurrentLocation() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Using "location" key to match PersonalInformationActivity
                            selectedCountry = documentSnapshot.getString("location");
                            if (selectedCountry == null) selectedCountry = "";
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
    }

    private void initCountryList() {
        countryList = new ArrayList<>();
        String[] locales = Locale.getISOCountries();
        for (String countryCode : locales) {
            Locale obj = new Locale("", countryCode);
            countryList.add(obj.getDisplayCountry());
        }
        Collections.sort(countryList);
        filteredList = new ArrayList<>(countryList);
    }

    private void filter(String text) {
        filteredList = new ArrayList<>();
        for (String item : countryList) {
            if (item.toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void saveToFirebase(String country) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("location", country); // Matching PersonalInformationActivity's field

            FirebaseFirestore.getInstance().collection("users").document(userId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Location saved: " + country, Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show());
        }
    }

    private class CountryAdapter extends RecyclerView.Adapter<CountryAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_country, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String country = filteredList.get(position);
            holder.tvName.setText(country);
            
            if (country.equals(selectedCountry)) {
                holder.ivCheck.setVisibility(View.VISIBLE);
                holder.tvName.setTextColor(0xFF5AC8FA);
            } else {
                holder.ivCheck.setVisibility(View.GONE);
                holder.tvName.setTextColor(0xFFFFFFFF);
            }

            holder.itemView.setOnClickListener(v -> {
                selectedCountry = country;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivCheck;
            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.tvCountryName);
                ivCheck = view.findViewById(R.id.ivCheck);
            }
        }
    }
}