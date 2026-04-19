package com.yay.tripsync;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private EditText emailEditText, passwordEditText;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        }
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        Button loginButton = findViewById(R.id.loginButton);
        Button googleButton = findViewById(R.id.googleButton);
        TextView forgotPassword = findViewById(R.id.forgotPassword);
        TextView signUpText = findViewById(R.id.signUpText);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Handle account switching
        String switchEmail = getIntent().getStringExtra("switch_email");
        if (switchEmail != null) {
            switchEmail = switchEmail.toLowerCase().trim();
            String savedPassword = getSavedPassword(switchEmail);
            if (savedPassword != null) {
                performAutoLogin(switchEmail, savedPassword);
            } else {
                emailEditText.setText(switchEmail);
            }
        }

        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim().toLowerCase();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(MainActivity.this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null && user.isEmailVerified()) {
                                saveAccountLocally(email, password);
                                checkAccountStatus(user, "login");
                            } else {
                                Toast.makeText(MainActivity.this, "Please verify your email first", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, R.string.invalid_credentials, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        googleButton.setOnClickListener(v -> {
            googleSignInClient.signOut().addOnCompleteListener(this, task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });

        forgotPassword.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ForgotPasswordActivity.class)));
        signUpText.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SignupActivity.class)));
    }

    private void checkAccountStatus(FirebaseUser user, String type) {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean isDeleted = documentSnapshot.getBoolean("isDeleted");
                        if (isDeleted != null && isDeleted) {
                            Long permanentDeletionAt = documentSnapshot.getLong("permanentDeletionAt");
                            
                            if (permanentDeletionAt != null && System.currentTimeMillis() > permanentDeletionAt) {
                                // 🔥 AFTER 30 DAYS: Fresh Start
                                Map<String, Object> freshData = new HashMap<>();
                                freshData.put("isDeleted", false);
                                freshData.put("deletionScheduledAt", null);
                                freshData.put("permanentDeletionAt", null);
                                freshData.put("location", "");
                                freshData.put("phone", "");
                                freshData.put("gender", "");
                                freshData.put("dob", "");
                                // Optionally clear name if you want a total reset
                                // freshData.put("name", user.getDisplayName()); 

                                db.collection("users").document(user.getUid()).update(freshData)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Your old account expired. Welcome to your fresh start!", Toast.LENGTH_LONG).show();
                                            navigateToSuccess(type);
                                        });
                            } else {
                                // 🔥 WITHIN 30 DAYS: Recover Data
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("isDeleted", false);
                                updates.put("deletionScheduledAt", null);
                                updates.put("permanentDeletionAt", null);

                                db.collection("users").document(user.getUid()).update(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Welcome back! Your account has been recovered.", Toast.LENGTH_SHORT).show();
                                            navigateToSuccess(type);
                                        });
                            }
                            return;
                        }
                    }
                    navigateToSuccess(type);
                })
                .addOnFailureListener(e -> navigateToSuccess(type));
    }

    private void performAutoLogin(String email, String password) {
        if ("google_auth".equals(password)) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .setAccountName(email)
                    .build();
            GoogleSignInClient tempClient = GoogleSignIn.getClient(this, gso);
            
            tempClient.silentSignIn().addOnCompleteListener(this, task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    firebaseAuthWithGoogle(task.getResult().getIdToken());
                } else {
                    googleSignInLauncher.launch(tempClient.getSignInIntent());
                }
            });
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            checkAccountStatus(user, "login");
                        } else {
                            navigateToSuccess("login");
                        }
                    } else {
                        Toast.makeText(this, "Session expired, please login manually.", Toast.LENGTH_SHORT).show();
                        emailEditText.setText(email);
                    }
                });
    }

    private String getSavedPassword(String email) {
        SharedPreferences prefs = getSharedPreferences("TripSyncAccounts", MODE_PRIVATE);
        return prefs.getString("pwd_" + email.toLowerCase(), null);
    }

    private void saveAccountLocally(String email, String password) {
        String cleanEmail = email.toLowerCase().trim();
        SharedPreferences prefs = getSharedPreferences("TripSyncAccounts", MODE_PRIVATE);
        
        Set<String> oldAccounts = prefs.getStringSet("saved_emails", new HashSet<>());
        Set<String> newAccounts = new HashSet<>();
        for (String acc : oldAccounts) {
            newAccounts.add(acc.toLowerCase().trim());
        }
        newAccounts.add(cleanEmail);
        
        prefs.edit()
                .putStringSet("saved_emails", newAccounts)
                .putString("pwd_" + cleanEmail, password)
                .commit();
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            // 🔥 Sync Google User to Firestore
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("name", user.getDisplayName());
                            userData.put("email", user.getEmail().toLowerCase());
                            userData.put("uid", user.getUid());
                            
                            db.collection("users").document(user.getUid())
                                    .set(userData, SetOptions.merge())
                                    .addOnSuccessListener(aVoid -> {
                                        saveAccountLocally(user.getEmail(), "google_auth");
                                        checkAccountStatus(user, "google");
                                    });
                        }
                    } else {
                        Log.w(TAG, "Firebase auth with Google failed", task.getException());
                        Toast.makeText(MainActivity.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToSuccess(String type) {
        Intent intent = new Intent(MainActivity.this, SuccessActivity.class);
        intent.putExtra("type", type);
        startActivity(intent);
        finish();
    }
}
