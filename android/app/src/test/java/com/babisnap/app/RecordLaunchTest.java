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

    @Test public void sideKeyLaunchIntentIsTheRecorderAndSettingsStaysSecond() throws Exception {
        String manifest = text("src/main/AndroidManifest.xml");
        java.util.List<String> launchers = launcherNames(manifest);
        assertEquals(java.util.Arrays.asList(".RecordEntryActivity", ".MainActivity"), launchers);
        assertFalse(manifest.contains("activity-alias"));
        int record = manifest.indexOf("android:name=\".RecordEntryActivity\"");
        int main = manifest.indexOf("android:name=\".MainActivity\"");
        assertTrue(record > 0 && record < main);
        String recordTag = manifest.substring(record, manifest.indexOf("</activity>", record));
        assertTrue(recordTag.contains("android:label=\"@string/record_entry_label\""));
        assertTrue(recordTag.contains("android:icon=\"@drawable/record_tile\""));
        assertTrue(recordTag.contains("android.intent.action.ASSIST"));
        assertTrue(recordTag.contains("android.intent.action.VOICE_COMMAND"));
        assertTrue(recordTag.contains("android.intent.category.LAUNCHER"));
        String mainTag = manifest.substring(main, manifest.indexOf("</activity>", main));
        assertTrue(mainTag.contains("android:label=\"@string/app_name\""));
        assertTrue(mainTag.contains("android:icon=\"@mipmap/ic_launcher\""));
        assertTrue(mainTag.contains("android.intent.category.LAUNCHER"));
        assertFalse(mainTag.contains("record_entry_label"));
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

    /** Names of activities and aliases whose own tag contains a LAUNCHER category, in manifest order. */
    private static java.util.List<String> launcherNames(String manifest) {
        java.util.List<String> names = new java.util.ArrayList<>();
        int from = 0;
        while (from < manifest.length()) {
            int activity = manifest.indexOf("<activity ", from);
            int alias = manifest.indexOf("<activity-alias ", from);
            int start = activity < 0 ? alias : alias < 0 ? activity : Math.min(activity, alias);
            if (start < 0) break;
            boolean isAlias = start == alias;
            int openEnd = manifest.indexOf('>', start);
            boolean selfClosing = openEnd > start && manifest.charAt(openEnd - 1) == '/';
            int end = selfClosing
                    ? openEnd
                    : manifest.indexOf(isAlias ? "</activity-alias>" : "</activity>", openEnd);
            if (end < 0) break;
            String tag = manifest.substring(start, end);
            if (tag.contains("android.intent.category.LAUNCHER")) {
                int nameAt = tag.indexOf("android:name=\"");
                int nameEnd = tag.indexOf('"', nameAt + 14);
                names.add(tag.substring(nameAt + 14, nameEnd));
            }
            from = end + 1;
        }
        return names;
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
