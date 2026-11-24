package com.babisnap.app;

import static org.junit.Assert.*;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** 24.11: auto-call and WhatsApp default to on for a fresh install; a saved choice (true or false) always wins. */
public class DefaultsTest {
    /** Minimal in-memory prefs, enough for getBoolean. */
    static final class FakePrefs implements SharedPreferences {
        final Map<String, Object> m = new HashMap<>();
        FakePrefs put(String k, Object v) { m.put(k, v); return this; }
        @Override public Map<String, ?> getAll() { return m; }
        @Override public String getString(String k, String d) { return m.containsKey(k) ? (String) m.get(k) : d; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { return m.containsKey(k) ? (Integer) m.get(k) : d; }
        @Override public long getLong(String k, long d) { return m.containsKey(k) ? (Long) m.get(k) : d; }
        @Override public float getFloat(String k, float d) { return m.containsKey(k) ? (Float) m.get(k) : d; }
        @Override public boolean getBoolean(String k, boolean d) { return m.containsKey(k) ? (Boolean) m.get(k) : d; }
        @Override public boolean contains(String k) { return m.containsKey(k); }
        @Override public Editor edit() { throw new UnsupportedOperationException(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    }

    @Test public void freshInstallAutoCallIsOn() {
        assertTrue(CallDial.DEFAULT_DIRECT);
        assertTrue(CallDial.directPref(new FakePrefs()));
    }

    @Test public void savedAutoCallChoiceIsKeptOnUpgrade() {
        assertFalse(CallDial.directPref(new FakePrefs().put(CallDial.PREF_DIRECT, false)));
        assertTrue(CallDial.directPref(new FakePrefs().put(CallDial.PREF_DIRECT, true)));
    }

    @Test public void freshInstallWhatsAppIsOn() {
        assertTrue(BuildConfig.WHATSAPP_DEFAULT_ON);
        assertTrue(Core.whatsAppDefault());
        assertTrue(Core.whatsAppEnabled(new FakePrefs()));
    }

    @Test public void savedWhatsAppChoiceIsKeptOnUpgrade() {
        assertFalse(Core.whatsAppEnabled(new FakePrefs().put(WhatsAppLogic.PREF_ENABLED, false)));
        assertTrue(Core.whatsAppEnabled(new FakePrefs().put(WhatsAppLogic.PREF_ENABLED, true)));
    }

    @Test public void directDialStillNeedsThePermission() {
        // Default on + no CALL_PHONE = dialer, exactly as before.
        assertFalse(CallDial.effective(CallDial.directPref(new FakePrefs()), false));
        assertTrue(CallDial.effective(CallDial.directPref(new FakePrefs()), true));
    }

    @Test public void permissionPromptAppearsOnlyOnce() {
        assertTrue(CallDial.askOnce(true, false, false, false));
        assertFalse(CallDial.askOnce(true, false, true, false));   // already asked
        assertFalse(CallDial.askOnce(true, true, false, false));   // already granted
        assertFalse(CallDial.askOnce(false, false, false, false)); // user turned it off
        assertFalse(CallDial.askOnce(true, false, false, true));   // blocked: no prompt, no forced settings
    }

    @Test public void deniedPromptSavesOffThroughTheExistingPath() throws Exception {
        String main = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/main/java/com/babisnap/app/MainActivity.java")), java.nio.charset.StandardCharsets.UTF_8);
        int req = main.indexOf("if (req == REQ_CALL && p.length > 0)");
        assertTrue(req > 0);
        assertTrue(main.indexOf("putBoolean(CallDial.PREF_DIRECT, false)", req) > req);
        assertTrue(main.contains("CallDial.askOnce("));
        assertTrue(main.contains("putBoolean(CallDial.PREF_ASKED, true)"));
        String voice = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/main/java/com/babisnap/app/VoiceActivity.java")), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(voice.contains("Manifest.permission.CALL_PHONE}"));            // never asked from the call card
        assertTrue(voice.contains("CallDial.directPref(Core.prefs(this))"));
    }
}
