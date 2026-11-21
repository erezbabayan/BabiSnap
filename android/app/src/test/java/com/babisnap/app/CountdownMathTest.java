package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class CountdownMathTest {
    private static final long T = CallDial.CONFIRM_MS;

    @Test public void durationUnchanged() { assertEquals(5000L, T); }

    @Test public void remainingDrainsFromOneToZero() {
        assertEquals(1f, CountdownMath.remaining(0, T), 0f);
        assertEquals(0.5f, CountdownMath.remaining(2500, T), 1e-6);
        assertEquals(0f, CountdownMath.remaining(5000, T), 0f);
        assertEquals(0f, CountdownMath.remaining(9000, T), 0f);
        assertEquals(1f, CountdownMath.remaining(-10, T), 0f);
        assertEquals(0f, CountdownMath.remaining(10, 0), 0f);
    }

    @Test public void secondsCountDownFiveToZero() {
        assertEquals(5, CountdownMath.secondsLeft(0, T));
        assertEquals(5, CountdownMath.secondsLeft(999, T));
        assertEquals(4, CountdownMath.secondsLeft(1000, T));
        assertEquals(1, CountdownMath.secondsLeft(4999, T));
        assertEquals(0, CountdownMath.secondsLeft(5000, T));
        assertEquals(0, CountdownMath.secondsLeft(6000, T));
    }

    @Test public void reducedMotionStepsWholeSeconds() {
        assertEquals(1f, CountdownMath.steppedRemaining(0, T), 0f);
        assertEquals(1f, CountdownMath.steppedRemaining(400, T), 0f);
        assertEquals(0.8f, CountdownMath.steppedRemaining(1000, T), 1e-6);
        assertEquals(0.8f, CountdownMath.steppedRemaining(1700, T), 1e-6);
        assertEquals(0f, CountdownMath.steppedRemaining(5000, T), 0f);
    }

    @Test public void reducedMotionOnlyWhenScaleZero() {
        assertTrue(CountdownMath.reducedMotion(0f));
        assertFalse(CountdownMath.reducedMotion(0.5f));
        assertFalse(CountdownMath.reducedMotion(1f));
    }

    @Test public void ripplesAreOffsetAndLoop() {
        assertEquals(0f, CountdownMath.ripplePhase(0, 0), 0f);
        assertEquals(0.5f, CountdownMath.ripplePhase(0, 1), 1e-6);
        assertEquals(CountdownMath.ripplePhase(100, 0), CountdownMath.ripplePhase(100 + CountdownMath.RIPPLE_PERIOD_MS, 0), 1e-6);
        float p = CountdownMath.ripplePhase(1399, 0);
        assertTrue(p >= 0f && p < 1f);
    }

    @Test public void rippleFadesAsItGrows() {
        assertTrue(CountdownMath.rippleAlpha(0f) > CountdownMath.rippleAlpha(0.5f));
        assertTrue(CountdownMath.rippleAlpha(0.5f) > CountdownMath.rippleAlpha(0.9f));
        assertEquals(0, CountdownMath.rippleAlpha(1f));
    }

    @Test public void shimmerLoops() {
        assertEquals(0f, CountdownMath.shimmerPos(0), 0f);
        assertEquals(0f, CountdownMath.shimmerPos(CountdownMath.SHIMMER_PERIOD_MS), 0f);
        assertTrue(CountdownMath.shimmerPos(800) > 0f && CountdownMath.shimmerPos(800) < 1f);
    }

    @Test public void urgencyOnlyInLastSecond() {
        assertEquals(0f, CountdownMath.urgency(0, T), 0f);
        assertEquals(0f, CountdownMath.urgency(4000, T), 0f);
        assertEquals(0.5f, CountdownMath.urgency(4500, T), 1e-6);
        assertEquals(1f, CountdownMath.urgency(5000, T), 0f);
    }
}
