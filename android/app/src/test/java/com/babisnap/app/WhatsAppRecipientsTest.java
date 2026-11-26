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
            {"שי", "ישי"}, {"אבי", "אביב"}, {"רון", "רוני"}, {"רון", "רונן"}, {"טל", "טלי"}, {"רן", "רני"}
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
        WhatsAppRecipients.Matches variantFull = WhatsAppRecipients.match("רני כהן", Collections.singletonList(roni));
        assertTrue(variantFull.one());
        assertEquals("רוני כהן", variantFull.hits.get(0).displayName);
        WhatsAppRecipients.Matches exactRani = WhatsAppRecipients.match("רני כהן", Arrays.asList(roni, rani));
        assertTrue(exactRani.one());
        assertEquals("רני כהן", exactRani.hits.get(0).displayName);
        assertTrue(WhatsAppRecipients.match("רני", Arrays.asList(named("רני", "0500000001"), named("רוני", "0500000002"))).one());
    }

    @Test public void medialVavAndYodAreASpellingVariant() {
        assertTrue(WhatsAppRecipients.spellingVariant("רני", "רוני"));
        assertTrue(WhatsAppRecipients.spellingVariant("שרה", "שירה"));
        assertFalse(WhatsAppRecipients.spellingVariant("רני", "רני"));
        assertFalse(WhatsAppRecipients.spellingVariant("רון", "רוני"));
        WhatsAppRecipients.Contact rani = named("רני", "0501111111");
        WhatsAppRecipients.Contact roni = named("רוני", "0522222222");
        WhatsAppRecipients.Contact dani = named("דני", "0533333333");
        WhatsAppRecipients.Matches onlyRani = WhatsAppRecipients.match("רוני", Collections.singletonList(rani));
        assertTrue(onlyRani.one());
        assertEquals("רני", onlyRani.hits.get(0).displayName);
        WhatsAppRecipients.Matches onlyRoni = WhatsAppRecipients.match("רני", Collections.singletonList(roni));
        assertTrue(onlyRoni.one());
        assertEquals("רוני", onlyRoni.hits.get(0).displayName);
        WhatsAppRecipients.Matches saidRoni = WhatsAppRecipients.match("רוני", Arrays.asList(rani, roni, dani));
        assertTrue(saidRoni.one());
        assertEquals("רוני", saidRoni.hits.get(0).displayName);
        WhatsAppRecipients.Matches saidRani = WhatsAppRecipients.match("רני", Arrays.asList(roni, rani, dani));
        assertTrue(saidRani.one());
        assertEquals("רני", saidRani.hits.get(0).displayName);

        WhatsAppRecipients.Contact sara = named("שרה", "0541111111");
        WhatsAppRecipients.Contact shira = named("שירה", "0542222222");
        assertTrue(WhatsAppRecipients.match("שירה", Collections.singletonList(sara)).one());
        assertEquals("שרה", WhatsAppRecipients.match("שירה", Collections.singletonList(sara)).hits.get(0).displayName);
        assertTrue(WhatsAppRecipients.match("שרה", Collections.singletonList(shira)).one());
        assertEquals("שירה", WhatsAppRecipients.match("שרה", Collections.singletonList(shira)).hits.get(0).displayName);
        assertTrue(WhatsAppRecipients.match("דיני", Collections.singletonList(named("דני", "0571111111"))).one());
        assertTrue(WhatsAppRecipients.match("גיל", Collections.singletonList(named("גל", "0572222222"))).one());

        WhatsAppRecipients.Contact reini = named("ריני", "0551111111");
        WhatsAppRecipients.Matches two = WhatsAppRecipients.match("רני", Arrays.asList(dani, roni, reini));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, two.code);
        assertEquals(2, two.hits.size());
        assertFalse(two.hits.contains(dani));

        WhatsAppRecipients.Contact raniCohen = person("רני כהן", "רני", "", mobile("0561111111"));
        WhatsAppRecipients.Contact moshe = person("משה כהן", "משה", "", mobile("0562222222"));
        WhatsAppRecipients.Matches full = WhatsAppRecipients.match("רוני כהן", Arrays.asList(moshe, raniCohen));
        assertTrue(full.one());
        assertEquals("רני כהן", full.hits.get(0).displayName);
    }

    @Test public void imaSheliAndFullNameWorkInBothWhatsAppOrders() {
        WhatsAppRecipients.Contact imaSheli = person("אמא שלי", "אמא", "", mobile("0501111111"));
        WhatsAppRecipients.Contact imaDani = person("אמא של דני", "אמא", "", mobile("0502222222"));
        WhatsAppRecipients.Contact ima = person("אמא", "אמא", "", mobile("0503333333"));
        List<WhatsAppRecipients.Contact> imaBook = Arrays.asList(imaSheli, imaDani, ima);

        WhatsAppRecipients.Resolution channelFirst = WhatsAppRecipients.resolve("וואטסאפ לאמא שלי היי", imaBook);
        assertEquals("אמא שלי", channelFirst.command.name);
        assertEquals("היי", channelFirst.command.body);
        assertTrue(channelFirst.matches.one());
        assertEquals("אמא שלי", channelFirst.matches.hits.get(0).displayName);

        WhatsAppRecipients.Resolution withBody = WhatsAppRecipients.resolve("וואטסאפ לאמא שלי מה שלומך", imaBook);
        assertEquals("אמא שלי", withBody.command.name);
        assertEquals("מה שלומך", withBody.command.body);
        assertTrue(withBody.matches.one());

        WhatsAppRecipients.Resolution channelAfter = WhatsAppRecipients.resolve("הודעה לאמא שלי בוואטסאפ", imaBook);
        assertEquals("אמא שלי", channelAfter.command.name);
        assertTrue(channelAfter.matches.one());
        assertEquals("אמא שלי", channelAfter.matches.hits.get(0).displayName);

        WhatsAppRecipients.Contact abaSheli = person("אבא שלי", "אבא", "", mobile("0521111111"));
        WhatsAppRecipients.Contact aba = person("אבא", "אבא", "", mobile("0522222222"));
        WhatsAppRecipients.Resolution abaHits = WhatsAppRecipients.resolve("וואטסאפ לאבא שלי מה שלומך", Arrays.asList(aba, abaSheli));
        assertTrue(abaHits.matches.one());
        assertEquals("אבא שלי", abaHits.matches.hits.get(0).displayName);
        assertEquals("מה שלומך", abaHits.command.body);

        // שלי is not a diminutive of של, so both "אמא" contacts stay a picker — not a unique "אמא של דני"
        WhatsAppRecipients.Matches noDiminutive = WhatsAppRecipients.match("אמא שלי", Arrays.asList(imaDani, ima));
        assertEquals(WhatsAppRecipients.MatchCode.MANY, noDiminutive.code);
        assertFalse(noDiminutive.one());
        assertEquals(2, noDiminutive.hits.size());

        WhatsAppRecipients.Contact efrat = person("אלחנן פרומן אפרת", "אלחנן", "", mobile("0521111111"));
        WhatsAppRecipients.Contact el1 = person("אלחנן כהן", "אלחנן", "", mobile("0522222222"));
        WhatsAppRecipients.Contact el2 = person("אלחנן לוי", "אלחנן", "", mobile("0523333333"));
        List<WhatsAppRecipients.Contact> els = Arrays.asList(el1, efrat, el2);
        WhatsAppRecipients.Resolution after = WhatsAppRecipients.resolve("הודעה לאלחנן אפרת בוואטסאפ", els);
        assertTrue(after.matches.one());
        assertEquals("אלחנן פרומן אפרת", after.matches.hits.get(0).displayName);
        WhatsAppRecipients.Resolution before = WhatsAppRecipients.resolve("וואטסאפ לאלחנן אפרת מה קורה", els);
        assertTrue(before.matches.one());
        assertEquals("אלחנן פרומן אפרת", before.matches.hits.get(0).displayName);
        assertEquals("אלחנן אפרת", before.command.name);
        assertEquals("מה קורה", before.command.body);

        WhatsAppRecipients.Contact dani = person("דני", "דני", "", mobile("0531111111"));
        WhatsAppRecipients.Resolution fallback = WhatsAppRecipients.resolve("וואטסאפ לדני שלי היי", Collections.singletonList(dani));
        assertTrue(fallback.matches.one());
        assertEquals("דני", fallback.matches.hits.get(0).displayName);
        assertEquals("היי", fallback.command.body);
    }

    @Test public void scoredMatchingPrefersMoreSpokenWordsAndKeepsTheBody() {
        WhatsAppRecipients.Contact imaSheli = person("אמא שלי", "אמא", "", mobile("0501111111"));
        WhatsAppRecipients.Contact imaDani = person("אמא של דני", "אמא", "", mobile("0502222222"));
        WhatsAppRecipients.Contact ima = person("אמא", "אמא", "", mobile("0503333333"));
        WhatsAppRecipients.Contact daniCohen = person("דני כהן", "דני", "", mobile("0504444444"));
        List<WhatsAppRecipients.Contact> book = Arrays.asList(imaSheli, imaDani, ima, daniCohen);

        WhatsAppRecipients.Matches exact = WhatsAppRecipients.match("אמא של דני", book);
        assertTrue(exact.one());
        assertEquals("אמא של דני", exact.hits.get(0).displayName);

        WhatsAppRecipients.Matches imaSheliWins = WhatsAppRecipients.match("אמא שלי", Arrays.asList(imaSheli, imaDani));
        assertTrue(imaSheliWins.one());
        assertEquals("אמא שלי", imaSheliWins.hits.get(0).displayName);

        WhatsAppRecipients.Matches clash = WhatsAppRecipients.match("אמא שלי", Collections.singletonList(imaDani));
        assertFalse(clash.one());
        assertEquals(WhatsAppRecipients.MatchCode.MANY, clash.code);

        WhatsAppRecipients.Contact imaDaniBare = person("אמא דני", "אמא", "", mobile("0505555555"));
        WhatsAppRecipients.Matches noShel = WhatsAppRecipients.match("אמא של דני", Arrays.asList(ima, imaSheli, imaDaniBare, daniCohen));
        assertTrue(noShel.one());
        assertEquals("אמא דני", noShel.hits.get(0).displayName);

        WhatsAppRecipients.Resolution wa = WhatsAppRecipients.resolve("וואטסאפ לאמא של דני היי", book);
        assertTrue(wa.matches.one());
        assertEquals("אמא של דני", wa.matches.hits.get(0).displayName);
        assertEquals("אמא של דני", wa.command.name);
        assertEquals("היי", wa.command.body);

        WhatsAppRecipients.Resolution after = WhatsAppRecipients.resolve("הודעה לאמא של דני בוואטסאפ היי", book);
        assertTrue(after.matches.one());
        assertEquals("אמא של דני", after.matches.hits.get(0).displayName);
        assertEquals("היי", after.command.body);

        WhatsAppRecipients.Contact efrat = person("אלחנן פרומן אפרת", "אלחנן", "", mobile("0521111111"));
        WhatsAppRecipients.Contact el1 = person("אלחנן כהן", "אלחנן", "", mobile("0522222222"));
        WhatsAppRecipients.Contact el2 = person("אלחנן לוי", "אלחנן", "", mobile("0523333333"));
        List<WhatsAppRecipients.Contact> els = Arrays.asList(el1, efrat, el2);
        assertTrue(WhatsAppRecipients.match("אלחנן אפרת", els).one());
        assertEquals("אלחנן פרומן אפרת", WhatsAppRecipients.match("אלחנן אפרת", els).hits.get(0).displayName);
        assertTrue(WhatsAppRecipients.match("אלחנן פרומן", els).one());
        assertEquals("אלחנן פרומן אפרת", WhatsAppRecipients.match("אלחנן פרומן", els).hits.get(0).displayName);

        WhatsAppRecipients.Contact mosheYechiel = person("משה יחיאל", "משה", "", mobile("0531111111"));
        WhatsAppRecipients.Contact mosheCohen = person("משה כהן", "משה", "", mobile("0532222222"));
        WhatsAppRecipients.Contact yechielLevi = person("יחיאל לוי", "יחיאל", "", mobile("0533333333"));
        WhatsAppRecipients.Matches twoBeatsOne = WhatsAppRecipients.match("משה יחיאל לוי", Arrays.asList(mosheCohen, yechielLevi, mosheYechiel));
        assertTrue(twoBeatsOne.one());
        assertEquals("משה יחיאל", twoBeatsOne.hits.get(0).displayName);

        WhatsAppRecipients.Matches prefixed = WhatsAppRecipients.match("לאמא של דני", book);
        assertTrue(prefixed.one());
        assertEquals("אמא של דני", prefixed.hits.get(0).displayName);
    }

    // ---- Name / message split (one recording): strict cutoff, unique name confirms, no-ל, leading ש ----

    private static WhatsAppRecipients.Contact full(String display, String given, String family, String phone) {
        return new WhatsAppRecipients.Contact(display, given, family, "", Collections.singletonList(mobile(phone)));
    }

    private static void split(String said, List<WhatsAppRecipients.Contact> book,
                              String name, String body, WhatsAppRecipients.MatchCode code) {
        WhatsAppRecipients.Resolution r = WhatsAppRecipients.resolve(said, book);
        assertEquals(said + " → name", name, r.command.name);
        assertEquals(said + " → body", body, r.command.body);
        assertEquals(said + " → code", code, r.matches.code);
    }

    @Test public void uniqueFirstNameWithAMessageOpensTheConfirmCard() {
        WhatsAppRecipients.Contact tamari = named("תמרי", "0501111111");
        List<WhatsAppRecipients.Contact> book = Arrays.asList(tamari, full("דני כהן", "דני", "כהן", "0502222222"));
        split("וואטסאפ לתמרי מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("שלח בוואטסאפ לתמרי מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("לתמרי בוואטסאפ מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ לתמרי היי מה שלומך", book, "תמרי", "היי מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("הודעה לתמרי מה שלומך בוואטסאפ", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        assertEquals(tamari, WhatsAppRecipients.resolve("וואטסאפ לתמרי מה שלומך", book).matches.hits.get(0));
    }

    @Test public void anotherContactsNameWordIsNotSwallowedFromTheMessage() {
        // «מה» is a spelling variant of «מיה»; it belongs to another contact, so it stays in the message.
        List<WhatsAppRecipients.Contact> book = Arrays.asList(named("תמרי", "0501111111"),
            full("מיה לוי", "מיה", "לוי", "0503333333"));
        split("וואטסאפ לתמרי מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ לתמרי היי מה שלומך", book, "תמרי", "היי מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ לתמרי מה נשמע", book, "תמרי", "מה נשמע", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ לתמרי הודעה מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
    }

    @Test public void extraWordsJoinTheNameOnlyWhenTheyAreThatContactsName() {
        WhatsAppRecipients.Contact love = full("תמרי אהובתי", "תמרי", "", "0501111111");
        List<WhatsAppRecipients.Contact> book = Arrays.asList(love, full("מיה לוי", "מיה", "לוי", "0503333333"));
        split("וואטסאפ לתמרי אהובתי מה שלומך", book, "תמרי אהובתי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);

        List<WhatsAppRecipients.Contact> cohen = Arrays.asList(full("תמרי כהן", "תמרי", "כהן", "0501111111"),
            named("דני", "0502222222"));
        split("וואטסאפ לתמרי כהן מה שלומך", cohen, "תמרי כהן", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ לתמרי מה שלומך", cohen, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);

        // A surname that is not saved is not part of the name: it stays visible in the message.
        List<WhatsAppRecipients.Contact> bare = Collections.singletonList(named("תמרי", "0501111111"));
        split("וואטסאפ לתמרי כהן מה שלומך", bare, "תמרי", "כהן מה שלומך", WhatsAppRecipients.MatchCode.ONE);
    }

    @Test public void sharedNameStaysAPickerAndUnknownNameFindsNobody() {
        List<WhatsAppRecipients.Contact> two = Arrays.asList(full("תמרי כהן", "תמרי", "כהן", "0501111111"),
            full("תמרי לוי", "תמרי", "לוי", "0502222222"));
        WhatsAppRecipients.Resolution both = WhatsAppRecipients.resolve("וואטסאפ לתמרי מה שלומך", two);
        assertEquals(WhatsAppRecipients.MatchCode.MANY, both.matches.code);
        assertEquals(2, both.matches.hits.size());
        assertEquals("מה שלומך", both.command.body);
        split("וואטסאפ לתמרי לוי מה שלומך", two, "תמרי לוי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);

        List<WhatsAppRecipients.Contact> none = Collections.singletonList(named("דני", "0502222222"));
        split("וואטסאפ לתמרי מה שלומך", none, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.NONE);
    }

    @Test public void possessiveNamesStillSplitCleanly() {
        List<WhatsAppRecipients.Contact> book = Arrays.asList(person("אמא שלי", "אמא", "", mobile("0501111111")),
            person("אמא של דני", "אמא", "", mobile("0502222222")), full("מיה לוי", "מיה", "לוי", "0503333333"));
        split("וואטסאפ לאמא שלי מה שלומך", book, "אמא שלי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ לאמא של דני מה שלומך", book, "אמא של דני", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
    }

    @Test public void nameSaidWithoutLamedIsFoundAndLeavesTheMessage() {
        List<WhatsAppRecipients.Contact> book = Arrays.asList(named("תמרי", "0501111111"),
            full("מיה לוי", "מיה", "לוי", "0503333333"), full("דני כהן", "דני", "כהן", "0502222222"));
        split("וואטסאפ תמרי מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("שלח בוואטסאפ תמרי מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ תמרי: מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ דני כהן מה שלומך", book, "דני כהן", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ ל תמרי מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ ל-תמרי מה שלומך", book, "תמרי", "מה שלומך", WhatsAppRecipients.MatchCode.ONE);
        // Exact words only: «מה» is not taken as מיה, so a message with no name keeps the old picker.
        WhatsAppRecipients.Resolution noName = WhatsAppRecipients.resolve("וואטסאפ מה שלומך", book);
        assertEquals(WhatsAppRecipients.Target.NONE, noName.command.target);
        assertEquals("מה שלומך", noName.command.body);
        assertEquals(WhatsAppRecipients.MatchCode.NONE, noName.matches.code);
        WhatsAppRecipients.Resolution stranger = WhatsAppRecipients.resolve("וואטסאפ אני בדרך", book);
        assertEquals(WhatsAppRecipients.Target.NONE, stranger.command.target);
        assertEquals("אני בדרך", stranger.command.body);
    }

    @Test public void theMessageKeepsItsLeadingShin() {
        List<WhatsAppRecipients.Contact> book = Collections.singletonList(named("תמרי", "0501111111"));
        split("וואטסאפ לתמרי שלומך", book, "תמרי", "שלומך", WhatsAppRecipients.MatchCode.ONE);
        split("וואטסאפ לתמרי שמעתי שאת חולה", book, "תמרי", "שמעתי שאת חולה", WhatsAppRecipients.MatchCode.ONE);
        assertEquals("שבוע טוב", WhatsAppRecipients.parse("וואטסאפ לתמרי שבוע טוב").body);
        // A connector ש before a clause is still dropped.
        assertEquals("אני בדרך", WhatsAppRecipients.parse("הודעה לאמא בוואטסאפ שאני בדרך").body);
        assertEquals("אני בדרך", WhatsAppRecipients.parse("תגיד לתמרי בוואטסאפ ש אני בדרך").body);
    }

    @Test public void heardHypothesesPreferTheOneThatFindsTheContact() {
        List<WhatsAppRecipients.Contact> book = Collections.singletonList(named("תמרי", "0501111111"));
        WhatsAppRecipients.Resolution r = WhatsAppRecipients.resolveHeard(
            Arrays.asList("וואטסאפ תמרי מה שלומך", "וואטסאפ לתמרי מה שלומך"), "וואטסאפ תמרי מה שלומך", book);
        assertTrue(r.matches.one());
        assertEquals("תמרי", r.command.name);
        assertEquals("מה שלומך", r.command.body);
    }

    @Test public void aCallKeepsTheLeftoverWordAsAPicker() {
        // Calls pass the whole spoken name: an unsaved surname after a unique first name stays a picker.
        List<WhatsAppRecipients.Contact> book = Arrays.asList(named("תמרי", "0501111111"),
            full("מיה לוי", "מיה", "לוי", "0503333333"));
        WhatsAppRecipients.Matches m = WhatsAppRecipients.matchAmong("תמרי כהן", book);
        assertEquals(WhatsAppRecipients.MatchCode.MANY, m.code);
        assertEquals("תמרי", m.usedKey);
        assertTrue(WhatsAppRecipients.matchAmong("תמרי", book).one());
    }

    private static WhatsAppRecipients.Contact named(String name, String phone) {
        return new WhatsAppRecipients.Contact(name, name, "", "", Collections.singletonList(mobile(phone)));
    }
}
