package com.babisnap.app;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WhatsAppGroupShareTest {
    @Test public void groupPhrasesDoNotEnterWhatsAppWhileDeferred() {
        assertFalse(WhatsAppGroupShare.GROUPS_ENABLED);
        String phrase = "הודעה לקבוצת משפחה בוואטסאפ אני בדרך";
        assertEquals(WhatsAppRecipients.Target.GROUP, WhatsAppRecipients.parse(phrase).target);
        assertFalse(WhatsAppGroupShare.entersWhatsApp(WhatsAppRecipients.Target.GROUP));
        assertTrue(WhatsAppGroupShare.entersWhatsApp(WhatsAppRecipients.Target.PERSON));
        assertTrue(WhatsAppGroupShare.entersWhatsApp(WhatsAppRecipients.Target.NONE));
        assertEquals(Classifier.Kind.WHATSAPP, Classifier.classify("וואטסאפ לתמרי מה קורה", false, true).kind);
        assertTrue(WhatsAppGroupShare.entersWhatsApp(WhatsAppRecipients.parse("וואטסאפ לתמרי מה קורה").target));
        assertNotEquals(Classifier.Kind.WHATSAPP, Classifier.classify(phrase, false, false).kind);
    }

    @Test public void groupIntentIsWhatsAppsOwnPicker() {
        WhatsAppGroupShare.Spec consumer = WhatsAppGroupShare.spec(true, true, "מה קורה");
        assertEquals(WhatsAppGroupShare.ACTION, consumer.action);
        assertEquals(WhatsAppGroupShare.TYPE, consumer.type);
        assertEquals(WhatsAppLogic.PKG, consumer.pkg);
        assertEquals("מה קורה", consumer.text);
        assertFalse(consumer.includeJid);
        assertFalse(consumer.systemChooser);
        assertTrue(consumer.opensWhatsAppPicker());

        WhatsAppGroupShare.Spec business = WhatsAppGroupShare.spec(false, true, "אני בדרך");
        assertEquals(WhatsAppLogic.PKG_BUSINESS, business.pkg);
        assertTrue(business.opensWhatsAppPicker());

        WhatsAppGroupShare.Spec none = WhatsAppGroupShare.spec(false, false, "היי");
        assertNull(none.pkg);
        assertFalse(none.opensWhatsAppPicker());
        assertFalse(none.systemChooser);
        assertFalse(none.includeJid);
    }

    @Test public void groupPhraseKeepsTheNameForTheCardAndTheSearchPaste() {
        WhatsAppRecipients.Command c = WhatsAppRecipients.parse("הודעה לקבוצת משפחה בוואטסאפ אני בדרך");
        assertEquals(WhatsAppRecipients.Target.GROUP, c.target);
        assertEquals("משפחה", c.name);
        assertEquals("אני בדרך", c.body);
        assertEquals("וואטסאפ · קבוצה משפחה", WhatsAppRecipients.groupCardHeadline(c.name));
        WhatsAppGroupShare.Spec spec = WhatsAppGroupShare.spec(true, false, c.body);
        assertEquals("אני בדרך", spec.text);
        assertTrue(spec.opensWhatsAppPicker());
        assertFalse(spec.text.contains("משפחה"));
    }

    @Test public void launcherDoesNotBuildASystemChooserOrAttachAJid() throws Exception {
        String share = text("src/main/java/com/babisnap/app/WhatsAppShare.java");
        int method = share.indexOf("static Intent groupPicker");
        int next = share.indexOf("static Intent textToJid");
        assertTrue(method > 0 && next > method);
        String body = share.substring(method, next);
        assertFalse(body.contains("createChooser"));
        assertFalse(body.contains("jid"));
        assertFalse(body.contains("setPackage(null)"));
        assertTrue(body.contains("spec.action"));
        assertTrue(body.contains("spec.type"));
        assertTrue(body.contains("EXTRA_TEXT"));
        assertTrue(body.contains("setPackage(spec.pkg)"));
        String voice = text("src/main/java/com/babisnap/app/VoiceActivity.java");
        assertFalse(voice.contains("createChooser"));
        assertFalse(voice.contains("text(null"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
