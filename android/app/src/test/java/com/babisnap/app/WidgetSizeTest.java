package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class WidgetSizeTest {
    @Test public void fiveSizesDefaultToRegular() {
        assertEquals(72, WidgetSize.DEFAULT_PERCENT);
        assertEquals(0.72, WidgetSize.fraction(72), 0.001);
        assertEquals(0.72, WidgetSize.fraction(0), 0.001); // unknown stored value stays regular
        assertEquals(2, WidgetSize.index(72));
        assertEquals("רגיל", WidgetSize.label(72));
        assertEquals("קטן מאוד", WidgetSize.label(64));
        assertEquals("גדול מאוד", WidgetSize.label(80));
        assertEquals(65, WidgetSize.fallbackDp(72));
        assertEquals(58, WidgetSize.fallbackDp(64));
        assertEquals(61, WidgetSize.fallbackDp(68));
        assertEquals(68, WidgetSize.fallbackDp(76));
        assertEquals(72, WidgetSize.fallbackDp(80));
    }

    @Test public void iconFitsTheCellAndLeavesRoomForTheLabel() {
        assertEquals(65, WidgetSize.iconDp(0, 0, 72));          // unknown cell → scaled fallback
        assertEquals(69, WidgetSize.iconDp(96, 140, 72));       // 72% of 96
        assertEquals(61, WidgetSize.iconDp(96, 140, 64));
        assertEquals(77, WidgetSize.iconDp(96, 140, 80));
        assertEquals(40, WidgetSize.iconDp(96, 50, 80));        // cell is short: clamp, never below the minimum
        assertEquals(40, WidgetSize.iconDp(20, 20, 72));
        assertEquals(110, WidgetSize.iconDp(300, 300, 80));
    }
}
