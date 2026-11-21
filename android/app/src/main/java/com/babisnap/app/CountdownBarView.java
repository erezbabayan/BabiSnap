package com.babisnap.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Thin countdown bar under the call card text: a purple-to-green gradient that drains,
 * with a light shimmer sweeping across it. Reduced motion: no shimmer, whole-second steps.
 */
public final class CountdownBarView extends View {
    private final Paint trackP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shineP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;
    private long startedAt = -1L, totalMs = CallDial.CONFIRM_MS, frozen = -1L;
    private boolean reduced, running;

    public CountdownBarView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        trackP.setColor(0xFFEADFB5);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void start(long durationMs, boolean reducedMotion) {
        totalMs = durationMs; reduced = reducedMotion; startedAt = SystemClock.uptimeMillis(); frozen = -1L; running = true; invalidate();
    }

    public void stop() { if (running) frozen = elapsed(); running = false; }

    private long elapsed() {
        if (frozen >= 0) return frozen;
        return startedAt < 0 ? 0L : SystemClock.uptimeMillis() - startedAt;
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), resolveSize(Math.round(6f * dp), h));
    }

    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        long t = elapsed();
        float w = getWidth(), h = getHeight(), rad = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, rad, rad, trackP);
        float left = reduced ? CountdownMath.steppedRemaining(t, totalMs) : CountdownMath.remaining(t, totalMs);
        if (left > 0f) {
            float fw = w * left;
            // RTL card: the bar drains toward the reading start (right side).
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float x0 = rtl ? w - fw : 0f, x1 = rtl ? w : fw;
            rect.set(x0, 0, x1, h);
            fillP.setShader(new LinearGradient(0, 0, w, 0, rtl ? 0xFF0E7A3A : 0xFF6A5FA8, rtl ? 0xFF6A5FA8 : 0xFF0E7A3A, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, rad, rad, fillP);
            if (!reduced) {
                float band = w * 0.25f;
                float pos = -band + (w + 2 * band) * CountdownMath.shimmerPos(t);
                if (rtl) pos = w - pos;
                shineP.setShader(new LinearGradient(pos - band / 2, 0, pos + band / 2, 0,
                    new int[]{0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                canvas.save();
                canvas.clipRect(rect);
                canvas.drawRoundRect(rect, rad, rad, shineP);
                canvas.restore();
            }
        }
        if (running && t < totalMs) {
            if (reduced) postInvalidateDelayed(1000L - (t % 1000L));
            else postInvalidateOnAnimation();
        }
    }
}
