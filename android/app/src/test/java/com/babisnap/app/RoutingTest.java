package com.babisnap.app;

import static org.junit.Assert.*;
import static com.babisnap.app.Classifier.Kind.*;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Simulates VoiceActivity: classify the spoken phrase, then parse the stripped text
 * (calendar re-parses dest-stripped text; task/note skip dest-word stripping).
 */
public class RoutingTest {
    static final int Y = 2026, M = 10, D = 22, DOW = 4, HOUR = 10, MIN = 15, DUR = 60;

    static HebrewParser parser() throws Exception { return new HebrewParser(ParserTest.r("Code.gs")); }

    static JSONObject parse(HebrewParser p, String text, boolean skipDestStrip) throws Exception {
        return new JSONObject(p.parseJson(text, Y, M, D, DOW, DUR, HOUR, MIN, skipDestStrip));
    }

    static JSONObject parseAt(HebrewParser p, String text, int y, int m, int d, int dow, int hour, int min) throws Exception {
        return new JSONObject(p.parseJson(text, y, m, d, dow, DUR, hour, min, false));
    }

    static final class Out {
        final Classifier.Kind kind;
        final String title;
        final JSONObject parsed;
        final String stripped;
        Out(Classifier.Kind k, String t, JSONObject p, String s) { kind = k; title = t; parsed = p; stripped = s; }
    }

    static Out run(HebrewParser p, String spoken) throws Exception {
        JSONObject parsed = parse(p, spoken, false);
        boolean explicitTime = !parsed.optBoolean("timeDefaulted", true) || parsed.optBoolean("allDay", false);
        Classifier.Result c = Classifier.classify(spoken, explicitTime);
        if (c.kind == MEETING) {
            if (c.stripped.isEmpty()) {
                parsed.put("title", "אירוע");
                parsed.put("titleFull", "אירוע");
            } else if (!c.stripped.equals(Classifier.normalize(spoken))) {
                parsed = parse(p, c.stripped, false);
            }
            return new Out(c.kind, parsed.optString("title"), parsed, c.stripped);
        }
        if (!c.stripped.isEmpty() && !c.stripped.equals(Classifier.normalize(spoken))) {
            parsed = parse(p, c.stripped, true);
        }
        String title = parsed.optString("titleFull", parsed.optString("title", "")).trim();
        if (title.isEmpty()) title = spoken;
        return new Out(c.kind, title, parsed, c.stripped);
    }

    static void expect(HebrewParser p, String spoken, Classifier.Kind kind, String title) throws Exception {
        Out o = run(p, spoken);
        assertEquals(spoken + " kind=" + o.kind + " title=" + o.title, kind, o.kind);
        assertEquals(spoken + " title=" + o.title, title, o.title);
    }

    @Test public void samplePhrases() throws Exception {
        HebrewParser p = parser();
        expect(p, "יומן מחר בעשר רופא שיניים", MEETING, "רופא שיניים");
        expect(p, "פגישה עם דני ביום רביעי בשתיים", MEETING, "פגישה עם דני");
        expect(p, "קבע פגישה עם רואה החשבון", MEETING, "פגישה עם רואה החשבון");
        expect(p, "תכניס ליומן איסוף ילדים בארבע", MEETING, "איסוף ילדים");
        expect(p, "תזכורת להתקשר לבנק מחר", TASK, "להתקשר לבנק");
        expect(p, "משימה לשלם חשמל", TASK, "לשלם חשמל");
        expect(p, "הערה לבדוק מחירים", NOTE, "לבדוק מחירים");
        expect(p, "ישיבה עם הצוות", MEETING, "ישיבה עם הצוות");
        expect(p, "זום עם אבי", MEETING, "זום עם אבי");
        expect(p, "תור לרופא", MEETING, "תור לרופא");
        expect(p, "מילואים יום שלם בשבוע הבא", MEETING, "מילואים");
        expect(p, "לקנות חלב", TASK, "לקנות חלב");
        expect(p, "למשימה לשלוח מייל ליוסי מחר", TASK, "לשלוח מייל ליוסי");
        expect(p, "פגישת עבודה במשרד", MEETING, "פגישת עבודה");
        expect(p, "אירוע חתונה במוצאי שבת", MEETING, "חתונה");
        expect(p, "תזכיר לי פגישה עם אורי מחר בארבע", MEETING, "פגישה עם אורי");
        expect(p, "מחר בעשר רופא שיניים", MEETING, "רופא שיניים");
        expect(p, "להתקשר לדני מחר בעשר", TASK, "להתקשר לדני");
        expect(p, "להערה הקוד של השער 1234", NOTE, "הקוד של השער 1234");
        expect(p, "יום הולדת לדני", MEETING, "יום הולדת לדני");
        expect(p, "תוסיף ללוח השנה חוג לנועה בחמש", MEETING, "חוג לנועה");
        expect(p, "תוסיף לקלנדר תור לרופא", MEETING, "תור לרופא");
        expect(p, "תקבע לי פגישה עם דני", MEETING, "פגישה עם דני");
        expect(p, "זימון מחר בעשר עם דני", MEETING, "זימון עם דני");
        expect(p, "יומן מחר בעשר פגישה עם דני", MEETING, "פגישה עם דני");
        expect(p, "הערה ביומן של דני יש הרבה פגישות", NOTE, "ביומן של דני יש הרבה פגישות");
        expect(p, "אסיפת הורים ביום שלישי בתשע", MEETING, "אסיפת הורים");
        expect(p, "ראיון עבודה ביום שני", MEETING, "ראיון עבודה");
        expect(p, "שיחת וידאו עם אבי מחר", MEETING, "שיחת וידאו עם אבי");
    }

    @Test public void eventNamesWithoutClockAreAllDay() throws Exception {
        HebrewParser p = parser();
        Out o = run(p, "יום הולדת לדני");
        assertEquals(MEETING, o.kind);
        assertTrue(o.parsed.toString(), o.parsed.optBoolean("allDay") && !o.parsed.optBoolean("timeDefaulted"));
        o = run(p, "מילואים בשבוע הבא");
        assertEquals(MEETING, o.kind);
        assertTrue(o.parsed.toString(), o.parsed.optBoolean("allDay"));
        assertEquals("2026-10-25 00:00", o.parsed.optString("startStr"));
        o = run(p, "פגישה עם דני מחר");
        assertEquals(MEETING, o.kind);
        assertFalse("פגישה without a clock stays 09:00, not all-day", o.parsed.optBoolean("allDay"));
        assertTrue(o.parsed.optBoolean("timeDefaulted"));
    }

    @Test public void motzaeiShabbatIsSaturdayEvening() throws Exception {
        Out o = run(parser(), "אירוע חתונה במוצאי שבת");
        assertEquals(MEETING, o.kind);
        assertEquals("חתונה", o.title);
        assertEquals("2026-10-24 20:00", o.parsed.optString("startStr"));
        assertFalse(o.parsed.optBoolean("allDay"));
    }

    @Test public void digitClockMinutesStayOutOfTitle() throws Exception {
        HebrewParser p = parser();
        Out o = run(p, "קבע ללכת לקניות היום ב-17:00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "תרשום ביומן ללכת לקניות היום ב-17:00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן היום ב17:00 ללכת לקניות");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות היום ב17 00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות בחמש 00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות היום ב-17 30");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:30", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות היום ב-17.00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות היום ב־17:00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות היום ב–17:00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות היום בשעה שבע עשרה");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));

        o = run(p, "יומן ללכת לקניות ב-17.00");
        assertEquals(MEETING, o.kind);
        assertEquals("ללכת לקניות", o.title);
        assertEquals("2026-10-22 17:00", o.parsed.optString("startStr"));
    }

    @Test public void leftoverShinAndEveningContext() throws Exception {
        HebrewParser p = parser();
        Out o = run(p, "תרשום ביומן שהרכב צריך טסט");
        assertEquals(MEETING, o.kind);
        assertEquals("הרכב צריך טסט", o.title);

        o = run(p, "תרשום ביומן שמחר בעשר יש רופא שיניים");
        assertEquals(MEETING, o.kind);
        assertEquals("רופא שיניים", o.title);
        assertEquals("2026-10-23 10:00", o.parsed.optString("startStr"));

        o = run(p, "תרשום ביומן שיש לי פגישה עם אורי מחר בעשר");
        assertEquals(MEETING, o.kind);
        assertEquals("פגישה עם אורי", o.title);
        assertEquals("2026-10-23 10:00", o.parsed.optString("startStr"));

        o = run(p, "יומן מחר בעשר שיעור פרטי");
        assertEquals(MEETING, o.kind);
        assertEquals("שיעור פרטי", o.title);

        o = run(p, "קבע שיחה עם דני מחר בעשר");
        assertEquals(MEETING, o.kind);
        assertEquals("שיחה עם דני", o.title);

        o = run(p, "יומן מחר בעשר שמירה");
        assertEquals(MEETING, o.kind);
        assertEquals("שמירה", o.title);

        o = run(p, "ארוחת ערב מחר בשמונה");
        assertEquals(MEETING, o.kind);
        assertEquals("ארוחת ערב", o.title);
        assertEquals("2026-10-23 20:00", o.parsed.optString("startStr"));

        o = run(p, "ארוחת ערב ב-8");
        assertEquals("ארוחת ערב", o.title);
        assertEquals("2026-10-22 20:00", o.parsed.optString("startStr"));

        o = run(p, "ארוחת צהריים בשתיים");
        assertEquals("ארוחת צהריים", o.title);
        assertEquals("2026-10-22 14:00", o.parsed.optString("startStr"));

        o = run(p, "מסיבה בתשע");
        assertEquals("מסיבה", o.title);
        assertEquals("2026-10-22 21:00", o.parsed.optString("startStr"));

        o = run(p, "ארוחת בוקר בשמונה");
        assertEquals("ארוחת בוקר", o.title);
        assertEquals("2026-10-22 08:00", o.parsed.optString("startStr"));

        o = run(p, "ארוחת ערב ב-08:00");
        assertEquals("ארוחת ערב", o.title);
        assertEquals("2026-10-22 08:00", o.parsed.optString("startStr"));
    }

    @Test public void calendarDateStrippedEvenAfterWeekdayAndAtzelStaysInTitleOnly() throws Exception {
        HebrewParser p = parser();
        String base = "טיפול אצל דוקטור גולדמן אוסטאופת מודיעין";
        String title = "טיפול אצל דוקטור גולדמן אוסטאופת";
        String[] phrases = {
            base + " ב-15 לנובמבר בארבע וחצי",
            base + " ב-15 בנובמבר בארבע וחצי",
            base + " בחמש עשרה לנובמבר בארבע וחצי",
            base + " בחמישה עשר בנובמבר בארבע וחצי",
            base + " ביום ראשון ב-15 לנובמבר ב-16:30",
            base + " ביום ראשון ב-15 לנובמבר בארבע וחצי",
            base + " יום רביעי ה-15 לנובמבר בארבע וחצי",
            base + " ב-15 נובמבר בארבע וחצי",
            base + " ב-15.11 לנובמבר בארבע וחצי",
            base + " בתאריך 15 לנובמבר בארבע וחצי",
            "יומן " + base + " ב-15 לנובמבר בארבע וחצי",
            "קבע " + base + " בחמש עשרה לנובמבר בארבע וחצי",
            "יומן " + base + " ב-15.11 בארבע וחצי",
            "טיפול אצל דוקטור גולדמן אוסטאופת במודיעין ב-15 לנובמבר בארבע וחצי"
        };
        for (String spoken : phrases) {
            // VoiceActivity-style: parse, classify, maybe reparse stripped (with Nov 10 now)
            org.json.JSONObject parsed = parseAt(p, spoken, 2026, 11, 10, 2, 17, 30);
            boolean explicitTime = !parsed.optBoolean("timeDefaulted", true) || parsed.optBoolean("allDay", false);
            Classifier.Result c = Classifier.classify(spoken, explicitTime);
            assertEquals(spoken + " kind", MEETING, c.kind);
            if (!c.stripped.isEmpty() && !c.stripped.equals(Classifier.normalize(spoken))) {
                parsed = parseAt(p, c.stripped, 2026, 11, 10, 2, 17, 30);
            }
            assertEquals(spoken + " title=" + parsed.optString("title"), title, parsed.optString("title"));
            assertEquals(spoken + " loc=" + parsed.optString("location"), "מודיעין", parsed.optString("location"));
            assertEquals(spoken + " start=" + parsed.optString("startStr"), "2026-11-15 16:30", parsed.optString("startStr"));
        }
    }
}
