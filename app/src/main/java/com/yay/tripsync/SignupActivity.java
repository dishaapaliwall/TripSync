package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        EditText name = findViewById(R.id.signupName);
        EditText email = findViewById(R.id.signupEmail);
        EditText password = findViewById(R.id.signupPassword);
        Button signupBtn = findViewById(R.id.btnSignup);

        signupBtn.setOnClickListener(v -> {
            String userName = name.getText().toString().trim();
            String userEmail = email.getText().toString().trim().toLowerCase();
            String userPass = password.getText().toString().trim();

            if (userName.isEmpty() || userEmail.isEmpty() || userPass.isEmpty()) {
                Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()){
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            if(userPass.length() < 6){
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check if account is scheduled for deletion first
            db.collection("users").whereEqualTo("email", userEmail).get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                            Boolean isDeleted = doc.getBoolean("isDeleted");
                            if (isDeleted != null && isDeleted) {
                                Toast.makeText(this, "This account is scheduled for deletion. Please login to recover it.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        
                        // Proceed with creation
                        createNewAccount(userName, userEmail, userPass);
                    })
                    .addOnFailureListener(e -> {
                        // If check fails, just try to create (standard behavior)
                        createNewAccount(userName, userEmail, userPass);
                    });
        });

        findViewById(R.id.goToLogin).setOnClickListener(v -> finish());
    }

    private void createNewAccount(String userName, String userEmail, String userPass) {
        auth.createUserWithEmailAndPassword(userEmail, userPass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if(user != null){
                            // 1. Update Auth Profile
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(userName)
                                    .build();

                            user.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
                                
                                // 2. 🔥 SAVE TO FIRESTORE (Initialize User Document)
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("name", userName);
                                userData.put("email", userEmail);
                                userData.put("uid", user.getUid());
                                userData.put("location", "");
                                userData.put("phone", "");
                                userData.put("gender", "");
                                userData.put("dob", "");
                                userData.put("isDeleted", false); // Ensure it's not deleted

                                db.collection("users").document(user.getUid())
                                        .set(userData)
                                        .addOnSuccessListener(aVoid -> {
                                            // 3. Send Verification Email
                                            user.sendEmailVerification().addOnCompleteListener(emailTask -> {
                                                Toast.makeText(this, "Account created! Verification email sent.", Toast.LENGTH_LONG).show();
                                                Intent intent = new Intent(SignupActivity.this, SuccessActivity.class);
                                                intent.putExtra("type", "signup");
                                                startActivity(intent);
                                                finish();
                                            });
                                        });
                            });
                        }
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(this, "Account already exists. Please log in.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Signup Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
