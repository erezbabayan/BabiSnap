package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class WidgetSizeTest {
    @Test public void sizedFromCellWidth() {
        assertEquals(76, ButtonWidget.iconDp(0, 0));       // unknown -> fallback
        assertEquals(79, ButtonWidget.iconDp(96, 120));    // ~82% of cell width
        assertEquals(94, ButtonWidget.iconDp(115, 140));
        assertEquals(57, ButtonWidget.iconDp(96, 60));     // never taller than the cell (minus top offset)
        assertEquals(40, ButtonWidget.iconDp(20, 20));
        assertEquals(110, ButtonWidget.iconDp(300, 300));
    }
}
