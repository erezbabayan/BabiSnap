package com.babisnap.app;

import static org.junit.Assert.*;
import static com.babisnap.app.Classifier.Kind.*;
import org.junit.Test;

public class ClassifierTest {
    /** {text, expected kind, explicit time said?} */
    static final Object[][] CASES = {
        // notes (information), with and without a date/time
        {"מחר בעשר יש ביקור טכנאי בבניין", MEETING, true},
        {"הקוד של השער בבניין של סבתא הוא 4521", NOTE, false},
        {"ביום שלישי יש אסיפת הורים בתשע בערב", MEETING, true},
        {"הסיסמה לוויפי של המשרד מתחילה באות ב", NOTE, false},
        {"רעיון לאפליקציה שמזהה ציפורים לפי קול", NOTE, false},
        {"הערה הרופא אמר לקחת את התרופה אחרי האוכל", NOTE, false},
        {"פתק המספר של השרברב נמצא על המקרר", NOTE, false},
        {"תרשום שהרכב צריך טסט בדצמבר", NOTE, false},
        {"ביום חמישי בשמונה החשמל מנותק בשכונה", MEETING, true},
        {"דני אוהב קפה בלי סוכר", NOTE, false},
        // v2.9: default is NOTE; information without an action verb stays a note (STT may drop "הערה")
        {"קוד לשער 1234", NOTE, false},
        {"הקוד לשער 1234", NOTE, false},
        {"הערה: הקוד של השער הוא 1234", NOTE, false},
        {"תרשום הערה הקוד לשער 1234", NOTE, false},
        {"פתק החניה בקומה מינוס 2", NOTE, false},
        {"הסיסמה של הווייפיי היא אבגד1234", NOTE, false},
        {"מספר החשבון של רואה החשבון 123456", NOTE, false},
        {"מחר יש חוג לנועה בחמש", MEETING, true},
        // tasks (something to do), with and without a date
        {"לקנות מחר חלב", TASK, false},
        {"מחר לקנות חלב", TASK, false},
        {"מחר בבוקר להתקשר לדני", TASK, true},
        {"בעשר לשלם ארנונה", TASK, true},
        {"חלב לקנות בדרך", TASK, false},
        {"אמא ביקשה תזכיר לי את התור", TASK, false},
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
        {"תזמין גם את דני לפגישה מחר בעשר", MEETING, true},
        {"תזמיני את דני כהן לישיבת צוות ביום שני", MEETING, false},
        {"להיפגש עם תמר במסעדת כפית במודיעין מחר בשבע בערב ותזמין אותה", MEETING, true},
        {"פגישה עם יוסי מחר בעשר ותשלח לו זימון", MEETING, true},
        {"תזמין פיצה לשמונה", TASK, true},
        {"תזמין את הטכנאי למחר", TASK, false},
        {"לשלוח לדני את המצגת עד יום חמישי", TASK, false},
        {"תבדוק מה עם ההחזר ממס הכנסה", TASK, false},
        {"להתקשר לדני מחר בעשר", TASK, true},
        {"הדוח השנתי חייב להיות מוגש עד סוף החודש", TASK, false},
        // v-yoman: any "יומן" variation -> MEETING (Google Calendar), even if it reads like a task/note
        {"תכניס ליומן לקנות חלב מחר", MEETING, false},
        {"ביומן מחר בעשר טכנאי בבניין", MEETING, true},
        {"יומן להתקשר לדני ביום ראשון", MEETING, false},
        {"תרשום ביומן שהרכב צריך טסט בדצמבר", MEETING, false},
        {"שים לי ביומן ארוחה עם אמא", MEETING, false},
        {"תוסיף ללוח השנה חוג לנועה בחמש", MEETING, true},
        {"תוסיף לקלנדר תור לרופא", MEETING, false},
        {"מחר בתשע ליומנים של הצוות סקירה", MEETING, true},
        {"הערה ביומן של דני יש הרבה פגישות", NOTE, false},
        {"פתק היומן הישן נמצא במגירה", NOTE, false},
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
        // v2.6: more meeting words (also ו/ה/ל-prefixed) and invite verbs
        {"זימון מחר בעשר עם דני", MEETING, true},
        {"זימון עם דני", MEETING, false},
        {"ישיבה עם הצוות", MEETING, false},
        {"מפגש הורים ביום שלישי", MEETING, false},
        {"ראיון עבודה ביום שני", MEETING, false},
        {"תור לרופא שיניים ביום חמישי", MEETING, false},
        {"כנס סייבר בתל אביב ביום רביעי", MEETING, false},
        {"סטנדאפ מחר בתשע", MEETING, true},
        {"שיחת וידאו עם אבי מחר", MEETING, false},
        {"והפגישה עם דני מחר בעשר", MEETING, true},
        {"לפגישה עם דני מחר", MEETING, false},
        {"ניפגש עם יוסי מחר באחת", MEETING, true},
        {"אירוע חברה ביום חמישי", MEETING, false},
        {"קביעה עם רונית ביום שני", MEETING, false},
        {"שיחה עם דני מחר בעשר", MEETING, true},
        {"לזמן את תמר לפגישה מחר", MEETING, false},
        {"פגישה בברוכין ביום שני ב-11:00 לראות את הבית לזמן את תמרי", MEETING, true},
        {"פגישה עם דני מחר בעשר ותשלח לו זימון", MEETING, true},
        // routing-words 2026-11-10
        {"יומן מחר בעשר רופא שיניים", MEETING, true},
        {"מחר בעשר רופא שיניים", MEETING, true},
        {"תזכיר לי פגישה עם אורי מחר בארבע", MEETING, true},
        {"תזכורת להתקשר לבנק מחר", TASK, false},
        {"למשימה לשלוח מייל ליוסי מחר", TASK, false},
        {"להערה הקוד של השער 1234", NOTE, false},
        {"יום הולדת לדני", MEETING, false},
        {"מילואים יום שלם בשבוע הבא", MEETING, true},
        {"אירוע חתונה במוצאי שבת", MEETING, false},
        {"אסיפת הורים ביום שלישי בתשע", MEETING, true},
        {"קבע פגישה עם רואה החשבון", MEETING, false},
        {"תור לרופא", MEETING, false},
        {"זום עם אבי", MEETING, false},
        {"ישיבה עם הצוות", MEETING, false},
        {"פגישת עבודה במשרד", MEETING, false},
    };

    @Test public void hebrewExamples() {
        StringBuilder bad = new StringBuilder();
        for (Object[] c : CASES) {
            Classifier.Result live = Classifier.classify((String) c[0], (Boolean) c[2]);
            Classifier.Result off = Classifier.classify((String) c[0], (Boolean) c[2], false);
            if (live.kind != c[1]) bad.append(c[0]).append(" => ").append(live.kind).append(" (expected ").append(c[1]).append(")\n");
            if (off.kind != live.kind || !off.stripped.equals(live.stripped))
                bad.append(c[0]).append(" flag-off diverged\n");
            if (live.kind == WHATSAPP) bad.append(c[0]).append(" live path returned WHATSAPP\n");
        }
        assertEquals(bad.toString(), "", bad.toString());
    }

    @Test public void requiredExamples() {
        assertEquals(MEETING, Classifier.classify("מחר בעשר יש ביקור טכנאי בבניין", true).kind);
        assertEquals(TASK, Classifier.classify("להתקשר לרואה חשבון ביום ראשון", false).kind);
    }

    @Test public void triggerWordsAreStripped() {
        assertEquals("לשלם חשבון חשמל", Classifier.classify("משימה, לשלם חשבון חשמל", false).stripped);
        assertEquals("להוציא את הכביסה", Classifier.classify("תזכיר לי להוציא את הכביסה", false).stripped);
        assertEquals("הקוד הוא 4521", Classifier.classify("הערה: הקוד הוא 4521", false).stripped);
        assertEquals("הרכב צריך טסט", Classifier.classify("תרשום שהרכב צריך טסט", false).stripped);
        assertEquals("לשלוח מייל ליוסי מחר", Classifier.classify("למשימה לשלוח מייל ליוסי מחר", false).stripped);
        assertEquals("הקוד של השער 1234", Classifier.classify("להערה הקוד של השער 1234", false).stripped);
        assertEquals("פגישה עם רואה החשבון", Classifier.classify("קבע פגישה עם רואה החשבון", false).stripped);
        assertEquals("פגישה עם אורי מחר בארבע", Classifier.classify("תזכיר לי פגישה עם אורי מחר בארבע", true).stripped);
        assertEquals("מחר בעשר רופא שיניים", Classifier.classify("יומן מחר בעשר רופא שיניים", true).stripped);
        assertEquals("ביומן של דני יש הרבה פגישות", Classifier.classify("הערה ביומן של דני יש הרבה פגישות", false).stripped);
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

    @Test public void explicitNoteWordIsStrippedAndNumberKept() {
        assertEquals("הקוד של השער הוא 1234", Classifier.classify("הערה: הקוד של השער הוא 1234", false).stripped);
        assertEquals("הקוד לשער 1234", Classifier.classify("תרשום הערה הקוד לשער 1234", false).stripped);
        assertEquals("קוד לשער 1234", Classifier.classify("קוד לשער 1234", false).stripped);
    }

    @Test public void normalizeKeepsClockColon() {
        assertEquals("ללכת לקניות היום ב-17:00", Classifier.normalize("ללכת לקניות היום ב-17:00"));
        assertEquals("קבע ללכת לקניות היום ב-17:00", Classifier.normalize("קבע ללכת לקניות היום ב-17:00"));
        assertEquals("יומן היום ב17:00 ללכת לקניות", Classifier.normalize("יומן היום ב17:00 ללכת לקניות"));
        // a colon that is not a clock time is still stripped
        assertEquals("הערה הקוד הוא 4521", Classifier.normalize("הערה: הקוד הוא 4521"));
        assertEquals("ללכת לקניות היום ב-17:00", Classifier.classify("קבע ללכת לקניות היום ב-17:00", true).stripped);
        assertEquals("היום ב17:00 ללכת לקניות", Classifier.classify("יומן היום ב17:00 ללכת לקניות", true).stripped);
        assertEquals("ללכת לקניות היום ב-17:00", Classifier.normalize("ללכת לקניות היום ב־17:00"));
        assertEquals("ללכת לקניות היום ב-17:00", Classifier.normalize("ללכת לקניות היום ב–17:00"));
        assertEquals("ללכת לקניות היום ב-17:00", Classifier.normalize("ללכת לקניות היום ב—17:00"));
        assertEquals("היום ב-17:00 ללכת לקניות", Classifier.classify("יומן היום ב־17:00 ללכת לקניות", true).stripped);
        assertEquals("טיפול אצל דוקטור ב-15.11 בארבע וחצי", Classifier.normalize("טיפול אצל דוקטור ב-15.11 בארבע וחצי"));
        assertEquals("טיפול אצל דוקטור ב-15/11 בארבע וחצי", Classifier.normalize("טיפול אצל דוקטור ב-15/11 בארבע וחצי"));
        assertEquals("טיפול אצל דוקטור ב-17.11.26 בשעה 10", Classifier.normalize("טיפול אצל דוקטור ב-17.11.26 בשעה 10"));
        assertEquals("טיפול אצל דוקטור ב-15.11 בארבע וחצי", Classifier.classify("יומן טיפול אצל דוקטור ב-15.11 בארבע וחצי", true).stripped);
    }

    @Test public void connectorShinAfterCommandIsStrippedButRealShinWordsStay() {
        assertEquals("הרכב צריך טסט", Classifier.classify("תרשום ביומן שהרכב צריך טסט", false).stripped);
        assertEquals("מחר בעשר יש רופא שיניים", Classifier.classify("תרשום ביומן שמחר בעשר יש רופא שיניים", true).stripped);
        assertEquals("יש לי פגישה עם אורי מחר בעשר", Classifier.stripConnectorShin("שיש לי פגישה עם אורי מחר בעשר"));
        assertEquals("יש לי פגישה עם אורי מחר בעשר", Classifier.classify("תרשום ביומן שיש לי פגישה עם אורי מחר בעשר", true).stripped);
        assertEquals("מחר בעשר שיעור פרטי", Classifier.classify("יומן מחר בעשר שיעור פרטי", true).stripped);
        assertEquals("שיחה עם דני מחר בעשר", Classifier.classify("קבע שיחה עם דני מחר בעשר", true).stripped);
        assertEquals("מחר בעשר שמירה", Classifier.classify("יומן מחר בעשר שמירה", true).stripped);
        assertEquals("שיעור פרטי", Classifier.stripConnectorShin("שיעור פרטי"));
        assertEquals("שמירה", Classifier.stripConnectorShin("שמירה"));
    }

    static final String[] WA_TOKENS = {
        "וואטסאפ", "WhatsApp", "whatsapp", "WHATSAPP", "ווטסאפ", "בואטסאפ", "בוואטסאפ",
        "לוואטסאפ", "לווטסאפ", "בווטסאפ", "הוואטסאפ", "מוואטסאפ", "ובוואטסאפ", "ובואטסאפ", "לבואטסאפ",
        "ובווטסאפ", "הווטסאפ", "מווטסאפ", "ווואטסאפ", "וווטסאפ",
        "ל-וואטסאפ", "ב-וואטסאפ", "ל-ווטסאפ", "ב-ווטסאפ",
        "וואצאפ", "ווצאפ", "בוואצאפ", "לוואצאפ", "בווצאפ", "לווצאפ"
    };

    @Test public void channelWordIsNotADestinationUntilEnabled() {
        for (String w : WA_TOKENS) {
            String phrase = w + " פגישה עם דני מחר בעשר";
            Classifier.Result live = Classifier.classify(phrase, true);
            Classifier.Result off = Classifier.classify(phrase, true, false);
            assertNotEquals(w, WHATSAPP, live.kind);
            assertEquals(w, live.kind, off.kind);
            assertEquals(w, live.stripped, off.stripped);
            assertTrue(w + " => " + live.stripped, live.stripped.contains(w.equals("WhatsApp") || w.equals("whatsapp") || w.equals("WHATSAPP") ? "whatsapp" : w) || live.stripped.toLowerCase(java.util.Locale.US).contains("whatsapp"));
        }
        // "לוואטסאפ" stays an ordinary word on the live path (not added to the verb blocklist).
        assertEquals(Classifier.classify("לוואטסאפ לדני", false).kind, Classifier.classify("לוואטסאפ לדני", false, false).kind);
        assertNotEquals(WHATSAPP, Classifier.classify("לוואטסאפ לדני", false).kind);
    }

    @Test public void enabledFlagDoesNotChangePhrasesWithoutChannelWord() {
        for (Object[] c : CASES) {
            String text = (String) c[0];
            boolean time = (Boolean) c[2];
            Classifier.Result a = Classifier.classify(text, time);
            Classifier.Result b = Classifier.classify(text, time, true);
            assertEquals(text, a.kind, b.kind);
            assertEquals(text, a.stripped, b.stripped);
        }
    }

    @Test public void channelWordRoutesToWhatsAppAndLeavesTheMessage() {
        for (String w : WA_TOKENS) {
            Classifier.Result c = Classifier.classify(w + " לדני אני מאחר", false, true);
            assertEquals(w, WHATSAPP, c.kind);
            assertEquals(w, "לדני אני מאחר", c.stripped);
            assertFalse(c.stripped.toLowerCase(java.util.Locale.US).contains("whatsapp"));
            assertFalse(c.stripped.contains("וואטסאפ"));
            assertFalse(c.stripped.contains("ווטסאפ"));
            assertFalse(c.stripped.contains("בואטסאפ"));
            assertFalse(c.stripped.contains("וואצאפ"));
            assertFalse(c.stripped.contains("ווצאפ"));
            assertFalse(c.stripped.contains("בוואטסאפ"));
        }
        assertEquals("פגישה עם דני מחר בעשר", Classifier.classify("וואטסאפ פגישה עם דני מחר בעשר", true, true).stripped);
        assertEquals("לקנות חלב", Classifier.classify("בוואטסאפ לקנות חלב", false, true).stripped);
        assertEquals("למשימה לשלוח מייל ליוסי", Classifier.classify("וואטסאפ למשימה לשלוח מייל ליוסי", false, true).stripped);
        assertEquals("הערה הקוד הוא 1234", Classifier.classify("הערה בוואטסאפ הקוד הוא 1234", false, true).stripped);
        assertEquals(NOTE, Classifier.classify("הערה בוואטסאפ הקוד הוא 1234", false).kind);
        assertEquals("אני מאחר", Classifier.classify("אני מאחר וואטסאפ", false, true).stripped);
        assertEquals("תגיד לדני שאני מאחר", Classifier.classify("תגיד לדני בוואטסאפ שאני מאחר", false, true).stripped);
        assertEquals("הגעתי", Classifier.classify("וואטסאפ שהגעתי", false, true).stripped);
        assertEquals("שיעור פסנתר", Classifier.classify("וואטסאפ שיעור פסנתר", false, true).stripped);
        assertEquals("", Classifier.classify("וואטסאפ", false, true).stripped);
        assertEquals("לדני", Classifier.classify("וואטסאפ, לדני", false, true).stripped);
        assertNotEquals(WHATSAPP, Classifier.classify("וואטסאפון לדני", false, true).kind);
        assertNotEquals(WHATSAPP, Classifier.classify("whatsappbusiness today", false, true).kind);
        // one destination: the kind is WhatsApp, not also a meeting
        assertEquals(WHATSAPP, Classifier.classify("יומן וואטסאפ מחר בעשר רופא", true, true).kind);
    }
}
