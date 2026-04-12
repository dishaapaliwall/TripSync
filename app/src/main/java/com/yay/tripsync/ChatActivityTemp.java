package com.yay.tripsync;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class ChatActivityTemp extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_temp);

        // Load ChatFragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new ChatFragment())
                    .commit();
        }
    }
}