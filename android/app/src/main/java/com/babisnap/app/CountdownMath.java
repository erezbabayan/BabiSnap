package com.babisnap.app;

/**
 * Pure math for the call countdown animation, so it can be unit tested on the JVM.
 * The view only draws what these functions return; the call decision stays in {@link CallDial}.
 */
public final class CountdownMath {
    /** Fraction of the countdown still left, 1 at the start and 0 at the end. */
    public static float remaining(long elapsedMs, long totalMs) {
        if (totalMs <= 0) return 0f;
        if (elapsedMs <= 0) return 1f;
        if (elapsedMs >= totalMs) return 0f;
        return 1f - (float) elapsedMs / (float) totalMs;
    }

    /** Whole seconds shown in the ring: 5,4,3,2,1 then 0 at the end (ceil). */
    public static int secondsLeft(long elapsedMs, long totalMs) {
        long left = totalMs - Math.max(0L, elapsedMs);
        if (left <= 0) return 0;
        return (int) ((left + 999L) / 1000L);
    }

    /**
     * Reduced motion: the ring moves only in whole-second steps so nothing slides.
     * The value is the fraction for the second currently shown.
     */
    public static float steppedRemaining(long elapsedMs, long totalMs) {
        if (totalMs <= 0) return 0f;
        return Math.min(1f, secondsLeft(elapsedMs, totalMs) * 1000f / (float) totalMs);
    }

    /** Settings › Developer › Animator duration scale = 0 means the user turned animations off. */
    public static boolean reducedMotion(float animatorDurationScale) {
        return animatorDurationScale <= 0f;
    }

    /** Ring colour goes from calm purple to urgent green-to-amber in the last second: returns 0..1 urgency. */
    public static float urgency(long elapsedMs, long totalMs) {
        long left = totalMs - Math.max(0L, elapsedMs);
        if (left >= 1000L) return 0f;
        if (left <= 0) return 1f;
        return 1f - left / 1000f;
    }

    static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private CountdownMath() {}
}
