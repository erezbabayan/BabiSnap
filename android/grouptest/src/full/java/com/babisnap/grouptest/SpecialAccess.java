package com.babisnap.grouptest;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import java.util.Locale;

/** Full probe only. These checks name the services that are not in the lite APK. */
final class SpecialAccess {
    static boolean notifications(Context context) {
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        String component = new ComponentName(context, WaNoticeService.class).flattenToString();
        return flat.contains(component);
    }

    static boolean accessibility(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        String component = new ComponentName(context, WaA11yService.class).flattenToString();
        return flat.toLowerCase(Locale.US).contains(component.toLowerCase(Locale.US));
    }

    private SpecialAccess() {}
}
