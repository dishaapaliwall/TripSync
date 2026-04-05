package com.yay.tripsync;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        final float W = dm.widthPixels;
        final float H = dm.heightPixels;

        BlanketView blanketView = new BlanketView(this, W, H);
        addContentView(blanketView,
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        new Handler().postDelayed(() -> {
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(3800);
            animator.setInterpolator(new DecelerateInterpolator(1.5f));
            animator.addUpdateListener(anim -> {
                float progress = (float) anim.getAnimatedValue();
                blanketView.setProgress(progress);
            });
            animator.start();
        }, 800);

        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 4800);
    }
}