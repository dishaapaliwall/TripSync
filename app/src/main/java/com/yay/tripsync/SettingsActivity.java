package com.yay.tripsync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        db = FirebaseFirestore.getInstance();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPersonalInfo).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, PersonalInformationActivity.class));
        });

        findViewById(R.id.btnSecurityPrivacy).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, SecurityPrivacyActivity.class));
        });

        findViewById(R.id.btnCountryRegion).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, CountryRegionActivity.class));
        });

        findViewById(R.id.btnAccountManagement).setOnClickListener(v -> {
            showAccountManagementDialog();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                String email = user.getEmail().toLowerCase().trim();
                SharedPreferences prefs = getSharedPreferences("TripSyncAccounts", MODE_PRIVATE);
                
                Set<String> savedEmails = new HashSet<>(prefs.getStringSet("saved_emails", new HashSet<>()));
                savedEmails.remove(email);
                
                prefs.edit()
                        .putStringSet("saved_emails", savedEmails)
                        .remove("pwd_" + email)
                        .apply();
            }

            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void showAccountManagementDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_management, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        RecyclerView rvAccounts = dialogView.findViewById(R.id.rvAccounts);
        View btnAddAccount = dialogView.findViewById(R.id.btnAddAccount);
        TextView btnClose = dialogView.findViewById(R.id.btnClose);

        SharedPreferences prefs = getSharedPreferences("TripSyncAccounts", MODE_PRIVATE);
        Set<String> savedEmailsSet = prefs.getStringSet("saved_emails", new HashSet<>());
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail().toLowerCase().trim() : "";

        List<String> emailList = new ArrayList<>(savedEmailsSet);
        if (!currentEmail.isEmpty() && !emailList.contains(currentEmail)) {
            emailList.add(currentEmail);
        }

        // 🔥 Fetch UIDs for consistent avatars
        db.collection("users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            Map<String, String> emailToUid = new HashMap<>();
            for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                String email = doc.getString("email");
                if (email != null) emailToUid.put(email.toLowerCase().trim(), doc.getId());
            }

            rvAccounts.setLayoutManager(new LinearLayoutManager(this));
            rvAccounts.setAdapter(new AccountAdapter(emailList, currentEmail, emailToUid, email -> {
                if (email.equals(currentEmail)) {
                    Toast.makeText(this, "Already logged in", Toast.LENGTH_SHORT).show();
                } else {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.putExtra("switch_email", email);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
                dialog.dismiss();
            }));
        });

        btnAddAccount.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {
        private List<String> emails;
        private String currentEmail;
        private Map<String, String> emailToUid;
        private OnAccountClickListener listener;

        AccountAdapter(List<String> emails, String currentEmail, Map<String, String> emailToUid, OnAccountClickListener listener) {
            this.emails = emails;
            this.currentEmail = currentEmail;
            this.emailToUid = emailToUid;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String email = emails.get(position);
            holder.tvEmail.setText(email);
            
            boolean isCurrent = email.equalsIgnoreCase(currentEmail);
            holder.ivCheck.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
            holder.tvStatus.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
            
            // 🔥 CONSISTENT AVATAR LOGIC (Matches ProfileActivity and MembersFragment)
            int[] avatars = {R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha, R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx};
            
            String uid = emailToUid.get(email.toLowerCase().trim());
            if (uid != null) {
                int avatarIndex = Math.abs(uid.hashCode()) % avatars.length;
                holder.imgAccount.setImageResource(avatars[avatarIndex]);
            } else {
                // Fallback to email hash if user doc not found (rare)
                int avatarIndex = Math.abs(email.hashCode()) % avatars.length;
                holder.imgAccount.setImageResource(avatars[avatarIndex]);
            }

            holder.itemView.setOnClickListener(v -> listener.onAccountClick(email));
        }

        @Override
        public int getItemCount() { return emails.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvEmail, tvStatus;
            ImageView ivCheck, imgAccount;
            ViewHolder(View v) {
                super(v);
                tvEmail = v.findViewById(R.id.tvEmail);
                tvStatus = v.findViewById(R.id.tvStatus);
                ivCheck = v.findViewById(R.id.ivCheck);
                imgAccount = v.findViewById(R.id.imgAccount);
            }
        }
    }

    interface OnAccountClickListener {
        void onAccountClick(String email);
    }
}
