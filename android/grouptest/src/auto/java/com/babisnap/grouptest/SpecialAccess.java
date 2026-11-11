package com.babisnap.grouptest;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import java.util.Locale;

/** Auto build: accessibility only. No notification listener. */
final class SpecialAccess {
    static boolean notifications(Context context) {
        return false;
    }

    static boolean accessibility(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        String component = new ComponentName(context, AutoA11yService.class).flattenToString();
        return flat.toLowerCase(Locale.US).contains(component.toLowerCase(Locale.US));
    }

    private SpecialAccess() {}
}
