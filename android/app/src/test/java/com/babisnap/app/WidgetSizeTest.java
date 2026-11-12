package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class WidgetSizeTest {
    @Test public void sizedFromCellWidth() {
        assertEquals(0.62, ButtonWidget.ICON_FRACTION, 0.001);
        assertEquals(56, ButtonWidget.iconDp(0, 0));       // unknown -> launcher-icon fallback
        assertEquals(60, ButtonWidget.iconDp(96, 120));    // ~62% of cell width
        assertEquals(71, ButtonWidget.iconDp(115, 140));
        assertEquals(40, ButtonWidget.iconDp(96, 60));     // leave room for the label, never below the minimum
        assertEquals(40, ButtonWidget.iconDp(20, 20));
        assertEquals(110, ButtonWidget.iconDp(300, 300));
    }
}
