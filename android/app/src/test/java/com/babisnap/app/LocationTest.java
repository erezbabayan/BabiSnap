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

    // ---- 2.4.x: complete CBS locality list + meeting context ----
    static String field(String j, String k) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + k + "\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(j);
        return m.find() ? m.group(1) : "";
    }
    static String loc(String j) { return field(j, "location"); }
    static String title(String j) { return field(j, "title"); }

    @Test public void bruchinBothSpeechForms() throws Exception {
        for (String s : new String[]{"פגישה בברוכין לראות את הבית ביום שני ב-11", "פגישה וברוכין לראות את הבית ביום שני ב-11",
                                     "פגישה וברוכין לראות את הבית ב-26/10 בשעה 11"}) {
            String j = parse(s);
            assertEquals(s, "ברוכין", loc(j));
            assertEquals(s, "פגישה – לראות את הבית", title(j));
            assertTrue(j, j.contains("\"startStr\":\"2026-10-26 11:00\""));
            assertEquals(Classifier.Kind.MEETING, Classifier.classify(s, true).kind);
        }
    }

    @Test public void unknownPlaceInMeetingContext() throws Exception {
        String j = parse("פגישה בגבעת הפרחים לדבר על החוזה מחר ב-10");
        assertEquals("גבעת הפרחים", loc(j)); assertEquals("פגישה – לדבר על החוזה", title(j));
        j = parse("פגישה עם יוסי בנווה צדק מחר בשש");
        assertEquals("נווה צדק", loc(j)); assertEquals("פגישה עם יוסי", title(j));
        assertEquals("יפו העתיקה", loc(parse("פגישה ביפו העתיקה מחר בשבע בערב")));
    }

    @Test public void atTheDentistKeepsPersonInTitle() throws Exception {
        String j = parse("פגישה אצל רופא השיניים מחר בעשר");
        assertEquals("אצל רופא השיניים", loc(j)); assertEquals("פגישה אצל רופא השיניים", title(j));
    }

    @Test public void localitiesFromCbsList() throws Exception {
        assertEquals("כרכור", loc(parse("פגישה בכרכור מחר ב-9")));
        assertEquals("שדה בוקר", loc(parse("פגישה בשדה בוקר מחר ב-9")));
        assertEquals("קרית שמונה", loc(parse("פגישה בקרית שמונה מחר ב-9")));
        assertEquals("ברוכין", loc(parse("לאסוף את הילדים בברוכין מחר בחמש"))); // known town: any kind
    }

    @Test public void noFalsePlaces() throws Exception {
        for (String s : new String[]{"פגישה בזום מחר ב-10", "פגישה בטלפון עם דני מחר ב-10", "פגישה בנוגע לחוזה מחר ב-10",
                "פגישה עם ברק מחר ב-10", "פגישה עם שני בשני ב-10", "פגישה בעוד שבוע בשבע בערב", "לקנות חלב בבקשה",
                "לשלוח את הדוח בדחיפות מחר", "לקנות פרחים בדרך הביתה", "לאסוף את הילדים וברוכין מחר בחמש"}) {
            assertEquals(s, "", loc(parse(s)));
        }
        assertTrue(parse("פגישה בשבע בערב בברוכין").contains(" 19:00\""));
    }
}
