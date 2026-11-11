package com.babisnap.app;

import static com.babisnap.app.Classifier.Kind.*;
import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immediate dial routing, shared name match, number choice, and masking. Dated phrases stay on the old route. */
public class CallDialTest {
    private static WhatsAppRecipients.Phone mobile(String raw) {
        return new WhatsAppRecipients.Phone(raw, false, true);
    }
    private static WhatsAppRecipients.Phone land(String raw) {
        return new WhatsAppRecipients.Phone(raw, true, false);
    }
    private static WhatsAppRecipients.Contact person(String display, String given, String nick, WhatsAppRecipients.Phone... phones) {
        return new WhatsAppRecipients.Contact(display, given, "", nick, Arrays.asList(phones));
    }

    private static Classifier.Result at(String phrase, boolean explicitTime) {
        return Classifier.classify(phrase, explicitTime, false);
    }

    @Test public void immediatePhrasesAreACallAndNameThePerson() {
        assertCall("תתקשר לתמרי", "תמרי");
        assertCall("תתקשרי לדני", "דני");
        assertCall("להתקשר לאמא עכשיו", "אמא");
        assertCall("תתקשר אל דני כהן", "דני כהן");
        assertCall("תתקשר עם אמא", "אמא");
        assertCall("תתקשרו לדני", "דני");
        assertCall("להתקשר לרואה חשבון", "רואה חשבון");
        assertCall("תתקשר לאמא שלי", "אמא");
        assertCall("תתקשר לתמרי בבקשה", "תמרי");
        // the WhatsApp flag does not change a phrase that has no channel word
        assertEquals(CALL, Classifier.classify("תתקשר לתמרי", false, true).kind);
        assertEquals("תמרי", Classifier.classify("תתקשר לתמרי", false, true).stripped);
    }

    @Test public void aFutureDateOrTimeStaysOnTheOldRoute() {
        assertEquals(TASK, at("להתקשר לדני מחר בעשר", true).kind);
        assertEquals(TASK, at("להתקשר לדני מחר בעשר", false).kind);
        assertEquals(TASK, at("מחר בבוקר להתקשר לדני", true).kind);
        assertEquals(TASK, at("להתקשר לרואה חשבון ביום ראשון", false).kind);
        assertEquals(TASK, at("תזכורת להתקשר לבנק מחר", false).kind);
        assertEquals(MEETING, at("יומן להתקשר לדני ביום ראשון", false).kind);
        assertEquals(TASK, at("תתקשר לתמרי ב-16:00", false).kind);
        assertEquals(TASK, at("תתקשר לתמרי ב-16 : 00", false).kind);
        assertEquals(TASK, at("תתקשר לתמרי היום", false).kind);
        assertEquals(TASK, at("תתקשר לתמרי בעוד שעתיים", false).kind);
        assertEquals(TASK, at("תתקשר לתמרי בשש", false).kind);
        assertEquals(TASK, at("תתקשר לדני ביום שני", false).kind);
        assertEquals(TASK, at("תתקשר לתמרי ב-15.11", false).kind);
        assertEquals(TASK, at("תוסיף לי להתקשר לדני", false).kind);
        assertEquals(TASK, at("צריך להתקשר לדני", false).kind);
        assertEquals(TASK, at("לקנות חלב ולהתקשר לדני", false).kind);
        assertEquals(TASK, at("תתקשר לדני ולרות", false).kind);
        assertEquals(MEETING, at("שיחה עם דני מחר בעשר", true).kind);
        assertEquals(TASK, at("תתקשר לתמרי", true).kind);
    }

    @Test public void whatsAppChannelWordsAreNotACallAndColonsStayInTheMessage() {
        assertEquals(WHATSAPP, Classifier.classify("הודעה לתמרי בוואטסאפ", false, true).kind);
        assertEquals(WHATSAPP, Classifier.classify("תתקשר לתמרי בוואטסאפ", false, true).kind);
        assertNotEquals(CALL, Classifier.classify("תתקשר לתמרי בוואטסאפ", false, false).kind);
        assertEquals(TASK, Classifier.classify("תתקשר לתמרי בוואטסאפ", false, false).kind);
        for (String word : new String[]{"וואטסאפ", "וואצאפ", "ווטסאפ", "WhatsApp"}) {
            Classifier.Result on = Classifier.classify(word + " לתמרי: אני מגיע ב-16:00", true, true);
            assertEquals(word, WHATSAPP, on.kind);
            assertTrue(word + " => " + on.stripped, on.stripped.contains("16:00"));
            assertNotEquals(word, CALL, Classifier.classify("הודעה לתמרי ב" + word, false, true).kind);
        }
        Classifier.Result off = Classifier.classify("הודעה לתמרי בוואטסאפ", false, false);
        assertNotEquals(CALL, off.kind);
        assertNotEquals(WHATSAPP, off.kind);
    }

    @Test public void aCallIsNotACalendarItemOrABabiTkItem() {
        Classifier.Result c = at("תתקשר לתמרי", false);
        assertEquals(CALL, c.kind);
        assertEquals(RouteGate.Dest.CALL, RouteGate.dest(c.kind));
        assertFalse(RouteGate.toCalQueue(c.kind));
        assertFalse(RouteGate.toBabiTk(c.kind));
        assertFalse(RouteGate.toWhatsApp(c.kind));
        assertTrue(RouteGate.toCall(c.kind));
        try {
            BabiTkJson.build("תמרי", null, null, null, null, "call", "bsnap-call");
            fail("call must not become a BabiTk task");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("CALL"));
        }
        assertEquals(5000L, CallDial.CONFIRM_MS);
        assertEquals(5000L, CardFlow.UNDO_MS);
        assertEquals(1, CallDial.actions().size());
        assertEquals(CallDial.Action.DIAL, CallDial.actions().get(0));
        assertEquals("חייג", CallDial.actions().get(0).label);
    }

    @Test public void namesMatchNicknamesAndALamedPrefixAndPreferMobile() {
        WhatsAppRecipients.Contact tamar = person("תמר כהן", "תָּמָר", "תמרי", mobile("0501234567"));
        WhatsAppRecipients.Contact dani = person("דניאל לוי", "דניאל", "דני", mobile("0521111111"));
        List<WhatsAppRecipients.Contact> book = Arrays.asList(tamar, dani);

        WhatsAppRecipients.Matches one = CallDial.match("תמרי", book);
        assertTrue(one.one());
        assertEquals("תמר כהן", one.hits.get(0).label());
        assertTrue(CallDial.match("לתמרי", book).one());
        assertEquals("דניאל לוי", CallDial.match("דני", book).hits.get(0).displayName);
        assertEquals("דניאל לוי", CallDial.match("לדני", book).hits.get(0).displayName);

        WhatsAppRecipients.Contact other = person("תמר לוי", "תמר", "", mobile("0533333333"));
        WhatsAppRecipients.Matches many = CallDial.match("תמר", Arrays.asList(tamar, other));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, many.code);
        assertEquals(2, many.hits.size());
        assertEquals(WhatsAppRecipients.MatchCode.NONE, CallDial.match("יוסי", book).code);
        assertEquals("לא מצאתי את «יוסי» באנשי הקשר", CallDial.notFound("יוסי"));
        assertEquals(CallDial.NO_NAME, CallDial.notFound(""));
        assertEquals(CallDial.NO_PERMISSION, "אין גישה לאנשי הקשר, אז אי אפשר לחייג");

        WhatsAppRecipients.Contact both = new WhatsAppRecipients.Contact("תמר", "תמר", "", "",
            Arrays.asList(land("03-5551234"), mobile("050-123-4567")));
        assertEquals("0501234567", CallDial.chooseRaw(both));
        assertEquals("tel:0501234567", CallDial.tel(both));
        assertNotEquals(both.digits(), CallDial.chooseRaw(both));
    }

    @Test public void anyCountryNumberIsDialableAndWhatsAppStillRequiresAnIsraeliNumber() {
        WhatsAppRecipients.Contact us = person("John Smith", "John", "", mobile("+1 (415) 555-0199"));
        assertNull(us.digits());
        assertEquals(WhatsAppRecipients.MatchCode.NONE, WhatsAppRecipients.match("John", Collections.singletonList(us)).code);
        assertTrue(CallDial.match("John", Collections.singletonList(us)).one());
        assertEquals("+14155550199", CallDial.chooseRaw(us));
        assertEquals("tel:+14155550199", CallDial.tel(us));
        assertTrue(CallDial.mask("+14155550199").startsWith("+"));
        assertFalse(CallDial.mask("+14155550199").contains("5550199"));
    }

    @Test public void directCallNeedsTheToggleAndThePermission() {
        assertEquals(CallDial.Handset.CALL, CallDial.place(CallDial.Moment.TIMEOUT, true, true, true));
        assertEquals("android.intent.action.CALL", CallDial.intentAction(CallDial.Handset.CALL));
        assertEquals(CallDial.Handset.DIAL, CallDial.place(CallDial.Moment.TIMEOUT, true, false, true));
        assertEquals(CallDial.Handset.DIAL, CallDial.place(CallDial.Moment.TIMEOUT, false, true, true));
        assertEquals(CallDial.Handset.DIAL, CallDial.place(CallDial.Moment.TIMEOUT, false, false, true));
        assertEquals(CallDial.Handset.DIAL, CallDial.place(CallDial.Moment.TAP, false, false, true));
        assertEquals(CallDial.Handset.CALL, CallDial.place(CallDial.Moment.TAP, true, true, true));
        assertEquals("android.intent.action.DIAL", CallDial.intentAction(CallDial.Handset.DIAL));
        assertEquals(CallDial.Handset.NONE, CallDial.place(CallDial.Moment.TIMEOUT, true, true, false));
        assertEquals(CallDial.Handset.NONE, CallDial.place(CallDial.Moment.TAP, true, true, false));
        assertEquals(CallDial.Handset.NONE, CallDial.place(CallDial.Moment.CANCEL, true, true, true));
        assertEquals(CallDial.Handset.NONE, CallDial.place(CallDial.Moment.LEAVE, true, true, true));
        assertNull(CallDial.intentAction(CallDial.Handset.NONE));
        assertFalse(CallDial.effective(true, false));
        assertFalse(CallDial.effective(false, true));
        assertTrue(CallDial.effective(true, true));
        assertEquals(CallDial.ToggleResult.SAVED_OFF, CallDial.onToggle(false, true, false));
        assertEquals(CallDial.ToggleResult.SAVED_ON, CallDial.onToggle(true, true, false));
        assertEquals(CallDial.ToggleResult.NEED_PROMPT, CallDial.onToggle(true, false, false));
        assertEquals(CallDial.ToggleResult.NEED_SETTINGS, CallDial.onToggle(true, false, true));
        assertEquals(5000L, CallDial.CONFIRM_MS);
        WhatsAppRecipients.Contact tamar = person("תמר כהן", "תמר", "", mobile("0501234567"));
        assertEquals("שיחה · מתקשר לתמר כהן", CallDial.headline(tamar, true));
        assertEquals("שיחה · ייפתח החייגן אצל תמר כהן", CallDial.headline(tamar, false));
        assertTrue(CallDial.summary(tamar, true).contains(CallDial.HINT_AUTO));
        assertFalse(CallDial.summary(tamar, true).contains(CallDial.HINT));
        assertTrue(CallDial.summary(tamar, false).contains(CallDial.HINT));
        assertEquals(CallDial.TOGGLE_HINT, "בסוף הספירה השיחה תצא ישירות, בלי חייגן");
    }

    @Test public void maskHidesTheMiddleAndKeepsAnIsraeliMobileReadable() {
        assertEquals("050-•••-4567", CallDial.mask("0501234567"));
        assertEquals("050-•••-4567", CallDial.mask("050-123-4567"));
        assertEquals("050-•••-4567", CallDial.mask("+972501234567"));
        assertEquals("050-•••-4567", CallDial.mask("972501234567"));
        assertEquals("03-•••-1234", CallDial.mask("03-5551234"));
        String hidden = CallDial.mask("0501234567");
        assertFalse(hidden.contains("123"));
        assertTrue(hidden.contains("•••"));
        assertEquals("•••-45", CallDial.mask("12345"));
        assertFalse(CallDial.mask("12345").contains("123"));
        WhatsAppRecipients.Contact tamar = person("תמר כהן", "תמר", "", mobile("0501234567"));
        assertEquals("שיחה · ייפתח החייגן אצל תמר כהן", CallDial.headline(tamar));
        String card = CallDial.summary(tamar);
        assertTrue(card.contains("תמר כהן"));
        assertTrue(card.contains("050-•••-4567"));
        assertTrue(card.contains(CallDial.HINT));
        assertFalse(card.contains("0501234567"));
    }

    @Test public void similarNamesStayAPickerForACallToo() {
        WhatsAppRecipients.Contact rani = new WhatsAppRecipients.Contact("רני", "רני", "", "",
            Collections.singletonList(mobile("0501111111")));
        WhatsAppRecipients.Contact roni = new WhatsAppRecipients.Contact("רוני", "רוני", "", "",
            Collections.singletonList(mobile("0522222222")));
        for (String said : new String[]{"רני", "רוני"}) {
            String other = said.equals("רני") ? "רוני" : "רני";
            WhatsAppRecipients.Contact saidC = said.equals("רני") ? rani : roni;
            WhatsAppRecipients.Contact otherC = said.equals("רני") ? roni : rani;
            WhatsAppRecipients.Matches both = CallDial.match(said, Arrays.asList(otherC, saidC));
            assertTrue(both.one());
            assertEquals(said, both.hits.get(0).displayName);
            WhatsAppRecipients.Matches only = CallDial.match(said, Collections.singletonList(otherC));
            assertEquals(WhatsAppRecipients.MatchCode.MANY, only.code);
            assertFalse(only.one());
            assertEquals(other, only.hits.get(0).displayName);
        }
        assertTrue(CallDial.match("רוני", Collections.singletonList(roni)).one());
        WhatsAppRecipients.Contact tamar = new WhatsAppRecipients.Contact("תמר", "תמר", "", "",
            Collections.singletonList(mobile("0501234567")));
        assertFalse(CallDial.match("תמרי", Collections.singletonList(tamar)).one());
        WhatsAppRecipients.Contact daniel = new WhatsAppRecipients.Contact("דניאל", "דניאל", "", "",
            Collections.singletonList(mobile("0533333333")));
        assertFalse(CallDial.match("דני", Collections.singletonList(daniel)).one());
        WhatsAppRecipients.Contact cohen = new WhatsAppRecipients.Contact("רוני כהן", "רוני", "כהן", "",
            Collections.singletonList(mobile("0501234567")));
        WhatsAppRecipients.Contact levi = new WhatsAppRecipients.Contact("רוני לוי", "רוני", "לוי", "",
            Collections.singletonList(mobile("0521111111")));
        assertTrue(CallDial.match("רוני כהן", Arrays.asList(levi, cohen)).one());
        assertEquals(WhatsAppRecipients.MatchCode.MANY, CallDial.match("רוני", Arrays.asList(cohen, levi)).code);
        assertFalse(CallDial.match("רוני כהן", Collections.singletonList(roni)).one());
        assertEquals("רוני", CallDial.match("רוני כהן", Collections.singletonList(roni)).hits.get(0).displayName);
        assertTrue(CallDial.match("לרני", Arrays.asList(roni, rani)).one());
        assertEquals("רני", CallDial.match("לרני", Arrays.asList(roni, rani)).hits.get(0).displayName);
        assertTrue(CallDial.match("רָנִי", Arrays.asList(roni, rani)).one());
        WhatsAppRecipients.Contact tamari = new WhatsAppRecipients.Contact("תמרי", "תמרי", "", "",
            Collections.singletonList(mobile("0540000000")));
        assertTrue(CallDial.match("תמרי", Arrays.asList(tamar, tamari)).one());
        assertEquals("תמרי", CallDial.match("תמרי", Arrays.asList(tamar, tamari)).hits.get(0).displayName);
        WhatsAppRecipients.Contact tamarToo = new WhatsAppRecipients.Contact("תמר", "תמר", "", "",
            Collections.singletonList(mobile("0541111111")));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, CallDial.match("תמר", Arrays.asList(tamar, tamarToo)).code);
        assertFalse(CallDial.match("תמר", Arrays.asList(tamar, tamarToo)).one());
        assertEquals(WhatsAppRecipients.MatchCode.MANY, CallDial.match("רון", Collections.singletonList(roni)).code);
        assertFalse(CallDial.match("רון", Collections.singletonList(roni)).one());
        WhatsAppRecipients.Matches heard = CallDial.matchNames(Arrays.asList("רני", "רוני"), Arrays.asList(rani, roni));
        assertTrue(heard.one());
        assertEquals("רני", heard.hits.get(0).displayName);
        assertEquals(5000L, CallDial.CONFIRM_MS);
    }

    private static void assertCall(String phrase, String name) {
        Classifier.Result c = at(phrase, false);
        assertEquals(phrase + " => " + c.kind + " / " + c.stripped, CALL, c.kind);
        assertEquals(phrase, name, c.stripped);
        assertEquals(phrase, name, CallDial.nameOf(Classifier.normalize(phrase)));
    }
}
