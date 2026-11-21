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
 * Call confirm countdown: a ring that drains around the phone icon, ripple rings that pulse out of it,
 * and the seconds left in the middle. Pure Canvas drawing, no extra libraries.
 * It only draws; the call itself is still fired by VoiceActivity's own timer.
 * Reduced motion: no ripples, the ring steps once a second.
 */
public final class CallCountdownView extends View {
    private static final int PURPLE = 0xFF6A5FA8;
    private static final int PURPLE_DARK = 0xFF2A1B7A;
    private static final int GREEN = 0xFF0E7A3A;
    private static final int GREEN_LIGHT = 0xFF34C76F;
    private static final int AMBER = 0xFFFFC72C;
    private static final int TRACK = 0xFFEADFB5;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        track.setStrokeWidth(6f * dp);
        track.setColor(TRACK);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(6f * dp);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ripple.setStyle(Paint.Style.STROKE);
        ripple.setStrokeWidth(2.5f * dp);
        digits.setColor(Color.WHITE);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        digits.setTextSize(15f * dp);
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
        float discR = ringR - 12f * dp;
        float urgency = CountdownMath.urgency(t, totalMs);
        boolean done = t >= totalMs;

        // Ripples come out of the disc and fade before the card edge.
        if (!reduced && !done) {
            for (int i = 0; i < CountdownMath.RIPPLES; i++) {
                float ph = CountdownMath.ripplePhase(t, i);
                ripple.setColor(GREEN_LIGHT);
                ripple.setAlpha(CountdownMath.rippleAlpha(ph));
                canvas.drawCircle(cx, cy, discR + (outer - discR) * ph, ripple);
            }
        }

        canvas.drawCircle(cx, cy, ringR, track);
        float left = reduced ? CountdownMath.steppedRemaining(t, totalMs) : CountdownMath.remaining(t, totalMs);
        if (left > 0f) {
            int head = blend(GREEN_LIGHT, AMBER, urgency);
            SweepGradient sg = new SweepGradient(cx, cy, new int[]{PURPLE, GREEN, head, PURPLE}, new float[]{0f, 0.5f, 0.999f, 1f});
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRotate(-90f, cx, cy);
            sg.setLocalMatrix(m);
            ring.setShader(sg);
            arc.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
            canvas.drawArc(arc, -90f, 360f * left, false, ring);
        }

        // Inner disc: deep purple to green, with a soft breathing scale when motion is allowed.
        float breathe = reduced || done ? 1f : 1f + 0.03f * (float) Math.sin(t / 1000.0 * Math.PI * 2);
        float r = discR * breathe;
        disc.setShader(new LinearGradient(cx, cy - r, cx, cy + r, PURPLE_DARK, blend(GREEN, PURPLE_DARK, 0.25f), Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, disc);

        if (phone != null) {
            int ic = Math.round(discR * 0.85f);
            float wiggle = reduced || done ? 0f : (float) (8.0 * Math.sin(t / 90.0)) * (((t / 700L) % 2 == 0) ? 1f : 0f);
            canvas.save();
            canvas.rotate(wiggle, cx, cy - discR * 0.22f);
            int l = Math.round(cx - ic / 2f), top = Math.round(cy - discR * 0.22f - ic / 2f);
            phone.setBounds(l, top, l + ic, top + ic);
            phone.setTint(Color.WHITE);
            phone.draw(canvas);
            canvas.restore();
        }
        int secs = CountdownMath.secondsLeft(t, totalMs);
        canvas.drawText(String.valueOf(secs), cx, cy + discR * 0.72f, digits);

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
