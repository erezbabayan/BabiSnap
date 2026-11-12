package com.babisnap.app;

import static org.junit.Assert.*;

import android.content.Intent;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RecordLaunchTest {
    @Test public void recordingScreenIsNeverTheSettingsScreen() {
        assertEquals(VoiceActivity.class.getName(), RecordLaunch.recordingClass());
        assertTrue(RecordLaunch.DEFAULT_AUTO);
        assertEquals("no_auto_record", RecordLaunch.EXTRA_NO_AUTO_RECORD);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK, RecordLaunch.FLAGS);
        assertTrue(RecordLaunch.HINT.contains("BabiSnap הקלטה"));
        assertTrue(RecordLaunch.HINT.contains("פתיחת BabiSnap מתחילה הקלטה מיד"));
        assertTrue(RecordLaunch.HINT.contains("לחיצה כפולה"));
        assertTrue(RecordLaunch.HINT.contains("עוזר דיגיטלי"));
        assertTrue(RecordLaunch.HINT.contains("מיקרופון"));
        assertTrue(RecordLaunch.HINT.contains("אין הקלטה נוספת"));
    }

    @Test public void launcherOpensRecordingUnlessRecentsGearOrTheToggleIsOff() {
        assertTrue(RecordLaunch.shouldRecordOnLaunch(true, Intent.ACTION_MAIN, true, false, false, false));
        assertFalse(RecordLaunch.shouldRecordOnLaunch(false, Intent.ACTION_MAIN, true, false, false, false));
        assertFalse(RecordLaunch.shouldRecordOnLaunch(true, Intent.ACTION_MAIN, true, true, false, false));
        assertFalse(RecordLaunch.shouldRecordOnLaunch(true, Intent.ACTION_MAIN, true, false, true, false));
        assertFalse(RecordLaunch.shouldRecordOnLaunch(true, Intent.ACTION_MAIN, true, false, false, true));
        assertFalse(RecordLaunch.shouldRecordOnLaunch(true, Intent.ACTION_MAIN, false, false, false, false));
        assertFalse(RecordLaunch.shouldRecordOnLaunch(true, Intent.ACTION_VIEW, true, false, false, false));
        assertFalse(RecordLaunch.shouldRecordOnLaunch(true, null, false, false, false, false));
    }

    @Test public void sideKeyWhileTheAppIsAlreadyRunningStillRecords() {
        // SystemUI double press: MAIN/LAUNCHER and NEW_TASK, while singleTask delivers onNewIntent.
        // NEW_TASK itself is not a reason to stay on the main screen.
        assertTrue(RecordLaunch.shouldRecordOnNewIntent(true, Intent.ACTION_MAIN, true, false, false));
        assertFalse(RecordLaunch.shouldRecordOnNewIntent(true, Intent.ACTION_MAIN, true, true, false));
        assertFalse(RecordLaunch.shouldRecordOnNewIntent(true, Intent.ACTION_MAIN, true, false, true));
        assertFalse(RecordLaunch.shouldRecordOnNewIntent(false, Intent.ACTION_MAIN, true, false, false));
        assertFalse(RecordLaunch.shouldRecordOnNewIntent(true, null, false, false, false));
    }

    @Test public void sideButtonAssistantAndWidgetAreWired() throws Exception {
        String manifest = text("src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android.intent.action.ASSIST"));
        assertTrue(manifest.contains("android.intent.action.VOICE_COMMAND"));
        assertTrue(manifest.contains("android.service.voice.VoiceInteractionService"));
        assertTrue(manifest.contains("android:launchMode=\"singleTask\""));
        assertTrue(manifest.contains(".RecordEntryActivity"));
        String layout = text("src/main/res/layout/widget_button.xml");
        assertTrue(layout.contains("70dp"));
        assertFalse(layout.contains("65dp"));
        assertTrue(layout.contains("12sp"));
        assertTrue(layout.contains("#FFFFFF"));
        String listen = text("src/main/res/layout/activity_listen.xml");
        assertTrue(listen.contains("@+id/settings"));
        assertTrue(listen.contains("הגדרות"));
        String session = text("src/main/java/com/babisnap/app/BabiSnapSession.java");
        assertTrue(session.contains("RecordLaunch.recording"));
        assertFalse(session.contains("MainActivity"));
        assertFalse(session.contains("AssistStructure"));
        String service = text("src/main/java/com/babisnap/app/BabiSnapVoiceService.java");
        assertTrue(service.contains("onLaunchVoiceAssistFromKeyguard"));
        assertTrue(service.contains("RecordLaunch.recording"));
        String entry = text("src/main/java/com/babisnap/app/RecordEntryActivity.java");
        assertTrue(entry.contains("onNewIntent"));
        assertTrue(entry.contains("RecordLaunch.recording"));
        String settings = text("src/main/java/com/babisnap/app/MainActivity.java");
        assertTrue(settings.contains("handoffToRecording"));
        assertTrue(settings.contains("onNewIntent"));
        assertFalse(settings.contains("גודל לחצן ההקלטה"));
        assertTrue(settings.contains("פתיחת BabiSnap מתחילה הקלטה מיד"));
        assertTrue(settings.contains("WidgetSize.forgetOldPref"));
        assertFalse(settings.contains("ButtonWidget.refresh"));
        String voice = text("src/main/java/com/babisnap/app/VoiceActivity.java");
        assertTrue(voice.contains("RecordLaunch.settings"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
