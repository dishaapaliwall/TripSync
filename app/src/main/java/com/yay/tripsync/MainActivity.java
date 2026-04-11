package com.yay.tripsync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // EdgeToEdge removed to start app after status bar
        setContentView(R.layout.activity_main);

        // 🔥 Firebase
        FirebaseAuth auth = FirebaseAuth.getInstance();

        // Views
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        Button loginButton = findViewById(R.id.loginButton);
        TextView forgotPassword = findViewById(R.id.forgotPassword);
        TextView signUpText = findViewById(R.id.signUpText);

        // 🔥 LOGIN BUTTON
        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(MainActivity.this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // 🔥 CHECK EMAIL VERIFIED
                            if (auth.getCurrentUser() != null && auth.getCurrentUser().isEmailVerified()) {
                                Intent intent = new Intent(MainActivity.this, SuccessActivity.class);
                                intent.putExtra("type", "login");
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(MainActivity.this, "Please verify your email first", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, R.string.invalid_credentials, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // 🔥 FORGOT PASSWORD
        forgotPassword.setOnClickListener(v -> Toast.makeText(MainActivity.this, R.string.forgot_password, Toast.LENGTH_SHORT).show());

        // 🔥 SIGNUP NAVIGATION
        signUpText.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SignupActivity.class)));
    }
}