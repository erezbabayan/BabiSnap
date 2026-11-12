package com.babisnap.app;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WhatsAppLogicTest {
    @Test public void defaultsAreOffAndBoth() {
        assertFalse(WhatsAppLogic.DEFAULT_ENABLED);
        assertEquals(WhatsAppLogic.Mode.BOTH, WhatsAppLogic.mode(null));
        assertEquals(WhatsAppLogic.Mode.BOTH, WhatsAppLogic.mode(""));
        assertEquals(WhatsAppLogic.Mode.BOTH, WhatsAppLogic.mode("nope"));
        assertEquals(WhatsAppLogic.Mode.BOTH, WhatsAppLogic.mode(WhatsAppLogic.DEFAULT_MODE));
        assertEquals(WhatsAppLogic.Mode.VOICE, WhatsAppLogic.mode("voice"));
        assertEquals(WhatsAppLogic.Mode.TRANSCRIPT, WhatsAppLogic.mode("transcript"));
    }

    @Test public void bothShowsTwoButtonsAndSendsOnlyWhatExists() {
        WhatsAppLogic.Buttons both = WhatsAppLogic.buttons(WhatsAppLogic.Mode.BOTH, true, true);
        assertTrue(both.voice && both.transcript);
        WhatsAppLogic.Buttons voiceOnly = WhatsAppLogic.buttons(WhatsAppLogic.Mode.VOICE, true, true);
        assertTrue(voiceOnly.voice);
        assertFalse(voiceOnly.transcript);
        WhatsAppLogic.Buttons textOnly = WhatsAppLogic.buttons(WhatsAppLogic.Mode.TRANSCRIPT, true, true);
        assertFalse(textOnly.voice);
        assertTrue(textOnly.transcript);
        // capture failed on voice-only: offer the transcript instead of a dead end
        WhatsAppLogic.Buttons fallback = WhatsAppLogic.buttons(WhatsAppLogic.Mode.VOICE, false, true);
        assertFalse(fallback.voice);
        assertTrue(fallback.transcript);
        WhatsAppLogic.Buttons neither = WhatsAppLogic.buttons(WhatsAppLogic.Mode.BOTH, false, false);
        assertFalse(neither.voice || neither.transcript);
    }

    @Test public void timerFollowsTranscriptLength() {
        assertEquals(8, WhatsAppLogic.confirmSeconds(null));
        assertEquals(8, WhatsAppLogic.confirmSeconds(""));
        assertEquals(8, WhatsAppLogic.confirmSeconds("א".repeat(24)));
        assertEquals(15, WhatsAppLogic.confirmSeconds("א".repeat(25)));
        assertEquals(15, WhatsAppLogic.confirmSeconds("א".repeat(80)));
        assertEquals(25, WhatsAppLogic.confirmSeconds("א".repeat(81)));
        assertEquals(8000L, WhatsAppLogic.confirmMs("היי"));
        assertEquals(25000L, WhatsAppLogic.confirmMs("א".repeat(90)));
        String stripped = Classifier.classify("וואטסאפ היי", false, true).stripped;
        assertEquals("היי", stripped);
        assertEquals(8, WhatsAppLogic.confirmSeconds(stripped));
    }

    @Test public void packagePrefersConsumerApp() {
        assertEquals(WhatsAppLogic.PKG, WhatsAppLogic.packageName(true, true));
        assertEquals(WhatsAppLogic.PKG, WhatsAppLogic.packageName(true, false));
        assertEquals(WhatsAppLogic.PKG_BUSINESS, WhatsAppLogic.packageName(false, true));
        assertNull(WhatsAppLogic.packageName(false, false));
        assertEquals("com.whatsapp", WhatsAppLogic.PKG);
        assertEquals("com.whatsapp.w4b", WhatsAppLogic.PKG_BUSINESS);
    }

    @Test public void opusFromApi29OtherwiseM4a() {
        assertFalse(WhatsAppLogic.useOpus(26));
        assertFalse(WhatsAppLogic.useOpus(28));
        assertTrue(WhatsAppLogic.useOpus(29));
        assertEquals("m4a", WhatsAppLogic.audioExt(26));
        assertEquals("audio/mp4", WhatsAppLogic.audioMime(26));
        assertEquals("ogg", WhatsAppLogic.audioExt(29));
        assertEquals("audio/ogg", WhatsAppLogic.audioMime(34));
    }

    @Test public void modeLabels() {
        assertEquals("קול או תמלול", WhatsAppLogic.modeLabel(WhatsAppLogic.Mode.BOTH));
        assertEquals("קול", WhatsAppLogic.modeLabel(WhatsAppLogic.Mode.VOICE));
        assertEquals("תמלול", WhatsAppLogic.modeLabel(WhatsAppLogic.Mode.TRANSCRIPT));
        assertEquals(0, WhatsAppLogic.modeIndex(WhatsAppLogic.Mode.BOTH));
        assertTrue(WhatsAppLogic.modeHint(WhatsAppLogic.Mode.BOTH).contains("רק"));
        assertTrue(WhatsAppLogic.modeHint(WhatsAppLogic.Mode.VOICE).contains("קובץ שמע"));
        assertTrue(WhatsAppLogic.modeHint(WhatsAppLogic.Mode.VOICE).contains("לא כהודעה קולית"));
        assertTrue(WhatsAppLogic.modeHint(WhatsAppLogic.Mode.TRANSCRIPT).contains("שלח"));
        assertTrue(WhatsAppLogic.modeHint(WhatsAppLogic.Mode.BOTH).contains("שלח תמלול"));
        assertEquals("הודעת קול", WhatsAppLogic.preview("  "));
        assertEquals("לדני", WhatsAppLogic.preview(" לדני "));
    }

    @Test public void voiceRecordsOnlyAfterTheRecipientAndFallsBackToText() {
        assertEquals(WhatsAppLogic.AfterMatch.TEXT_CARD, WhatsAppLogic.afterMatch(WhatsAppLogic.Mode.TRANSCRIPT));
        assertEquals(WhatsAppLogic.AfterMatch.RECORD, WhatsAppLogic.afterMatch(WhatsAppLogic.Mode.VOICE));
        assertEquals(WhatsAppLogic.AfterMatch.CHOICE_CARD, WhatsAppLogic.afterMatch(WhatsAppLogic.Mode.BOTH));
        assertTrue(WhatsAppLogic.countdownSendsTranscript(WhatsAppLogic.Mode.TRANSCRIPT, false));
        assertTrue(WhatsAppLogic.countdownSendsTranscript(WhatsAppLogic.Mode.BOTH, false));
        assertTrue(WhatsAppLogic.countdownSendsTranscript(WhatsAppLogic.Mode.VOICE, false));
        assertFalse(WhatsAppLogic.countdownSendsTranscript(WhatsAppLogic.Mode.VOICE, true));
        assertTrue(WhatsAppLogic.fallbackToTranscript(false));
        assertFalse(WhatsAppLogic.fallbackToTranscript(true));
        assertEquals("שלח תמלול", WhatsAppLogic.BTN_SEND_TEXT);
        assertEquals("הקלט קול", WhatsAppLogic.BTN_RECORD);
        assertEquals("עצור", WhatsAppLogic.BTN_STOP);
        assertTrue(WhatsAppLogic.RECORD_FAILED.contains("תמלול"));
    }

    @Test public void voiceTakeStopsOnSilenceCapOrTheStopButton() {
        assertFalse(WhatsAppLogic.voiceShouldStop(1000, 500, false));
        assertTrue(WhatsAppLogic.voiceShouldStop(1000, WhatsAppLogic.VOICE_SILENCE_MS, false));
        assertTrue(WhatsAppLogic.voiceShouldStop(WhatsAppLogic.VOICE_CAP_MS, 0, false));
        assertTrue(WhatsAppLogic.voiceShouldStop(100, 0, true));
        assertEquals(0L, WhatsAppLogic.quietAfter(1500, WhatsAppLogic.VOICE_QUIET, 250));
        assertEquals(1750L, WhatsAppLogic.quietAfter(1500, WhatsAppLogic.VOICE_QUIET - 1, 250));
        assertEquals(2000L, WhatsAppLogic.VOICE_SILENCE_MS);
        assertEquals(60000L, WhatsAppLogic.VOICE_CAP_MS);
    }

    @Test public void voiceShareTargetsTheJidAndDoesNotRecordBesideSpeech() throws Exception {
        String share = new String(Files.readAllBytes(Path.of("src/main/java/com/babisnap/app/WhatsAppShare.java")), StandardCharsets.UTF_8);
        int method = share.indexOf("static VoiceShare voiceToJid");
        assertTrue(method > 0);
        String body = share.substring(method);
        assertTrue(body.contains("EXTRA_STREAM"));
        assertTrue(body.contains("\"jid\""));
        assertTrue(body.contains("FLAG_GRANT_READ_URI_PERMISSION"));
        assertTrue(body.contains("setPackage(pkg)"));
        assertFalse(body.contains("createChooser"));
        String voice = new String(Files.readAllBytes(Path.of("src/main/java/com/babisnap/app/VoiceActivity.java")), StandardCharsets.UTF_8);
        int stub = voice.indexOf("void startWaCaptureIfWanted()");
        int next = voice.indexOf("void stopWaCapture");
        assertTrue(stub > 0 && next > stub);
        String stubBody = voice.substring(stub, next);
        assertFalse(stubBody.contains("MediaRecorder"));
        assertFalse(stubBody.contains(".start("));
        assertTrue(voice.contains("beginVoiceTake"));
        assertTrue(voice.contains("WhatsAppShare.voiceToJid"));
        assertTrue(voice.contains("WhatsAppLogic.RECORD_FAILED"));
    }

    @Test public void fileProviderIsPrivateCacheOnly() throws Exception {
        String manifest = new String(Files.readAllBytes(Path.of("src/main/AndroidManifest.xml")), StandardCharsets.UTF_8);
        int auth = manifest.indexOf("${applicationId}.wa");
        assertTrue(auth > 0);
        int start = manifest.lastIndexOf("<provider", auth);
        int end = manifest.indexOf(">", auth);
        String tag = manifest.substring(start, end);
        assertTrue(tag.contains("exported=\"false\""));
        assertFalse(tag.contains("exported=\"true\""));
        assertTrue(tag.contains("grantUriPermissions=\"true\""));
        assertTrue(manifest.contains("android:name=\"com.whatsapp\""));
        assertTrue(manifest.contains("android:name=\"com.whatsapp.w4b\""));
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"));
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"));
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"));
        assertFalse(manifest.contains("READ_MEDIA_AUDIO"));
        String paths = new String(Files.readAllBytes(Path.of("src/main/res/xml/wa_file_paths.xml")), StandardCharsets.UTF_8);
        assertTrue(paths.contains("cache-path"));
        assertTrue(paths.contains("path=\"wa/\""));
        assertFalse(paths.contains("external-path"));
        assertFalse(paths.contains("root-path"));
        assertFalse(paths.contains("files-path"));
    }
}
