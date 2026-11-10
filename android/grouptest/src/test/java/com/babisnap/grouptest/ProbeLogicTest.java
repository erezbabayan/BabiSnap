package com.babisnap.grouptest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProbeLogicTest {
    @Test
    public void findsUrlEncodedGroupJid() {
        ProbeLogic.Found found = ProbeLogic.findJid(Collections.singleton(
                "intent:#Intent;S.jid=120363123456789012%40g.us;end"));
        assertEquals("120363123456789012@g.us", found.jid);
        assertTrue(found.group);
    }

    @Test
    public void findsLegacyDashGroupJid() {
        ProbeLogic.Found found = ProbeLogic.findJid(Collections.singleton(
                "jid=972501234567-1620000000%40g.us"));
        assertEquals("972501234567-1620000000@g.us", found.jid);
        assertTrue(found.group);
    }

    @Test
    public void prefersGroupOverPhoneJid() {
        ProbeLogic.Found found = ProbeLogic.findJid(Arrays.asList(
                "972501234567@s.whatsapp.net",
                "120363123456789012@g.us"));
        assertTrue(found.group);
        assertTrue(found.jid.endsWith("@g.us"));
    }

    @Test
    public void phoneJidIsNotAGroup() {
        ProbeLogic.Found found = ProbeLogic.findJid(Collections.singleton("972501234567@s.whatsapp.net"));
        assertFalse(found.group);
        assertFalse(ProbeLogic.isGroupJid(found.jid));
    }

    @Test
    public void bareNumberIsNotAJid() {
        assertNull(ProbeLogic.findJid(Collections.singleton("972501234567")));
        assertNull(ProbeLogic.findJid(Collections.singleton("בדיקה מ־BabiSnap")));
        assertNull(ProbeLogic.findJid(null));
    }

    @Test
    public void doubleEncodedJid() {
        ProbeLogic.Found found = ProbeLogic.findJid(Collections.singleton("120363123456789012%2540g.us"));
        assertEquals("120363123456789012@g.us", found.jid);
    }

    @Test
    public void titleMatchCollapsesWhitespaceOnly() {
        assertTrue(ProbeLogic.titlesMatch("  משפחה \n ", "משפחה"));
        assertTrue(ProbeLogic.titlesMatch("אמא", "אמא"));
        assertFalse(ProbeLogic.titlesMatch("משפחה", "המשפחה"));
        assertFalse(ProbeLogic.titlesMatch("אמא", "אבא"));
        assertFalse(ProbeLogic.titlesMatch("", "אמא"));
        assertFalse(ProbeLogic.titlesMatch(null, null));
        assertEquals("משפחה 💚", ProbeLogic.normalizeTitle("  משפחה   💚 "));
    }

    @Test
    public void israeliNumbers() {
        assertEquals("972501234567", ProbeLogic.toIlDigits("050-123-4567"));
        assertEquals("972501234567", ProbeLogic.toIlDigits("+972 50 123 4567"));
        assertEquals("972501234567", ProbeLogic.toIlDigits("(050) 1234567"));
        assertEquals("972501234567", ProbeLogic.toIlDigits("501234567"));
        assertEquals("97231234567", ProbeLogic.toIlDigits("03-1234567"));
        assertNull(ProbeLogic.toIlDigits("123"));
        assertNull(ProbeLogic.toIlDigits(null));
        assertEquals("972501234567@s.whatsapp.net", ProbeLogic.contactJid("972501234567"));
        assertNull(ProbeLogic.contactJid("123"));
    }

    @Test
    public void headlineShowsAliasAndFullCountdown() {
        assertEquals("שולח לאמא בעוד 8 שניות", ProbeLogic.cardHeadline("contact", "שרה", "אמא", 8));
        assertEquals("שולח לקבוצה: משפחה · בעוד 8 שניות", ProbeLogic.cardHeadline("group", "משפחה", "", 8));
        assertEquals("שולח לאמא עכשיו", ProbeLogic.cardHeadline("contact", "שרה", "  אמא ", 0));
        assertEquals("שולח לקבוצה: קבוצה · בעוד 1 שנייה", ProbeLogic.cardHeadline("group", "  ", null, 1));
    }

    @Test
    public void sendControlRejectsVoiceAndRequiresASendLabelToConfirm() {
        assertTrue(ProbeLogic.isSendControl("com.whatsapp:id/send", "שלח", true));
        assertTrue(ProbeLogic.descConfirmsSend("Send"));
        assertTrue(ProbeLogic.descConfirmsSend("שלח הודעה"));
        assertFalse(ProbeLogic.descConfirmsSend("Voice message"));
        assertFalse(ProbeLogic.isSendControl("com.whatsapp:id/voice_note_btn", "הקלט הודעה קולית", true));
        assertTrue(ProbeLogic.isSendControl("com.whatsapp:id/send", null, false));
        assertFalse(ProbeLogic.descConfirmsSend(null));
        assertEquals("SEND_HE", ProbeLogic.descCode("שלח"));
        assertEquals("SEND_EN", ProbeLogic.descCode("Send message"));
        assertEquals("VOICE", ProbeLogic.descCode("record"));
        assertEquals("OTHER", ProbeLogic.descCode("קבוצה"));
        assertTrue(ProbeLogic.idIsTitle("com.whatsapp:id/conversation_contact_name"));
        assertFalse(ProbeLogic.idIsTitle("com.whatsapp:id/entry"));
    }

    @Test
    public void reportDropsNamesJidsAndMessageText() {
        ProbeLogic.Report report = new ProbeLogic.Report();
        report.androidSdk = 34;
        report.waVersion = "2.24.20.78";
        report.pkg = "WA";
        report.shortcut = "אמא";
        report.shortcutRoute = "RESOLVED";
        report.shortcutActivity = "120363123456789012@g.us";
        report.notifOn = true;
        report.groups = 2;
        report.directReply = true;
        report.contactsPerm = true;
        report.selected = 3;
        report.sendKind = "GROUP";
        report.sendPath = "ACTION_JID";
        report.sendMark = "OK";
        report.flow = "OPENED";
        report.a11yOn = false;
        report.autoPress = false;
        report.titleMatch = "בדיקה מ־BabiSnap";
        report.sendButton = "YES";
        report.sendLabel = "SEND_HE";
        report.autoResult = "NONE";
        report.variant = "אמא";
        String text = report.format();
        assertTrue(text.contains("VARIANT): FULL"));
        assertTrue(text.contains("SHORTCUT): NOT_TRIED"));
        assertTrue(text.contains("TITLE_MATCH): UNKNOWN"));
        assertTrue(text.contains("SHORTCUT_ACTIVITY): NONE"));
        assertTrue(text.contains("WA_VERSION): 2.24.20.78"));
        assertTrue(text.contains("GROUPS): 2"));
        assertFalse(text.contains("אמא"));
        assertFalse(text.contains("@"));
        assertFalse(text.contains("בדיקה"));
        assertFalse(text.contains("g.us"));
    }

    @Test
    public void reportKeepsSafeActivityName() {
        assertEquals("com.whatsapp.contact.picker.ContactPicker",
                ProbeLogic.activityCode("com.whatsapp.contact.picker.ContactPicker"));
        assertEquals("NONE", ProbeLogic.activityCode("com.whatsapp.contact.picker.ContactPicker " + "0501234567"));
        assertEquals("UNKNOWN", ProbeLogic.versionCode("beta"));
        assertEquals("BOTH", ProbeLogic.pkgCode(true, true));
        assertEquals("NONE", ProbeLogic.pkgCode(false, false));
    }

    @Test
    public void reasonsDoNotIncludeTheSavedName() {
        String reason = ProbeLogic.autoReason("TITLE_MISMATCH", "group");
        assertEquals("שם הקבוצה השתנה – אמת מחדש", reason);
        assertFalse(reason.contains("@"));
        assertEquals("הכותרת לא תואמת את השם השמור – לא נשלח", ProbeLogic.autoReason("TITLE_MISMATCH", "contact"));
        assertEquals("נמצא כפתור בלי הכיתוב «שלח» – לא נלחץ", ProbeLogic.autoReason("SEND_UNCONFIRMED", "group"));
    }
}
