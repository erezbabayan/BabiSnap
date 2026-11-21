package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

/** The calendar routing word and the command around it never stay in the text sent on to the title parser. */
public class YomanStripTest {
    private static Classifier.Result c(String s) { return Classifier.classify(s, true); }

    @Test public void colonAfterTheCalendarWordIsRouting() {
        Classifier.Result r = c("ליומן: פגישה עם דני מחר ב-10");
        assertEquals(Classifier.Kind.MEETING, r.kind);
        assertFalse(r.stripped, r.stripped.contains("יומן"));
        assertFalse(r.stripped, r.stripped.startsWith(":"));
    }

    @Test public void infinitiveCommandBeforeTheCalendarWordIsStripped() {
        assertEquals("פגישה מחר ב-9", c("לרשום ביומן פגישה מחר ב-9").stripped);
        assertEquals("יום הולדת לנועה ביום שני", c("להוסיף ליומן יום הולדת לנועה ביום שני").stripped);
    }

    @Test public void existingCommandFormsStillStripped() {
        assertEquals("פגישה עם דני מחר ב-10", c("תרשום ביומן פגישה עם דני מחר ב-10").stripped);
        assertEquals("פגישה עם דני מחר ב-10", c("הוסף ליומן פגישה עם דני מחר ב-10").stripped);
    }
}
