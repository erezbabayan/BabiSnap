package com.babisnap.app;

import android.content.Context;

import java.util.List;

/** One registry heartbeat per Jerusalem day, or immediately when app_version changes. No-op without a signed-in email. */
final class SnapHeartbeat {
    private static volatile boolean inFlight;

    static void onAppOpen(Context c) {
        Context app = c.getApplicationContext();
        String email = SnapRegistry.normalizeEmail(Core.prefs(app).getString("email", null));
        if (!SnapRegistry.validEmail(email) || !SnapClient.configured()) return;
        String ver = SnapInstall.appVersion(app);
        long last = Core.prefs(app).getLong(SnapInstall.HB_AT, 0);
        String lastVer = Core.prefs(app).getString(SnapInstall.HB_VER, "");
        synchronized (SnapHeartbeat.class) {
            if (inFlight) return;
            if (!SnapRegistry.shouldHeartbeat(last, lastVer, ver, System.currentTimeMillis())) return;
            inFlight = true;
        }
        String installId = SnapInstall.id(app);
        List<String> sources = SnapRegistry.sources(true, BabiTk.hasToken(app));
        String device = SnapInstall.deviceLabel();
        new Thread(() -> {
            try {
                SnapRegistry.PostResult r = SnapClient.post(email, installId, SnapRegistry.EVENT_HEARTBEAT, sources, ver, device);
                if (r != null && r.ok) {
                    Core.prefs(app).edit().putLong(SnapInstall.HB_AT, System.currentTimeMillis()).putString(SnapInstall.HB_VER, ver).apply();
                }
            } catch (Exception ignored) {
            } finally { inFlight = false; }
        }, "snap-heartbeat").start();
    }

    private SnapHeartbeat() {}
}
