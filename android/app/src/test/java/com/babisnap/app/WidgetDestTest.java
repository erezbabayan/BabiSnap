package com.babisnap.app;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class WidgetDestTest {
    private static String text(String p) throws Exception {
        return new String(Files.readAllBytes(Paths.get(p)), StandardCharsets.UTF_8);
    }

    @Test public void styleFollowsWidth() {
        assertEquals(WidgetSize.DestStyle.ICONS, WidgetSize.destStyle(0));
        assertEquals(WidgetSize.DestStyle.NONE, WidgetSize.destStyle(110));
        assertEquals(WidgetSize.DestStyle.NONE, WidgetSize.destStyle(179));
        assertEquals(WidgetSize.DestStyle.ICONS, WidgetSize.destStyle(180));
        assertEquals(WidgetSize.DestStyle.ICONS, WidgetSize.destStyle(249));
        assertEquals(WidgetSize.DestStyle.LABELS, WidgetSize.destStyle(250));
        assertEquals(WidgetSize.DestStyle.LABELS, WidgetSize.destStyle(400));
    }

    @Test public void largeWidgetListsAllFiveDestinationsWithSharedIcons() throws Exception {
        String w = text("src/main/res/layout/widget_mic.xml");
        for (String k : new String[]{"calendar", "whatsapp", "call", "task", "note"}) {
            String icon = "@drawable/ic_dest_" + k + "\"";
            int first = w.indexOf(icon);
            assertTrue(k + " in labelled row", first > 0);
            assertTrue(k + " in icon row", w.indexOf(icon, first + 1) > first);
        }
        assertTrue(w.contains("@drawable/widget_logo_mic"));
        assertTrue(w.contains("@+id/dest_labels") && w.contains("@+id/dest_icons"));
    }

    @Test public void bothWidgetsShowTheMicBadge() throws Exception {
        assertTrue(text("src/main/res/drawable/widget_logo_mic.xml").contains("@drawable/ic_mic_badge"));
        assertTrue(text("src/main/res/drawable/record_tile_mic.xml").contains("@drawable/ic_mic_badge"));
        assertTrue(text("src/main/res/layout/widget_button.xml").contains("@drawable/record_tile_mic"));
    }
}
