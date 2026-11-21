package com.babisnap.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * Call confirm countdown (design A, approved by Erez): a purple-to-green ring that drains around a navy disc,
 * a phone icon that wiggles, and the seconds left (5→0) inside. The ring tip turns amber in the last second.
 * Pure Canvas drawing, no extra libraries. It only draws; the call is still fired by VoiceActivity's own timer.
 * Reduced motion: no wiggle, the ring steps once a second.
 */
public final class CallCountdownView extends View {
    private static final int PURPLE = 0xFF6A5FA8;
    private static final int PURPLE_DARK = 0xFF2A1B7A;
    private static final int GREEN = 0xFF0E7A3A;
    private static final int GREEN_LIGHT = 0xFF34C76F;
    private static final int TEAL_DARK = 0xFF1F5A55;
    private static final int AMBER = 0xFFFFC72C;
    private static final int TRACK = 0xFFEADFB5;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final Drawable phone;
    private final float dp;

    private long startedAt = -1L;
    private long totalMs = CallDial.CONFIRM_MS;
    private boolean reduced;
    private boolean running;
    private long frozenElapsed = -1L;

    public CallCountdownView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(7f * dp);
        track.setColor(TRACK);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(7f * dp);
        ring.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(13f * dp);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setAlpha(70);
        digits.setColor(Color.WHITE);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        digits.setTextSize(22f * dp);
        Drawable d = c.getDrawable(R.drawable.ic_dial_answer);
        phone = d == null ? null : d.mutate();
        setContentDescription("ספירה לאחור לפני השיחה");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Starts (or restarts) the countdown from full. */
    public void start(long durationMs, boolean reducedMotion) {
        totalMs = durationMs;
        reduced = reducedMotion;
        startedAt = SystemClock.uptimeMillis();
        frozenElapsed = -1L;
        running = true;
        invalidate();
    }

    /** Freezes the picture where it is (cancel, dial, card closed). */
    public void stop() {
        if (running) frozenElapsed = elapsed();
        running = false;
    }

    private long elapsed() {
        if (frozenElapsed >= 0) return frozenElapsed;
        if (startedAt < 0) return 0L;
        return SystemClock.uptimeMillis() - startedAt;
    }

    @Override protected void onMeasure(int w, int h) {
        int size = Math.round(132f * dp);
        setMeasuredDimension(resolveSize(size, w), resolveSize(size, h));
    }

    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        long t = elapsed();
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float outer = Math.min(cx, cy);
        float ringR = outer - 20f * dp;
        float discR = ringR - 10f * dp;
        float urgency = CountdownMath.urgency(t, totalMs);
        boolean done = t >= totalMs;

        canvas.drawCircle(cx, cy, ringR, track);
        float left = reduced ? CountdownMath.steppedRemaining(t, totalMs) : CountdownMath.remaining(t, totalMs);
        if (left > 0f) {
            int head = blend(GREEN_LIGHT, AMBER, urgency);
            SweepGradient sg = new SweepGradient(cx, cy, new int[]{PURPLE, GREEN, head, PURPLE}, new float[]{0f, 0.5f, 0.999f, 1f});
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRotate(-90f, cx, cy);
            sg.setLocalMatrix(m);
            ring.setShader(sg);
            glow.setShader(sg);
            arc.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
            canvas.drawArc(arc, -90f, 360f * left, false, glow);
            canvas.drawArc(arc, -90f, 360f * left, false, ring);
        }

        // Inner disc: navy to dark teal.
        disc.setShader(new LinearGradient(cx, cy - discR, cx, cy + discR, PURPLE_DARK, TEAL_DARK, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, discR, disc);

        if (phone != null) {
            int ic = Math.round(discR * 0.78f);
            float wiggle = reduced || done ? 0f : (float) (8.0 * Math.sin(t / 90.0)) * (((t / 700L) % 2 == 0) ? 1f : 0f);
            canvas.save();
            canvas.rotate(wiggle, cx, cy - discR * 0.25f);
            int l = Math.round(cx - ic / 2f), top = Math.round(cy - discR * 0.25f - ic / 2f);
            phone.setBounds(l, top, l + ic, top + ic);
            phone.setTint(Color.WHITE);
            phone.draw(canvas);
            canvas.restore();
        }
        int secs = CountdownMath.secondsLeft(t, totalMs);
        canvas.drawText(String.valueOf(secs), cx, cy + discR * 0.66f, digits);

        if (running && !done) {
            if (reduced) postInvalidateDelayed(1000L - (t % 1000L));
            else postInvalidateOnAnimation();
        }
    }

    static int blend(int a, int b, float f) {
        f = CountdownMath.clamp01(f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * f) << 16) | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}
