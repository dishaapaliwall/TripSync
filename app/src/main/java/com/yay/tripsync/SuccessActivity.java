package com.yay.tripsync;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_success);

        // 🔥 Window Insets for Edge-to-Edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 🔥 VIEWS
        ImageView wave = findViewById(R.id.waveBg);
        TextView text = findViewById(R.id.successText);
        View circle = findViewById(R.id.circle);
        ImageView thumb = findViewById(R.id.thumb);

        // 🔥 PULSE ANIMATION LOOP
        Runnable pulse = new Runnable() {
            @Override
            public void run() {
                if (circle != null && thumb != null) {
                    circle.animate().scaleX(0.9f).scaleY(0.9f).setDuration(300)
                            .withEndAction(() -> {
                                if (circle != null) circle.animate().scaleX(1.1f).scaleY(1.1f).setDuration(300);
                            });

                    thumb.animate().scaleX(0.9f).scaleY(0.9f).setDuration(300)
                            .withEndAction(() -> {
                                if (thumb != null) thumb.animate().scaleX(1.1f).scaleY(1.1f).setDuration(300);
                            });

                    circle.postDelayed(this, 1200);
                }
            }
        };

        // 🔥 START ANIMATION
        if (circle != null) {
            circle.post(pulse);
        }

        // 🔥 LOGIN / SIGNUP CHECK
        String type = getIntent().getStringExtra("type");
        // 🔥 THUMB CLICK → OPEN TRIP SCREEN
        thumb.setOnClickListener(v -> {
            startActivity(new android.content.Intent(SuccessActivity.this, TripActivity.class));
        });

        if(type != null && type.equals("signup")){
            text.setText(R.string.signed_up);
            wave.setImageResource(R.drawable.vector1);   // signup wala
        } else {
            text.setText(R.string.logged_in);
            wave.setImageResource(R.drawable.vector);    // login wala
        }
    }
}