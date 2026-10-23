package com.babisnap.app;

import static org.junit.Assert.*;
import static com.babisnap.app.Classifier.Kind.*;
import org.junit.Test;

public class ClassifierTest {
    /** {text, expected kind, explicit time said?} */
    static final Object[][] CASES = {
        // notes (information), with and without a date/time
        {"מחר בעשר יש ביקור טכנאי בבניין", NOTE, true},
        {"הקוד של השער בבניין של סבתא הוא 4521", NOTE, false},
        {"ביום שלישי יש אסיפת הורים בתשע בערב", NOTE, true},
        {"הסיסמה לוויפי של המשרד מתחילה באות ב", NOTE, false},
        {"רעיון לאפליקציה שמזהה ציפורים לפי קול", NOTE, false},
        {"הערה הרופא אמר לקחת את התרופה אחרי האוכל", NOTE, false},
        {"פתק המספר של השרברב נמצא על המקרר", NOTE, false},
        {"תרשום שהרכב צריך טסט בדצמבר", NOTE, false},
        {"ביום חמישי בשמונה החשמל מנותק בשכונה", NOTE, true},
        {"דני אוהב קפה בלי סוכר", NOTE, false},
        // tasks (something to do), with and without a date
        {"להתקשר לרואה חשבון ביום ראשון", TASK, false},
        {"לקנות מתנה לדני עד מחר בחמש", TASK, true},
        {"משימה לשלם חשבון חשמל", TASK, false},
        {"תזכיר לי להוציא את הכביסה בשש", TASK, true},
        {"לעשות בדיקות דם", TASK, false},
        {"תקנה חלב בדרך הביתה", TASK, false},
        {"צריך לחדש את הדרכון", TASK, false},
        {"לא לשכוח להחזיר את הספר לספרייה", TASK, false},
        {"להזמין שולחן במסעדה ליום שישי", TASK, false},
        // stage-2 invites: "תזמין את X לפגישה" is a meeting; "תזמין" alone is still an order
        {"תזמין את תמר לפגישה מחר בעשר", MEETING, true},
        {"תזמיני את דני כהן לישיבת צוות ביום שני", MEETING, false},
        {"להיפגש עם תמר במסעדת כפית במודיעין מחר בשבע בערב ותזמין אותה", MEETING, true},
        {"פגישה עם יוסי מחר בעשר ותשלח לו זימון", MEETING, true},
        {"תזמין פיצה לשמונה", TASK, true},
        {"תזמין את הטכנאי למחר", TASK, false},
        {"לשלוח לדני את המצגת עד יום חמישי", TASK, false},
        {"תבדוק מה עם ההחזר ממס הכנסה", TASK, false},
        {"להתקשר לדני מחר בעשר", TASK, true},
        {"הדוח השנתי חייב להיות מוגש עד סוף החודש", TASK, false},
        // meetings (time + people/gathering) -> Google Calendar
        {"פגישה עם דני מחר בשתיים", MEETING, true},
        {"ארוחת ערב עם אמא ביום שישי בשמונה", MEETING, true},
        {"זום עם הצוות ביום שני בעשר", MEETING, true},
        {"קבע פגישה עם רונית ביום רביעי", MEETING, false},
        {"להיפגש עם יוסי מחר באחת", MEETING, true},
        {"ישיבת צוות ביום ראשון בתשע", MEETING, true},
        {"פגישה עם רואה החשבון", MEETING, false},
        // command prefixes ("תוסיף לי", "שים לי", "תרשום לי"...) are ignored
        {"תוסיף לי פגישה היום בערב עם רוני בשעה 9:00 בערב משחק של מכבי", MEETING, true},
        {"שים לי ארוחת ערב עם אמא ביום שישי בשמונה בערב", MEETING, true},
        {"תוסיף לי משימה לקנות חלב", TASK, false},
        {"תרשום לי לקנות חלב", TASK, false},
        {"תוסיף לי הערה הקוד של השער הוא 4521", NOTE, false},
        {"תקבע לי פגישה עם דני", MEETING, false},
    };

    @Test public void hebrewExamples() {
        StringBuilder bad = new StringBuilder();
        for (Object[] c : CASES) {
            Classifier.Kind k = Classifier.classify((String) c[0], (Boolean) c[2]).kind;
            if (k != c[1]) bad.append(c[0]).append(" => ").append(k).append(" (expected ").append(c[1]).append(")\n");
        }
        assertEquals(bad.toString(), "", bad.toString());
    }

    @Test public void requiredExamples() {
        assertEquals(NOTE, Classifier.classify("מחר בעשר יש ביקור טכנאי בבניין", true).kind);
        assertEquals(TASK, Classifier.classify("להתקשר לרואה חשבון ביום ראשון", false).kind);
    }

    @Test public void triggerWordsAreStripped() {
        assertEquals("לשלם חשבון חשמל", Classifier.classify("משימה, לשלם חשבון חשמל", false).stripped);
        assertEquals("להוציא את הכביסה", Classifier.classify("תזכיר לי להוציא את הכביסה", false).stripped);
        assertEquals("הקוד הוא 4521", Classifier.classify("הערה: הקוד הוא 4521", false).stripped);
        assertEquals("הרכב צריך טסט", Classifier.classify("תרשום שהרכב צריך טסט", false).stripped);
    }

    @Test public void babitkBody() {
        String j = BabiTkJson.build("לקנות \"מתנה\"", "2026-10-23", "17:00", null, "", "task", "u-1");
        assertEquals("{\"title\":\"לקנות \\\"מתנה\\\"\",\"due_date\":\"2026-10-23\",\"due_time\":\"17:00\",\"source\":\"voice\",\"client_id\":\"u-1\",\"type\":\"task\",\"board\":\"notebook\"}", j);
        String n = BabiTkJson.build("קוד", null, null, "x", "בית", "note", "u-2");
        assertFalse(n.contains("due_")); assertTrue(n.contains("\"type\":\"note\"") && n.contains("\"location\":\"בית\""));
    }

    @Test public void responseOutcomes() {
        assertEquals(BabiTkJson.Outcome.DONE, BabiTkJson.outcome(201));
        assertEquals(BabiTkJson.Outcome.FAILED, BabiTkJson.outcome(400));
        assertEquals(BabiTkJson.Outcome.UNAUTHORIZED, BabiTkJson.outcome(401));
        assertEquals(BabiTkJson.Outcome.RATE_LIMITED, BabiTkJson.outcome(429));
        assertEquals(BabiTkJson.Outcome.RETRY, BabiTkJson.outcome(500));
        assertEquals(BabiTkJson.Outcome.RETRY, BabiTkJson.outcome(-1));
    }
}
