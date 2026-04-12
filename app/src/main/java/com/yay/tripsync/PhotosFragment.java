package com.yay.tripsync;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

public class PhotosFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        TextView textView = new TextView(getContext());
        textView.setText("Photos");
        textView.setTextColor(0xFFFFFFFF);
        textView.setTextSize(20);
        textView.setGravity(android.view.Gravity.CENTER);

        return textView;
    }
}