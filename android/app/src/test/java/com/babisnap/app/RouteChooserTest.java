package com.babisnap.app;

import static com.babisnap.app.RouteChooser.Choice.*;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Test;

public class RouteChooserTest {
    private static Classifier.Result c(String s) { return Classifier.classify(s, false); }
    private static boolean asks(String s) { return RouteChooser.shouldAsk(c(s), true, true, false); }

    @Test public void asksOnlyForAGuessedNote() {
        assertTrue(asks("הרכב עושה רעש מוזר"));
        assertTrue(c("הרכב עושה רעש מוזר").guessed);
    }

    @Test public void routingWordsNeverAsk() {
        assertFalse(asks("הערה הרכב עושה רעש מוזר"));     // explicit note
        assertFalse(asks("פתק הרכב עושה רעש"));
        assertFalse(asks("משימה לקנות חלב"));             // explicit task
        assertFalse(asks("תזכיר לי לקנות חלב"));
        assertFalse(asks("ביומן ארוחה עם אמא"));          // calendar word
        assertFalse(asks("פגישה עם דני"));                // meeting cue / contact
        assertFalse(asks("להיפגש עם יוסי"));
        assertFalse(asks("צריך לתקן את הברז"));           // obligation → task
    }

    @Test public void dateTimeOrTagsNeverAsk() {
        Classifier.Result g = c("הרכב עושה רעש מוזר");
        assertFalse(RouteChooser.shouldAsk(g, false, true, false));
        assertFalse(RouteChooser.shouldAsk(g, true, false, false));
        assertFalse(RouteChooser.shouldAsk(g, true, true, true));
        assertFalse(RouteChooser.shouldAsk(null, true, true, false));
    }

    @Test public void explicitTimeIsAMeetingNotAQuestion() {
        Classifier.Result r = Classifier.classify("הרכב עושה רעש מוזר", true);
        assertEquals(Classifier.Kind.MEETING, r.kind);
        assertFalse(RouteChooser.shouldAsk(r, true, false, false));
    }

    @Test public void onlyTheLastFallbackIsGuessed() {
        assertFalse(c("הערה משהו").guessed);
        assertFalse(c("משימה משהו").guessed);
        assertFalse(c("פגישה מחר").guessed);
    }

    @Test public void fiveCirclesWithWhatsAppOnlyWhenEnabled() {
        assertEquals(Arrays.asList(CALENDAR, WHATSAPP, CALL, TASK, NOTE), RouteChooser.choices(true));
        assertEquals(Arrays.asList(CALENDAR, CALL, TASK, NOTE), RouteChooser.choices(false));
    }

    @Test public void gridIsThreePlusTwoForFiveElseTwoPerRow() {
        assertEquals(3, RouteChooser.perRow(5));
        assertEquals(2, RouteChooser.perRow(4));
        assertEquals(2, RouteChooser.perRow(3));
        assertTrue(RouteChooser.circleDp(5) < RouteChooser.circleDp(4));
        assertTrue(RouteChooser.circleDp(5) >= 48);
    }

    @Test public void callCircleUsesTheExistingCallFlow() {
        Classifier.Result g = c("אבא שלי");
        Classifier.Result r = RouteChooser.chosen(CALL, g);
        assertEquals(Classifier.Kind.CALL, r.kind);
        assertEquals(RouteGate.Dest.CALL, RouteGate.dest(r.kind));
        assertEquals(g.stripped, r.stripped);          // the spoken name goes to the contact search
        assertFalse(RouteGate.toBabiTk(r.kind));
        assertFalse(RouteGate.toCalQueue(r.kind));
        assertEquals("שיחה", RouteChooser.label(CALL));
    }

    @Test public void defaultIsNoteAfterEightSeconds() {
        assertEquals(NOTE, RouteChooser.DEFAULT);
        assertEquals(8000L, RouteChooser.TIMEOUT_MS);
        assertEquals(Classifier.Kind.NOTE, RouteChooser.kind(RouteChooser.DEFAULT));
    }

    @Test public void choiceKeepsTheTextAndNeverReAsks() {
        Classifier.Result g = c("הרכב עושה רעש מוזר");
        for (RouteChooser.Choice ch : RouteChooser.Choice.values()) {
            Classifier.Result r = RouteChooser.chosen(ch, g);
            assertEquals(g.stripped, r.stripped);
            assertEquals(RouteChooser.kind(ch), r.kind);
            assertFalse(RouteChooser.shouldAsk(r, true, true, false));
        }
        assertEquals(Classifier.Kind.MEETING, RouteChooser.kind(CALENDAR));
        assertEquals(Classifier.Kind.WHATSAPP, RouteChooser.kind(WHATSAPP));
        assertEquals(Classifier.Kind.TASK, RouteChooser.kind(TASK));
    }

    @Test public void circlesUseTheSharedIcons() {
        assertEquals(DestIcon.Dest.CALENDAR, RouteChooser.dest(CALENDAR));
        assertEquals(DestIcon.Dest.WHATSAPP, RouteChooser.dest(WHATSAPP));
        assertEquals(DestIcon.Dest.CALL, RouteChooser.dest(CALL));
        assertEquals(DestIcon.Dest.TASK, RouteChooser.dest(TASK));
        assertEquals(DestIcon.Dest.NOTE, RouteChooser.dest(NOTE));
    }

    @Test public void voiceActivityAsksBeforeRoutingAndDefaultsOnLeave() throws Exception {
        String src = new String(Files.readAllBytes(Paths.get("src/main/java/com/babisnap/app/VoiceActivity.java")), StandardCharsets.UTF_8);
        int ask = src.indexOf("RouteChooser.shouldAsk(");
        int route = src.indexOf("routeClassified(c);");
        assertTrue(ask > 0 && route > ask);
        assertTrue(src.contains("ui.postDelayed(chooserExpire, RouteChooser.TIMEOUT_MS)"));
        assertTrue(src.contains("if (chooserOpen) chooseRoute(RouteChooser.DEFAULT);"));
    }
}
