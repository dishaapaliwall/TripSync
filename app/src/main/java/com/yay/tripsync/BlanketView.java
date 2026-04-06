package com.yay.tripsync;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class BlanketView extends View {

    private float progress = 0f;
    private final float W, H;
    private final Paint paint;

    public BlanketView(Context context, float W, float H) {
        super(context);
        this.W = W;
        this.H = H;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#8B1C2D"));
        paint.setStyle(Paint.Style.FILL);
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress <= 0f) return;
        if (progress >= 1f) {
            canvas.drawRect(-100, -100, W + 100, H + 100, paint);
            return;
        }

        final int N = 80;
        final float diagLen = (float) Math.sqrt(W * W + H * H);
        final float waveAmp = W * 0.06f;
        final float waveFreq = 2f;

        float waveFade;
        if (progress < 0.15f) {
            waveFade = progress / 0.15f;
        } else if (progress < 0.75f) {
            waveFade = 1f;
        } else {
            waveFade = Math.max(0f, 1f - (progress - 0.75f) / 0.17f);
        }

        float[] xs = new float[N + 1];
        float[] ys = new float[N + 1];

        for (int i = 0; i <= N; i++) {
            float s    = (float) i / N;
            float cx   = W * (1f - progress);
            float cy   = H * (1f - progress);
            float span = diagLen * 2.0f;
            float tPos = s - 0.5f;

            float px = cx + tPos * span * 0.707f;
            float py = cy - tPos * span * 0.707f;

            float envelope = (float) Math.sin(Math.PI * s);
            float wave = waveAmp * envelope * waveFade
                    * (float) Math.sin(s * Math.PI * 2f * waveFreq);

            xs[i] = px - wave * 0.707f;
            ys[i] = py - wave * 0.707f;
        }

        Path path = new Path();
        path.moveTo(W + 500, H + 500);
        path.lineTo(xs[0], ys[0]);

        for (int i = 1; i < N; i++) {
            float mx = (xs[i] + xs[i + 1]) / 2f;
            float my = (ys[i] + ys[i + 1]) / 2f;
            path.quadTo(xs[i], ys[i], mx, my);
        }

        path.lineTo(xs[N], ys[N]);
        path.lineTo(W + 500, -500);
        path.lineTo(W + 500, H + 500);
        path.close();

        canvas.drawPath(path, paint);
    }
}