package com.babisnap.app;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WhatsAppRecipientsTest {
    private static WhatsAppRecipients.Person person(String id, String name, String alias, String phone) {
        return new WhatsAppRecipients.Person(id, name, alias, WhatsAppRecipients.toIlDigits(phone));
    }

    private static final List<WhatsAppRecipients.Person> TAMRI = Arrays.asList(
        person("1", "תמר כהן", "תמרי", "0501234567"),
        person("2", "אמא שלי", "אמא", "0507654321"));

    @Test public void messageForAliasBeforeChannel() {
        WhatsAppRecipients.Resolved r = WhatsAppRecipients.resolve("הודעה לתמרי בוואטסאפ אני בדרך", TAMRI);
        assertTrue(r.unique());
        assertEquals("תמרי", r.person.alias);
        assertEquals("אני בדרך", r.body);
        assertEquals("לפתוח שיחה עם תמרי?", WhatsAppRecipients.headline(r.person));
    }

    @Test public void channelThenAliasWithColonAndElPrefix() {
        WhatsAppRecipients.Resolved colon = WhatsAppRecipients.resolve("וואטסאפ לתמרי: אני בדרך", TAMRI);
        assertTrue(colon.unique());
        assertEquals("תמרי", colon.person.alias);
        assertEquals("אני בדרך", colon.body);

        WhatsAppRecipients.Resolved el = WhatsAppRecipients.resolve("וואטסאפ אל תמרי אני מאחר", TAMRI);
        assertEquals("תמרי", el.person.alias);
        assertEquals("אני מאחר", el.body);

        WhatsAppRecipients.Resolved shin = WhatsAppRecipients.resolve("הודעה לאמא בוואטסאפ שאני בדרך", TAMRI);
        assertEquals("אמא", shin.person.alias);
        assertEquals("אני בדרך", shin.body);
    }

    @Test public void keepsShalomAndDoesNotGuessAMissingAlias() {
        assertEquals("שלום", WhatsAppRecipients.resolve("וואטסאפ לתמרי: שלום", TAMRI).body);
        assertEquals("שיעור", WhatsAppRecipients.resolve("וואטסאפ לתמרי שיעור", TAMRI).body);

        WhatsAppRecipients.Resolved missing = WhatsAppRecipients.resolve("וואטסאפ אני בדרך", TAMRI);
        assertEquals(WhatsAppRecipients.Code.CHOOSE, missing.code);
        assertNull(missing.person);
        assertEquals("אני בדרך", missing.body);

        WhatsAppRecipients.Resolved noChannel = WhatsAppRecipients.resolve("הודעה לתמרי אני בדרך", TAMRI);
        assertEquals(WhatsAppRecipients.Code.CHOOSE, noChannel.code);
        assertNull("בלי מילת וואטסאפ לא בוחרים אדם", noChannel.person);

        assertFalse(WhatsAppRecipients.resolve("הודעה לתמרי בוואטסאפ", TAMRI).body.contains("תמרי"));
        assertEquals("", WhatsAppRecipients.resolve("הודעה לתמרי בוואטסאפ", TAMRI).body);
    }

    @Test public void longestAliasWinsAndSharedAliasDoesNotPick() {
        List<WhatsAppRecipients.Person> both = Arrays.asList(
            person("1", "תמר", "תמרי", "0501234567"),
            person("2", "תמר כהן", "תמרי כהן", "0501111111"));
        WhatsAppRecipients.Resolved r = WhatsAppRecipients.resolve("וואטסאפ לתמרי כהן אני בדרך", both);
        assertTrue(r.unique());
        assertEquals("תמרי כהן", r.person.alias);
        assertEquals("אני בדרך", r.body);

        List<WhatsAppRecipients.Person> shared = Arrays.asList(
            person("1", "תמר", "תמרי", "0501234567"),
            person("2", "תמרה", "תמרי", "0502222222"));
        WhatsAppRecipients.Resolved amb = WhatsAppRecipients.resolve("וואטסאפ לתמרי היי", shared);
        assertEquals(WhatsAppRecipients.Code.CHOOSE, amb.code);
        assertNull(amb.person);
    }

    @Test public void nearerAliasWinsWhenDistancesDiffer() {
        List<WhatsAppRecipients.Person> people = Arrays.asList(
            person("1", "דני", "דני", "0501234567"),
            person("2", "דנה", "דנה", "0502222222"));
        WhatsAppRecipients.Resolved r = WhatsAppRecipients.resolve("וואטסאפ לדני היי לדנה", people);
        assertTrue(r.unique());
        assertEquals("דני", r.person.alias);
        assertFalse(r.body.contains("לדני"));
    }

    @Test public void englishChannelAndEmptyListStayAChooser() {
        WhatsAppRecipients.Resolved en = WhatsAppRecipients.resolve("WhatsApp לתמרי: היי", TAMRI);
        assertTrue(en.unique());
        assertEquals("היי", en.body);
        WhatsAppRecipients.Resolved none = WhatsAppRecipients.resolve("וואטסאפ לתמרי היי", Collections.emptyList());
        assertEquals(WhatsAppRecipients.Code.CHOOSE, none.code);
        assertNull(none.person);
        assertEquals("היי", WhatsAppRecipients.bodyFor(TAMRI.get(0), "וואטסאפ לתמרי היי"));
    }

    @Test public void israeliMobileBecomesE164AndPrivateJid() {
        assertEquals("+972501234567", WhatsAppRecipients.toIlE164("0501234567"));
        assertEquals("+972501234567", WhatsAppRecipients.toIlE164("050-123-4567"));
        assertEquals("+972501234567", WhatsAppRecipients.toIlE164("05 01234567"));
        assertEquals("+972501234567", WhatsAppRecipients.toIlE164("+972 50 123 4567"));
        assertEquals("972501234567", WhatsAppRecipients.toIlDigits("0501234567"));
        assertEquals("972501234567@s.whatsapp.net", WhatsAppRecipients.contactJid("972501234567"));
        assertEquals("972501234567@s.whatsapp.net", WhatsAppRecipients.contactJid("+972501234567"));
        assertTrue(WhatsAppRecipients.toIlE164("0501234567").startsWith("+9725"));
        assertTrue(WhatsAppRecipients.isPrivateJid(WhatsAppRecipients.contactJid("0501234567")));
        assertNull(WhatsAppRecipients.toIlE164("123"));
        assertNull(WhatsAppRecipients.toIlE164(null));
        assertNull(WhatsAppRecipients.contactJid("123"));
        assertNull(WhatsAppRecipients.contactJid("972501234567-1620000000"));
        assertFalse(WhatsAppRecipients.isPrivateJid("120363123456789012@g.us"));
        String url = WhatsAppRecipients.waMeUrl("972501234567", "אני בדרך");
        assertTrue(url.startsWith("https://wa.me/972501234567?text="));
        assertTrue(url.contains("%20"));
        assertFalse(url.contains("@g.us"));
    }

    @Test public void listRoundTripSearchAliasAndDelete() {
        List<WhatsAppRecipients.Person> saved = WhatsAppRecipients.upsert(null, "תמר כהן", "תמרי", "050-123-4567");
        assertNotNull(saved);
        assertEquals(1, saved.size());
        assertEquals("972501234567", saved.get(0).e164);
        assertEquals("תמר", WhatsAppRecipients.suggestAlias("תמר כהן"));
        List<WhatsAppRecipients.Person> again = WhatsAppRecipients.upsert(saved, "תמר כהן", "", "0501234567");
        assertEquals("תמרי", again.get(0).alias);
        assertEquals(saved.get(0).id, again.get(0).id);
        assertNull(WhatsAppRecipients.upsert(saved, "זר", "זר", "123"));
        List<WhatsAppRecipients.Person> renamed = WhatsAppRecipients.withAlias(again, again.get(0).id, "  תמרי ");
        assertEquals("תמרי", renamed.get(0).alias);
        assertEquals(1, WhatsAppRecipients.search(renamed, "תמר").size());
        assertEquals(1, WhatsAppRecipients.search(renamed, "050").size());
        assertTrue(WhatsAppRecipients.search(renamed, "אין כזה").isEmpty());
        assertTrue(WhatsAppRecipients.without(renamed, renamed.get(0).id).isEmpty());
        String json = WhatsAppRecipients.toJson(renamed);
        assertFalse(json.contains("g.us"));
        assertFalse(json.contains("@"));
        List<WhatsAppRecipients.Person> back = WhatsAppRecipients.parse(json);
        assertEquals("תמרי", back.get(0).alias);
        assertEquals("972501234567", back.get(0).e164);
        assertTrue(WhatsAppRecipients.parse(null).isEmpty());
        assertTrue(WhatsAppRecipients.parse("not-json").isEmpty());
        assertTrue(WhatsAppRecipients.parse("[{\"id\":\"g\",\"name\":\"משפחה\",\"alias\":\"משפחה\",\"jid\":\"1203@g.us\"}]").isEmpty());
    }

    @Test public void openCountdownIsEightSeconds() {
        assertEquals(8, WhatsAppRecipients.OPEN_SECONDS);
        assertEquals(8000L, WhatsAppRecipients.OPEN_MS);
        assertEquals("+972501234567", TAMRI.get(0).prettyPhone());
        assertTrue(TAMRI.get(0).detail().contains("תמר כהן"));
        assertTrue(TAMRI.get(0).detail().contains("+972501234567"));
    }
}
