package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SignupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // EdgeToEdge removed to start app after status bar
        setContentView(R.layout.activity_signup);

        FirebaseAuth auth = FirebaseAuth.getInstance();

        EditText name = findViewById(R.id.signupName);
        EditText email = findViewById(R.id.signupEmail);
        EditText password = findViewById(R.id.signupPassword);
        Button signupBtn = findViewById(R.id.btnSignup);

        signupBtn.setOnClickListener(v -> {
            String userName = name.getText().toString().trim();
            String userEmail = email.getText().toString().trim();
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

            auth.createUserWithEmailAndPassword(userEmail, userPass)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if(user != null){
                                // 🔥 UPDATE USER PROFILE WITH NAME
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(userName)
                                        .build();

                                user.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
                                    // SEND VERIFICATION EMAIL
                                    user.sendEmailVerification().addOnCompleteListener(emailTask -> {
                                        Toast.makeText(this, "Verification email sent. Check your inbox.", Toast.LENGTH_LONG).show();
                                        Intent intent = new Intent(SignupActivity.this, SuccessActivity.class);
                                        intent.putExtra("type", "signup");
                                        startActivity(intent);
                                        finish();
                                    });
                                });
                            }
                        } else {
                            Toast.makeText(this, R.string.user_exists_error, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        findViewById(R.id.goToLogin).setOnClickListener(v -> finish());
    }
}