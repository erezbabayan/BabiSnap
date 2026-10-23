package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InviteFlowTest {
    static final List<ContactMatcher.Contact> BOOK = ContactMatcherTest.BOOK;

    @Test public void onlyWhenAskedAndSettingOn() {
        List<String> tamar = Collections.singletonList("תמר");
        assertTrue(InviteFlow.wanted(tamar, true, true));
        assertFalse(InviteFlow.wanted(tamar, false, true));                 // setting off (default)
        assertFalse(InviteFlow.wanted(Collections.<String>emptyList(), true, true)); // "פגישה עם תמר" – no invite phrase
        assertFalse(InviteFlow.wanted(null, true, true));
        assertFalse(InviteFlow.wanted(tamar, true, false));                 // not a meeting
    }

    @Test public void kinds() {
        assertEquals(InviteFlow.Kind.ONE, InviteFlow.Invitee.resolve("תמר עבודה", BOOK).kind);
        assertEquals(InviteFlow.Kind.MANY, InviteFlow.Invitee.resolve("תמר", BOOK).kind);
        assertEquals(InviteFlow.Kind.NONE, InviteFlow.Invitee.resolve("שלומית", BOOK).kind);
        assertEquals(InviteFlow.Kind.NONE, InviteFlow.Invitee.resolve("דני מהמכולת", BOOK).kind);
        assertEquals(InviteFlow.Kind.NO_PERMISSION, InviteFlow.Invitee.noPermission("תמר").kind);
    }

    @Test public void singleMatchesNeedNoPicker() {
        List<InviteFlow.Invitee> all = Arrays.asList(InviteFlow.Invitee.resolve("תמר עבודה", BOOK), InviteFlow.Invitee.resolve("שלומית", BOOK));
        assertFalse(InviteFlow.needsPicker(all));
        assertNull(InviteFlow.nextPicker(all));
        assertEquals(Arrays.asList("tamar.cohen@example.com"), InviteFlow.attendees(all));
    }

    @Test public void pickersOneAfterAnother() {
        InviteFlow.Invitee tamar = InviteFlow.Invitee.resolve("תמר", BOOK), yossi = InviteFlow.Invitee.resolve("יוסי", BOOK),
            dani = InviteFlow.Invitee.resolve("כהן", BOOK);
        List<InviteFlow.Invitee> all = Arrays.asList(tamar, yossi, dani);
        assertTrue(InviteFlow.needsPicker(all));
        assertSame(tamar, InviteFlow.nextPicker(all));
        tamar.chosen = tamar.matches.get(1); tamar.decided = true;          // tap תמר לוי
        assertSame(dani, InviteFlow.nextPicker(all));
        dani.decided = true;                                                // "בלי זימון"
        assertNull(InviteFlow.nextPicker(all));
        assertEquals(Arrays.asList("tamar.levi@example.net", "yossi@example.com"), InviteFlow.attendees(all));
    }

    @Test public void timeoutSendsNothingForTheOpenPicker() {
        InviteFlow.Invitee tamar = InviteFlow.Invitee.resolve("תמר", BOOK), yossi = InviteFlow.Invitee.resolve("יוסי", BOOK);
        List<InviteFlow.Invitee> all = Arrays.asList(tamar, yossi);
        assertEquals(Arrays.asList("yossi@example.com"), InviteFlow.attendees(all));
        assertEquals(10000, InviteFlow.PICKER_TIMEOUT_MS);
    }

    @Test public void noPermissionInvitesNobody() {
        assertTrue(InviteFlow.attendees(Arrays.asList(InviteFlow.Invitee.noPermission("תמר"))).isEmpty());
    }

    @Test public void duplicateEmailsAreSentOnce() {
        InviteFlow.Invitee a = InviteFlow.Invitee.resolve("תמר עבודה", BOOK), b = InviteFlow.Invitee.resolve("תמר כהן", BOOK);
        assertEquals(1, InviteFlow.attendees(Arrays.asList(a, b)).size());
    }

    @Test public void texts() {
        ContactMatcher.Match m = InviteFlow.Invitee.resolve("תמר עבודה", BOOK).chosen;
        assertEquals("זימון יישלח ל: תמר כהן · \u2066tamar.cohen@example.com\u2069", InviteFlow.okStrip(m));
        assertEquals("לא נשלח זימון – לא נמצא מייל לתמר", InviteFlow.noEmailStrip("תמר"));
        assertEquals("לא נשלח זימון ל-David – אין גישה לאנשי קשר", InviteFlow.noPermissionStrip("David"));
        assertEquals("למי לשלוח זימון?", InviteFlow.pickerQuestion(InviteFlow.Invitee.resolve("תמר", BOOK), 1));
        assertEquals("למי לשלוח זימון – תמר?", InviteFlow.pickerQuestion(InviteFlow.Invitee.resolve("תמר", BOOK), 2));
        assertEquals("3 אנשי קשר", InviteFlow.pickerCount(3));
        assertTrue(InviteFlow.jitBody(Collections.singletonList("דני")).startsWith("כדי להזמין את דני לפגישה, BabiSnap צריך למצוא את המייל"));
        assertTrue(InviteFlow.patchFailed(403).contains("לאשר שוב"));
    }
}
