package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class WidgetSizeTest {
    @Test public void oneLauncherIconSize() {
        assertEquals(0.89, WidgetSize.ICON_FRACTION, 0.001);
        assertEquals(70, WidgetSize.FALLBACK_DP);
        assertEquals(70, WidgetSize.iconDp(0, 0));
        assertEquals(85, WidgetSize.iconDp(96, 140));   // 89% of the cell width
        assertEquals(102, WidgetSize.iconDp(115, 160));
        assertEquals(40, WidgetSize.iconDp(96, 50));    // shorter than the label leaves: clamp to the minimum
        assertEquals(40, WidgetSize.iconDp(20, 20));
        assertEquals(110, WidgetSize.iconDp(300, 300));
        assertEquals("record_button_percent", WidgetSize.OLD_PREF);
    }
}
