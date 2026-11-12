package com.babisnap.app;

import static org.junit.Assert.*;

import android.content.Intent;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RecordLaunchTest {
    @Test public void micGrantedStartsRecordingOtherwiseSettings() {
        assertEquals(VoiceActivity.class.getName(), RecordLaunch.target(true));
        assertEquals(MainActivity.class.getName(), RecordLaunch.target(false));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK, RecordLaunch.FLAGS);
        assertEquals("ask_mic", RecordLaunch.EXTRA_ASK_MIC);
        assertTrue(RecordLaunch.HINT.contains("BabiSnap הקלטה"));
        assertTrue(RecordLaunch.HINT.contains("לחיצה כפולה"));
        assertTrue(RecordLaunch.HINT.contains("עוזר דיגיטלי"));
        assertTrue(RecordLaunch.HINT.contains("מיקרופון"));
        assertTrue(RecordLaunch.HINT.contains("אין הקלטה נוספת"));
    }

    @Test public void sideButtonAndWidgetAreWired() throws Exception {
        String manifest = text("src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android.intent.action.ASSIST"));
        assertTrue(manifest.contains("android.intent.action.VOICE_COMMAND"));
        assertTrue(manifest.contains("android.service.voice.VoiceInteractionService"));
        assertTrue(manifest.contains("android:permission=\"android.permission.BIND_VOICE_INTERACTION\""));
        assertFalse(manifest.contains("uses-permission android:name=\"android.permission.BIND_VOICE_INTERACTION\""));
        assertTrue(manifest.contains(".RecordEntryActivity"));
        String strings = text("src/main/res/values/strings.xml");
        assertTrue(strings.contains("BabiSnap הקלטה"));
        String layout = text("src/main/res/layout/widget_button.xml");
        assertTrue(layout.contains("56dp"));
        assertFalse(layout.contains("76dp"));
        assertTrue(layout.contains("12sp"));
        assertTrue(layout.contains("#FFFFFF"));
        assertTrue(layout.contains("@string/app_name"));
        assertTrue(layout.contains("android:maxLines=\"1\""));
        String voice = text("src/main/res/xml/voice_interaction_service.xml");
        assertTrue(voice.contains("supportsAssist=\"true\""));
        assertTrue(voice.contains("BabiSnapSessionService"));
        assertTrue(voice.contains("BabiSnapRecognition"));
        String session = text("src/main/java/com/babisnap/app/BabiSnapSession.java");
        assertTrue(session.contains("RecordLaunch.intent"));
        assertFalse(session.contains("AssistStructure"));
        String recognition = text("src/main/java/com/babisnap/app/BabiSnapRecognition.java");
        assertTrue(recognition.contains("ERROR_CLIENT"));
        assertFalse(recognition.contains("MediaRecorder"));
        String widget = text("src/main/java/com/babisnap/app/MicWidget.java");
        assertTrue(widget.contains("VoiceActivity.class"));
        String settings = text("src/main/java/com/babisnap/app/MainActivity.java");
        assertTrue(settings.contains("RecordLaunch.HINT"));
        assertTrue(settings.contains("RecordLaunch.EXTRA_ASK_MIC"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
