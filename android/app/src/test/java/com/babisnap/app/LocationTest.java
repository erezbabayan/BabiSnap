package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

/** Stage 1 of "location": place extracted from the phrase -> calendar location field + 📍 line in the undo card. */
public class LocationTest {
    static HebrewParser parser() throws Exception { return new HebrewParser(ParserTest.r("Code.gs")); }
    static String parse(String s) throws Exception { return parser().parseJson(s, 2026, 10, 23, 5, 60); }

    @Test public void tamarAtKapitInModiin() throws Exception {
        String txt = "להיפגש עם תמר במסעדת כפית במודיעין מחר בשבע בערב";
        String j = parse(txt);
        assertTrue(j, j.contains("\"title\":\"פגישה עם תמר\""));
        assertTrue(j, j.contains("\"location\":\"מסעדת כפית, מודיעין\""));
        assertTrue(j, j.contains("\"startStr\":\"2026-10-24 19:00\""));
        assertEquals(Classifier.Kind.MEETING, Classifier.classify(txt, true).kind);
        String ev = EventJson.build("פגישה עם תמר", "2026-10-24 19:00", 60, "מסעדת כפית, מודיעין", null);
        assertTrue(ev, ev.contains("\"summary\":\"פגישה עם תמר\",\"location\":\"מסעדת כפית, מודיעין\""));
    }

    @Test public void timeWordsAreNotPlaces() throws Exception {
        for (String s : new String[]{"פגישה עם דני מחר בשבע בערב", "פגישה עם דני ביום ראשון בבוקר", "פגישה עם דני במחר בשעה 10"}) {
            String j = parse(s);
            assertTrue(s + " -> " + j, j.contains("\"location\":\"\"") || !j.contains("\"location\""));
            assertTrue(s + " -> " + j, j.contains("\"title\":\"פגישה עם דני\""));
        }
        assertTrue(parse("פגישה עם דני מחר בשבע בערב").contains(" 19:00\""));
    }

    @Test public void citiesThatLookLikeTimes() throws Exception {
        String j = parse("פגישה עם רון בבאר שבע מחר ב-10");
        assertTrue(j, j.contains("\"location\":\"באר שבע\"") && j.contains("2026-10-24 10:00") && j.contains("\"title\":\"פגישה עם רון\""));
    }

    @Test public void taskKeepsPlaceInTitleAndGetsLocation() throws Exception {
        String j = parse("לקנות חלב בסופר ברמת גן");
        assertTrue(j, j.contains("\"titleFull\":\"לקנות חלב בסופר ברמת גן\"") && j.contains("\"location\":\"סופר, רמת גן\""));
        String body = BabiTkJson.build("לקנות חלב בסופר ברמת גן", null, null, null, "סופר, רמת גן", "task", "bsnap-1");
        assertTrue(body, body.contains("\"location\":\"סופר, רמת גן\""));
    }

    @Test public void meetingCardShowsPinLine() {
        assertEquals("פגישה עם תמר\n📅 24/10/2026 · 19:00\n📍 מסעדת כפית, מודיעין",
            CardText.meeting("פגישה עם תמר", 24, 10, 2026, 19, 0, false, "מסעדת כפית, מודיעין"));
        assertEquals("פגישה עם דני\n📅 24/10/2026 · 09:00 (לא נאמרה שעה)",
            CardText.meeting("פגישה עם דני", 24, 10, 2026, 9, 0, true, ""));
        assertEquals("לקנות חלב\nללא תאריך\n📍 סופר", CardText.saved("לקנות חלב", false, 0, 0, 0, null, "סופר"));
        assertEquals("משימה\n📅 1/11/2026 · 08:30", CardText.saved("משימה", true, 1, 11, 2026, "08:30", null));
    }

    @Test public void longPlaceIsClippedOnCardOnly() {
        StringBuilder b = new StringBuilder(); for (int i = 0; i < 120; i++) b.append('א');
        String line = CardText.locationLine("  " + b + "  ");
        assertTrue(line.startsWith("\n📍 ") && line.endsWith("…"));
        assertEquals(CardText.LOCATION_CARD_MAX, line.length() - "\n📍 ".length());
        assertEquals("", CardText.locationLine("   "));
        assertEquals("", CardText.locationLine(null));
    }
}
