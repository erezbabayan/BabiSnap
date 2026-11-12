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

        WhatsAppRecipients.Matches diminutive = WhatsAppRecipients.match("תמרי", book);
        assertFalse(diminutive.one());
        assertEquals(WhatsAppRecipients.MatchCode.MANY, diminutive.code);
        assertEquals("תמר כהן", diminutive.hits.get(0).label());

        WhatsAppRecipients.Matches prefixed = WhatsAppRecipients.match("לתמרי", book);
        assertFalse(prefixed.one());
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

    @Test public void nearNamesNeverAutoConfirmAndExactStaysFirst() {
        String[][] pairs = {
            {"רני", "רוני"}, {"דני", "דיני"}, {"גל", "גיל"}, {"שי", "ישי"},
            {"אבי", "אביב"}, {"רון", "רוני"}, {"רון", "רונן"}, {"טל", "טלי"}, {"רן", "רני"}
        };
        for (String[] pair : pairs) {
            for (int dir = 0; dir < 2; dir++) {
                String said = pair[dir];
                String other = pair[1 - dir];
                WhatsAppRecipients.Contact a = named(said, "050111111" + dir);
                WhatsAppRecipients.Contact b = named(other, "052222222" + dir);
                WhatsAppRecipients.Matches both = WhatsAppRecipients.match(said, Arrays.asList(b, a));
                assertTrue(said + "/" + other, both.one());
                assertEquals(said, both.hits.get(0).displayName);
                WhatsAppRecipients.Matches onlyOther = WhatsAppRecipients.match(said, Collections.singletonList(b));
                assertEquals(said + " only " + other, WhatsAppRecipients.MatchCode.MANY, onlyOther.code);
                assertFalse(onlyOther.one());
                assertNotEquals(WhatsAppRecipients.MatchCode.NONE, onlyOther.code);
                assertEquals(other, onlyOther.hits.get(0).displayName);
            }
        }
        WhatsAppRecipients.Contact lone = named("רוני", "0501234567");
        assertTrue(WhatsAppRecipients.match("רוני", Collections.singletonList(lone)).one());
        WhatsAppRecipients.Contact tamar = named("תמר", "0501234567");
        WhatsAppRecipients.Matches tamari = WhatsAppRecipients.match("תמרי", Collections.singletonList(tamar));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, tamari.code);
        assertFalse(tamari.one());
        WhatsAppRecipients.Contact daniel = new WhatsAppRecipients.Contact("דניאל", "דניאל", "", "",
            Collections.singletonList(mobile("0521111111")));
        assertFalse(WhatsAppRecipients.match("דני", Collections.singletonList(daniel)).one());
        WhatsAppRecipients.Contact nick = new WhatsAppRecipients.Contact("דניאל", "דניאל", "", "דני",
            Collections.singletonList(mobile("0521111111")));
        assertTrue(WhatsAppRecipients.match("דני", Collections.singletonList(nick)).one());

        WhatsAppRecipients.Contact cohen = new WhatsAppRecipients.Contact("רוני כהן", "רוני", "כהן", "",
            Collections.singletonList(mobile("0501234567")));
        WhatsAppRecipients.Contact levi = new WhatsAppRecipients.Contact("רוני לוי", "רוני", "לוי", "",
            Collections.singletonList(mobile("0521111111")));
        WhatsAppRecipients.Matches full = WhatsAppRecipients.match("רוני כהן", Arrays.asList(levi, cohen));
        assertTrue(full.one());
        assertEquals("רוני כהן", full.hits.get(0).displayName);
        WhatsAppRecipients.Matches first = WhatsAppRecipients.match("רוני", Arrays.asList(cohen, levi));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, first.code);
        assertFalse(first.one());
        WhatsAppRecipients.Contact bare = named("רוני", "0541111111");
        WhatsAppRecipients.Matches fallback = WhatsAppRecipients.match("רוני כהן", Collections.singletonList(bare));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, fallback.code);
        assertFalse(fallback.one());
        assertEquals("רוני", fallback.hits.get(0).displayName);

        WhatsAppRecipients.Contact rani = named("רני", "0501234567");
        WhatsAppRecipients.Contact roni = named("רוני", "0521111111");
        WhatsAppRecipients.Matches lamed = WhatsAppRecipients.match("לרני", Arrays.asList(roni, rani));
        assertTrue(lamed.one());
        assertEquals("רני", lamed.hits.get(0).displayName);
        WhatsAppRecipients.Matches dotted = WhatsAppRecipients.match("רָנִי", Arrays.asList(roni, rani));
        assertTrue(dotted.one());
        assertEquals("רני", dotted.hits.get(0).displayName);
        WhatsAppRecipients.Contact savedTamari = named("תמרי", "0533333333");
        assertTrue(WhatsAppRecipients.match("תמרי", Arrays.asList(tamar, savedTamari)).one());
        assertEquals("תמרי", WhatsAppRecipients.match("תמרי", Arrays.asList(tamar, savedTamari)).hits.get(0).displayName);
        WhatsAppRecipients.Contact tamarToo = named("תמר", "0544444444");
        WhatsAppRecipients.Matches twins = WhatsAppRecipients.match("תמר", Arrays.asList(tamar, tamarToo));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, twins.code);
        assertFalse(twins.one());
        assertEquals(2, twins.hits.size());
        WhatsAppRecipients.Matches ron = WhatsAppRecipients.match("רון", Collections.singletonList(roni));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, ron.code);
        assertFalse(ron.one());
        assertEquals("רוני", ron.hits.get(0).displayName);
        WhatsAppRecipients.Matches heard = WhatsAppRecipients.matchNames(Arrays.asList("רני", "רוני"), Arrays.asList(rani, roni));
        assertTrue(heard.one());
        assertEquals("רני", heard.hits.get(0).displayName);
        WhatsAppRecipients.Matches laterExact = WhatsAppRecipients.matchNames(Arrays.asList("רון", "רני"), Arrays.asList(rani, roni));
        assertTrue(laterExact.one());
        assertEquals("רני", laterExact.hits.get(0).displayName);
    }

    @Test public void everySpokenWordMustBeInTheDisplayName() {
        WhatsAppRecipients.Contact yechiel = person("משה יחיאל", "משה", "", mobile("0501111111"));
        WhatsAppRecipients.Contact cohen = person("משה כהן", "משה", "", mobile("0502222222"));
        WhatsAppRecipients.Contact levi = person("משה לוי", "משה", "", mobile("0503333333"));
        List<WhatsAppRecipients.Contact> moshes = Arrays.asList(cohen, yechiel, levi);
        WhatsAppRecipients.Matches one = WhatsAppRecipients.match("משה יחיאל", moshes);
        assertTrue(one.one());
        assertEquals("משה יחיאל", one.hits.get(0).displayName);
        assertTrue(WhatsAppRecipients.match("יחיאל משה", moshes).one());
        assertTrue(WhatsAppRecipients.match("למשה יחיאל", moshes).one());
        assertTrue(WhatsAppRecipients.match("מֹשֶׁה יְחִיאֵל", moshes).one());

        WhatsAppRecipients.Contact efrat = person("אלחנן פרומן אפרת", "אלחנן", "", mobile("0521111111"));
        WhatsAppRecipients.Contact el1 = person("אלחנן כהן", "אלחנן", "", mobile("0522222222"));
        WhatsAppRecipients.Contact el2 = person("אלחנן לוי", "אלחנן", "", mobile("0523333333"));
        WhatsAppRecipients.Matches middle = WhatsAppRecipients.match("אלחנן אפרת", Arrays.asList(el1, efrat, el2));
        assertTrue(middle.one());
        assertEquals("אלחנן פרומן אפרת", middle.hits.get(0).displayName);

        WhatsAppRecipients.Contact longer = person("משה יחיאל אברהם", "משה", "", mobile("0531111111"));
        WhatsAppRecipients.Contact reversed = person("אברהם משה יחיאל", "אברהם", "", mobile("0532222222"));
        WhatsAppRecipients.Matches both = WhatsAppRecipients.match("משה יחיאל", Arrays.asList(cohen, longer, reversed));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, both.code);
        assertEquals(2, both.hits.size());
        assertFalse(both.hits.contains(cohen));

        WhatsAppRecipients.Matches exactWins = WhatsAppRecipients.match("משה יחיאל", Arrays.asList(longer, cohen, yechiel));
        assertTrue(exactWins.one());
        assertEquals("משה יחיאל", exactWins.hits.get(0).displayName);
        WhatsAppRecipients.Contact yechielToo = person("משה יחיאל", "משה", "", mobile("0533333333"));
        WhatsAppRecipients.Matches twins = WhatsAppRecipients.match("משה יחיאל", Arrays.asList(yechiel, yechielToo, cohen));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, twins.code);
        assertEquals(2, twins.hits.size());

        WhatsAppRecipients.Contact david = person("דוד לוי", "דוד", "", mobile("0541111111"));
        WhatsAppRecipients.Matches fallback = WhatsAppRecipients.match("משה דוד", Arrays.asList(cohen, levi, david));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, fallback.code);
        assertEquals(2, fallback.hits.size());
        assertFalse(fallback.hits.contains(david));
        assertFalse(fallback.one());

        WhatsAppRecipients.Contact rani = named("רני כהן", "0501234567");
        WhatsAppRecipients.Contact roni = named("רוני כהן", "0521111111");
        WhatsAppRecipients.Matches notRoni = WhatsAppRecipients.match("רני כהן", Collections.singletonList(roni));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, notRoni.code);
        assertFalse(notRoni.one());
        WhatsAppRecipients.Matches exactRani = WhatsAppRecipients.match("רני כהן", Arrays.asList(roni, rani));
        assertTrue(exactRani.one());
        assertEquals("רני כהן", exactRani.hits.get(0).displayName);
        assertTrue(WhatsAppRecipients.match("רני", Arrays.asList(named("רני", "0500000001"), named("רוני", "0500000002"))).one());
    }

    private static WhatsAppRecipients.Contact named(String name, String phone) {
        return new WhatsAppRecipients.Contact(name, name, "", "", Collections.singletonList(mobile(phone)));
    }
}
