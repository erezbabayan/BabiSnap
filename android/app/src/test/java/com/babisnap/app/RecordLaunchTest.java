package com.babisnap.app;

import static org.junit.Assert.*;

import android.content.Intent;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RecordLaunchTest {
    @Test public void mainIconDoesNotRecordAndTheShortcutDoes() {
        assertEquals(VoiceActivity.class.getName(), RecordLaunch.recordingClass());
        assertEquals("launch_records", RecordLaunch.OLD_PREF);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK, RecordLaunch.FLAGS);
        assertTrue(RecordLaunch.HINT.contains("«BabiSnap הקלטה»"));
        assertTrue(RecordLaunch.HINT.contains("האייקון «BabiSnap» פותח את ההגדרות"));
        assertFalse(RecordLaunch.HINT.contains("פתיחת BabiSnap מתחילה הקלטה מיד"));
        assertTrue(RecordLaunch.HINT.contains("עוזר דיגיטלי"));
        assertTrue(RecordLaunch.HINT.contains("אין הקלטה נוספת"));
    }

    @Test public void launcherEntryIsLabeledAndTheMainActivityStaysSettings() throws Exception {
        String manifest = text("src/main/AndroidManifest.xml");
        int main = manifest.indexOf("android:name=\".MainActivity\"");
        int record = manifest.indexOf("android:name=\".RecordEntryActivity\"");
        int alias = manifest.indexOf("android:name=\".RecordLauncher\"");
        assertTrue(main > 0 && record > main && alias > record);
        String mainTag = manifest.substring(main, manifest.indexOf("</activity>", main));
        assertTrue(mainTag.contains("android:label=\"@string/app_name\""));
        assertTrue(mainTag.contains("android.intent.category.LAUNCHER"));
        assertFalse(mainTag.contains("record_entry_label"));
        assertFalse(mainTag.contains("launchMode=\"singleTask\""));
        String aliasTag = manifest.substring(alias, manifest.indexOf("</activity-alias>"));
        assertTrue(aliasTag.contains("android:label=\"@string/record_entry_label\""));
        assertTrue(aliasTag.contains("android:icon=\"@drawable/record_tile\""));
        assertTrue(aliasTag.contains("android:targetActivity=\".RecordEntryActivity\""));
        assertTrue(aliasTag.contains("android.intent.category.LAUNCHER"));
        assertTrue(manifest.contains("android.intent.action.ASSIST"));
        assertTrue(manifest.contains("android.intent.action.VOICE_COMMAND"));
        String strings = text("src/main/res/values/strings.xml");
        assertTrue(strings.contains(">BabiSnap הקלטה<"));
        String settings = text("src/main/java/com/babisnap/app/MainActivity.java");
        assertFalse(settings.contains("handoffToRecording"));
        assertFalse(settings.contains("shouldRecordOnLaunch"));
        assertFalse(settings.contains("פתיחת BabiSnap מתחילה הקלטה מיד"));
        assertTrue(settings.contains("RecordLaunch.forgetOldPref"));
        String entry = text("src/main/java/com/babisnap/app/RecordEntryActivity.java");
        assertTrue(entry.contains("onNewIntent"));
        assertTrue(entry.contains("RecordLaunch.recording"));
        assertFalse(entry.contains("MainActivity"));
        String session = text("src/main/java/com/babisnap/app/BabiSnapSession.java");
        assertTrue(session.contains("RecordLaunch.recording"));
        assertFalse(session.contains("MainActivity"));
        String voice = text("src/main/java/com/babisnap/app/VoiceActivity.java");
        assertTrue(voice.contains("RecordLaunch.settings"));
        String listen = text("src/main/res/layout/activity_listen.xml");
        assertTrue(listen.contains("@+id/settings"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
