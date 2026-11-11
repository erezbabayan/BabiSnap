package com.babisnap.app;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WhatsAppRecipientsTest {
    private static WhatsAppRecipients.Phone mobile(String raw) {
        return new WhatsAppRecipients.Phone(raw, false, true);
    }
    private static WhatsAppRecipients.Phone wa(String raw) {
        return new WhatsAppRecipients.Phone(raw, true, false);
    }
    private static WhatsAppRecipients.Contact person(String display, String given, String nick, WhatsAppRecipients.Phone... phones) {
        return new WhatsAppRecipients.Contact(display, given, "", nick, Arrays.asList(phones));
    }

    @Test public void erezPhraseKeepsTheSpokenMessageNotTheClockLeftover() throws Exception {
        String message = "מה קורה אני מגיע בארבע";
        String[] triggers = {"וואצאפ", "וואטסאפ", "ווטסאפ", "WhatsApp"};
        for (String trigger : triggers) {
            WhatsAppRecipients.Command plain = WhatsAppRecipients.parse(trigger + " לתמרי " + message);
            assertEquals(trigger, WhatsAppRecipients.Target.PERSON, plain.target);
            assertEquals(trigger, "תמרי", plain.name);
            assertEquals(trigger, message, plain.body);
            assertEquals(trigger, message, WhatsAppRecipients.shownText(plain));

            WhatsAppRecipients.Command colon = WhatsAppRecipients.parse(trigger + " לתמרי: " + message);
            assertEquals(trigger, "תמרי", colon.name);
            assertEquals(trigger, message, colon.body);
        }
        assertEquals("מה קורה אני מגיע ב-16:00",
            WhatsAppRecipients.parse("וואטסאפ לתמרי מה קורה אני מגיע ב-16:00").body);
        assertEquals("מה קורה אני מגיע ב-16:00",
            WhatsAppRecipients.parse("וואצאפ לתמרי: מה קורה אני מגיע ב-16:00").body);
        assertEquals("מה קורה אני מגיע 16:00",
            WhatsAppRecipients.parse("וואטסאפ לתמרי מה קורה אני מגיע 16:00").body);
        assertEquals("מחר בשמונה אגיע",
            WhatsAppRecipients.parse("וואטסאפ לתמרי מחר בשמונה אגיע").body);
        assertEquals("מחר בשמונה ב-16:00 נתראה",
            WhatsAppRecipients.parse("ווטסאפ לתמרי: מחר בשמונה ב-16:00 נתראה").body);

        HebrewParser p = new HebrewParser(ParserTest.r("Code.gs"));
        String title = new org.json.JSONObject(p.parseJson(
            "וואטסאפ לתמרי מה קורה אני מגיע ב-16:00", 2026, 11, 11, 3, 60, 10, 0)).optString("title");
        String body = WhatsAppRecipients.parse("וואטסאפ לתמרי מה קורה אני מגיע ב-16:00").body;
        assertEquals("מה קורה אני מגיע ב-16:00", body);
        assertFalse(body.equals(title));
        assertFalse("00".equals(body));
    }

    @Test public void privatePhrasesKeepTheNameAndDropTheRoute() {
        WhatsAppRecipients.Command a = WhatsAppRecipients.parse("הודעה לתמרי בוואטסאפ אני בדרך");
        assertEquals(WhatsAppRecipients.Target.PERSON, a.target);
        assertEquals("תמרי", a.name);
        assertEquals("אני בדרך", a.body);

        WhatsAppRecipients.Command b = WhatsAppRecipients.parse("וואטסאפ לתמרי: אני בדרך");
        assertEquals(WhatsAppRecipients.Target.PERSON, b.target);
        assertEquals("תמרי", b.name);
        assertEquals("אני בדרך", b.body);

        WhatsAppRecipients.Command c = WhatsAppRecipients.parse("וואטסאפ אל תמרי אני מאחר");
        assertEquals("תמרי", c.name);
        assertEquals("אני מאחר", c.body);

        WhatsAppRecipients.Command d = WhatsAppRecipients.parse("הודעה לאמא בוואטסאפ שאני בדרך");
        assertEquals("אמא", d.name);
        assertEquals("אני בדרך", d.body);
        assertEquals("שלום", WhatsAppRecipients.parse("וואטסאפ לתמרי: שלום").body);
    }

    @Test public void groupNameIsShownAndStrippedFromTheBody() {
        WhatsAppRecipients.Command a = WhatsAppRecipients.parse("הודעה לקבוצת משפחה בוואטסאפ אני בדרך");
        assertEquals(WhatsAppRecipients.Target.GROUP, a.target);
        assertEquals("משפחה", a.name);
        assertEquals("אני בדרך", a.body);
        assertEquals("וואטסאפ · קבוצה משפחה", WhatsAppRecipients.groupCardHeadline(a.name));

        WhatsAppRecipients.Command b = WhatsAppRecipients.parse("וואטסאפ לקבוצת משפחה: אני בדרך");
        assertEquals(WhatsAppRecipients.Target.GROUP, b.target);
        assertEquals("משפחה", b.name);
        assertEquals("אני בדרך", b.body);

        WhatsAppRecipients.Command multi = WhatsAppRecipients.parse("הודעה לקבוצת המשפחה של נועה בוואטסאפ מאחרים");
        assertEquals("המשפחה של נועה", multi.name);
        assertEquals("מאחרים", multi.body);
    }

    @Test public void noNameIsNotAPerson() {
        WhatsAppRecipients.Command c = WhatsAppRecipients.parse("וואטסאפ אני בדרך");
        assertEquals(WhatsAppRecipients.Target.NONE, c.target);
        assertEquals("", c.name);
        assertEquals("אני בדרך", c.body);
    }

    @Test public void hebrewNamesIgnoreNiqqudPrefixesAndMatchFirstNameOrNickname() {
        WhatsAppRecipients.Contact tamar = person("תמר כהן", "תָּמָר", "", mobile("0501234567"));
        WhatsAppRecipients.Contact dani = person("דניאל לוי", "דניאל", "דני", mobile("0521111111"));
        List<WhatsAppRecipients.Contact> book = Arrays.asList(tamar, dani);

        WhatsAppRecipients.Matches one = WhatsAppRecipients.match("תמרי", book);
        assertTrue(one.one());
        assertEquals("תמר כהן", one.hits.get(0).label());

        WhatsAppRecipients.Matches prefixed = WhatsAppRecipients.match("לתמרי", book);
        assertTrue(prefixed.one());
        assertEquals("תמר כהן", prefixed.hits.get(0).displayName);

        WhatsAppRecipients.Matches nick = WhatsAppRecipients.match("דני", book);
        assertTrue(nick.one());
        assertEquals("דני", nick.hits.get(0).nickname);

        WhatsAppRecipients.Matches full = WhatsAppRecipients.match("תמר כהן", book);
        assertTrue(full.one());
        assertEquals("תמר כהן", full.hits.get(0).displayName);

        WhatsAppRecipients.Contact other = person("תמר לוי", "תמר", "", mobile("0533333333"));
        WhatsAppRecipients.Matches many = WhatsAppRecipients.match("תמר", Arrays.asList(tamar, other));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, many.code);
        assertEquals(2, many.hits.size());
        assertEquals(WhatsAppRecipients.MatchCode.NONE, WhatsAppRecipients.match("יוסי", book).code);
        assertEquals("וואטסאפ · ייפתח אצל תמר כהן", WhatsAppRecipients.cardHeadline(tamar));
        assertEquals(5000L, CardFlow.UNDO_MS);
    }

    @Test public void spacesAndPartialFirstName() {
        WhatsAppRecipients.Contact c = person("תמר  כהן", "תמר", "", mobile("0501234567"));
        assertTrue(WhatsAppRecipients.match("תמר כהן", Collections.singletonList(c)).one());
        assertTrue(WhatsAppRecipients.match("שתמר", Collections.singletonList(c)).one());
        assertTrue(WhatsAppRecipients.match("ותמר", Collections.singletonList(c)).one());
    }

    @Test public void whatsAppNumberWinsOtherwiseMobile() {
        WhatsAppRecipients.Contact both = new WhatsAppRecipients.Contact("תמר", "תמר", "", "",
            Arrays.asList(mobile("0501234567"), wa("0529999999")));
        assertEquals("972529999999", both.digits());
        assertEquals("+972529999999", both.pretty());
        WhatsAppRecipients.Contact onlyMobile = person("תמר", "תמר", "", mobile("050-123-4567"));
        assertEquals("972501234567", onlyMobile.digits());
        assertEquals("+972501234567", WhatsAppRecipients.toIlE164("0501234567"));
        assertEquals("+972501234567", WhatsAppRecipients.toIlE164("05 01234567"));
        assertTrue(WhatsAppRecipients.toIlE164("0501234567").startsWith("+9725"));
        assertEquals("972501234567@s.whatsapp.net", WhatsAppRecipients.contactJid("0501234567"));
        assertTrue(WhatsAppRecipients.isPrivateJid(WhatsAppRecipients.contactJid("0501234567")));
        assertFalse(WhatsAppRecipients.isPrivateJid("120363@g.us"));
        assertNull(WhatsAppRecipients.toIlE164("123"));
        assertTrue(WhatsAppRecipients.isWhatsAppAccount("com.whatsapp"));
        assertTrue(WhatsAppRecipients.isWhatsAppAccount("com.whatsapp.w4b"));
        assertFalse(WhatsAppRecipients.isWhatsAppAccount("com.google"));
        assertTrue(WhatsAppRecipients.isMobileType(WhatsAppRecipients.TYPE_MOBILE));
        String url = WhatsAppRecipients.waMeUrl("972501234567", "אני בדרך");
        assertTrue(url.startsWith("https://wa.me/972501234567?text="));
        assertTrue(url.contains("%20"));
    }

    @Test public void searchFindsAContactWhenTheSpokenNameMissed() {
        WhatsAppRecipients.Contact a = person("תמר כהן", "תמר", "", mobile("0501234567"));
        WhatsAppRecipients.Contact b = person("יוסי לוי", "יוסי", "", mobile("0521111111"));
        List<WhatsAppRecipients.Contact> book = Arrays.asList(a, b);
        assertEquals(2, WhatsAppRecipients.search(book, "").size());
        assertEquals(1, WhatsAppRecipients.search(book, "כהן").size());
        assertEquals("תמר כהן", WhatsAppRecipients.search(book, "כהן").get(0).label());
        assertEquals(1, WhatsAppRecipients.search(book, "050").size());
        assertTrue(WhatsAppRecipients.search(book, "אין כזה").isEmpty());
    }
}
