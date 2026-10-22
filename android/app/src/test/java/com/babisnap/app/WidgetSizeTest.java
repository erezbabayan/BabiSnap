package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class WidgetSizeTest {
    @Test public void cappedAtIconSize() {
        assertEquals(54, ButtonWidget.iconDp(0, 0));
        assertEquals(54, ButtonWidget.iconDp(90, 110));
        assertEquals(48, ButtonWidget.iconDp(80, 48));
        assertEquals(24, ButtonWidget.iconDp(10, 10));
    }
}
