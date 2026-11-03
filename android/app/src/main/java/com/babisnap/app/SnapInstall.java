package com.babisnap.app;

import android.content.Context;
import android.os.Build;

/** Stable per-install id (random UUID in prefs — never IMEI) plus the two fields the registry may store beside it. */
final class SnapInstall {
    static final String PREF = "snap_install_id";
    static final String HB_AT = "snap_hb_at";
    static final String HB_VER = "snap_hb_ver";

    static String id(Context c) {
        String cur = Core.prefs(c).getString(PREF, null);
        if (SnapRegistry.validInstallId(cur)) return cur;
        String id = SnapRegistry.newInstallId();
        Core.prefs(c).edit().putString(PREF, id).commit();
        return id;
    }

    static String appVersion(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) { return ""; }
    }

    /** Optional. Manufacturer + model only; no serial, no Android id. */
    static String deviceLabel() {
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.equalsIgnoreCase(maker)) maker = "";
        String s = (maker + " " + model).trim();
        if (s.isEmpty() || "unknown".equalsIgnoreCase(s)) return null;
        return s;
    }

    private SnapInstall() {}
}
