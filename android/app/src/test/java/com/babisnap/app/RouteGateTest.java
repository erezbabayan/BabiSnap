package com.babisnap.app;

import static com.babisnap.app.Classifier.Kind.*;
import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Kind.WHATSAPP stays off the calendar queue and off BabiTk. The off switch keeps the old routes. */
public class RouteGateTest {
    @Test public void whatsAppNeverReachesEitherQueue() {
        String[] phrases = {
            "הודעה לתמרי בוואטסאפ אני בדרך",
            "וואטסאפ לתמרי: אני בדרך",
            "וואטסאפ אל תמרי אני מאחר",
            "יומן וואטסאפ מחר בעשר רופא",
            "וואטסאפ פגישה עם דני מחר בעשר",
            "בוואטסאפ לקנות חלב"
        };
        for (String phrase : phrases) {
            for (boolean time : new boolean[]{false, true}) {
                Classifier.Result on = Classifier.classify(phrase, time, true);
                assertEquals(phrase, WHATSAPP, on.kind);
                assertEquals(phrase, RouteGate.Dest.WHATSAPP, RouteGate.dest(on.kind));
                assertFalse(phrase, RouteGate.toCalQueue(on.kind));
                assertFalse(phrase, RouteGate.toBabiTk(on.kind));
                assertTrue(phrase, RouteGate.toWhatsApp(on.kind));
                Classifier.Result off = Classifier.classify(phrase, time, false);
                Classifier.Result live = Classifier.classify(phrase, time);
                assertNotEquals(phrase, WHATSAPP, off.kind);
                assertEquals(phrase, live.kind, off.kind);
                assertNotEquals(phrase, RouteGate.Dest.WHATSAPP, RouteGate.dest(off.kind));
            }
        }
    }

    @Test public void otherRoutesStayPutWhenTheSwitchIsOn() {
        Classifier.Result meeting = Classifier.classify("יומן מחר בעשר רופא שיניים", true, true);
        assertEquals(MEETING, meeting.kind);
        assertTrue(RouteGate.toCalQueue(meeting.kind));
        assertFalse(RouteGate.toBabiTk(meeting.kind));
        assertFalse(RouteGate.toWhatsApp(meeting.kind));

        Classifier.Result task = Classifier.classify("לקנות חלב", false, true);
        assertEquals(TASK, task.kind);
        assertTrue(RouteGate.toBabiTk(task.kind));
        assertFalse(RouteGate.toCalQueue(task.kind));

        Classifier.Result note = Classifier.classify("הערה הקוד של השער 1234", false, true);
        assertEquals(NOTE, note.kind);
        assertTrue(RouteGate.toBabiTk(note.kind));
        assertFalse(RouteGate.toWhatsApp(note.kind));
    }

    @Test public void babitkRefusesAWhatsAppPayload() {
        try {
            BabiTkJson.build("היי", null, null, null, null, "whatsapp", "bsnap-1");
            fail("whatsapp must not become a BabiTk task");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("WHATSAPP"));
        }
        assertEquals("task", field(BabiTkJson.build("t", null, null, null, null, "weird", "x"), "type"));
        assertTrue(BabiTkJson.isWhatsAppPayload("{\"type\":\"whatsapp\"}"));
        assertTrue(BabiTkJson.isWhatsAppPayload("{\"type\": \"whatsapp\"}"));
        assertFalse(BabiTkJson.isWhatsAppPayload("{\"type\":\"task\"}"));
        assertFalse(BabiTkJson.isWhatsAppPayload(null));
        assertFalse(WhatsAppLogic.DEFAULT_ENABLED);
        try {
            BabiTkJson.build("תמרי", null, null, null, null, "call", "bsnap-2");
            fail("call must not become a BabiTk task");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("CALL"));
        }
        assertTrue(BabiTkJson.isCallPayload("{\"type\":\"call\"}"));
        assertFalse(BabiTkJson.isCallPayload("{\"type\":\"task\"}"));
        assertFalse(BabiTkJson.isCallPayload("{\"type\":\"whatsapp\"}"));
        assertFalse(RouteGate.toCalQueue(CALL));
        assertFalse(RouteGate.toBabiTk(CALL));
        assertTrue(RouteGate.toCall(CALL));
        assertEquals(RouteGate.Dest.CALL, RouteGate.dest(CALL));
    }

    @Test public void voiceActivityReturnsBeforeTheQueues() throws Exception {
        String src = text("src/main/java/com/babisnap/app/VoiceActivity.java");
        int gate = src.indexOf("RouteGate.Dest.WHATSAPP");
        int call = src.indexOf("RouteGate.Dest.CALL");
        int cal = src.indexOf("CalQueue.enqueue");
        int babi = src.indexOf("BabiTk.enqueue");
        assertTrue(gate > 0 && call > gate && cal > call && babi > call);
        assertTrue(src.contains("if (!RouteGate.toBabiTk(c.kind)) return;"));
        assertTrue(src.contains("Intent.ACTION_DIAL"));
        assertFalse(src.contains("ACTION_CALL"));
        assertFalse(src.contains("CALL_PHONE"));
        assertTrue(src.contains("CallDial.CONFIRM_MS"));
        assertTrue(src.contains("BabiTkJson.isCallPayload(pendingBody)"));
        assertTrue(src.contains("BabiTkJson.isWhatsAppPayload(pendingBody)"));
        assertFalse(src.contains("AccessibilityService"));
        assertFalse(src.contains("NotificationListener"));
        assertTrue(src.contains("WhatsAppContacts.load"));
        assertTrue(src.contains("R.layout.card_saved"));
        assertTrue(src.contains("UNDO_MS"));
        assertFalse(src.contains("card_wa_open"));
        assertFalse(src.contains("card_wa_choose"));
        assertFalse(src.contains("WhatsAppDirectory"));
        assertFalse(java.nio.file.Files.exists(Path.of("src/main/java/com/babisnap/app/WhatsAppRecipientsActivity.java")));
        String contacts = text("src/main/java/com/babisnap/app/WhatsAppContacts.java");
        assertFalse(contacts.contains("AccessibilityService"));
        assertFalse(contacts.contains("NotificationListener"));
    }

    @Test public void manifestAndTestBuildAddNoSensitivePermission() throws Exception {
        String manifest = text("src/main/AndroidManifest.xml");
        assertFalse(manifest.contains("SYSTEM_ALERT_WINDOW"));
        assertFalse(manifest.contains("BIND_ACCESSIBILITY_SERVICE"));
        assertFalse(manifest.contains("BIND_NOTIFICATION_LISTENER_SERVICE"));
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"));
        assertFalse(manifest.contains("WhatsAppRecipientsActivity"));
        assertFalse(manifest.contains("android.intent.action.PICK"));
        assertFalse(manifest.contains("CALL_PHONE"));
        assertFalse(manifest.contains("READ_PHONE_STATE"));
        assertFalse(manifest.contains("READ_CALL_LOG"));
        assertTrue(manifest.contains("android.intent.action.DIAL"));
        assertTrue(manifest.contains("wa.me"));
        int contactsPerm = 0;
        int from = 0;
        while (manifest.indexOf("READ_CONTACTS", from) >= 0) {
            contactsPerm++;
            from = manifest.indexOf("READ_CONTACTS", from) + 1;
        }
        assertEquals(1, contactsPerm);
        assertTrue(manifest.contains("${pairScheme}"));
        assertFalse(manifest.contains("android:scheme=\"babisnap\""));
        String gradle = text("build.gradle");
        assertTrue(gradle.contains("WHATSAPP_DEFAULT_ON\", \"false\""));
        assertTrue(gradle.contains("applicationIdSuffix \".watest\""));
        assertTrue(gradle.contains("WHATSAPP_DEFAULT_ON\", \"true\""));
        int watest = gradle.indexOf("watest {");
        int on = gradle.indexOf("WHATSAPP_DEFAULT_ON\", \"true\"");
        assertTrue(watest > 0 && on > watest);
        String label = text("src/watest/res/values/strings.xml");
        assertTrue(label.contains("BabiSnap וואטסאפ בדיקה"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private static String field(String json, String k) {
        int i = json.indexOf("\"" + k + "\":\"");
        if (i < 0) return null;
        int start = i + k.length() + 4;
        return json.substring(start, json.indexOf('"', start));
    }
}
