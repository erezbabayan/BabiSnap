package com.babisnap.grouptest;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Installed-package and special-access checks. Versions only, never account data. */
final class Access {
    static final String WA = "com.whatsapp";
    static final String W4B = "com.whatsapp.w4b";

    static boolean installed(Context context, String pkg) {
        return info(context, pkg) != null;
    }

    static String preferredPkg(Context context) {
        if (installed(context, WA)) return WA;
        if (installed(context, W4B)) return W4B;
        return null;
    }

    static String waVersion(Context context) {
        PackageInfo wa = info(context, WA);
        if (wa != null && wa.versionName != null) return wa.versionName;
        PackageInfo business = info(context, W4B);
        if (business != null) return business.versionName;
        return null;
    }

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
        return flat.toLowerCase(java.util.Locale.US).contains(component.toLowerCase(java.util.Locale.US));
    }

    private static PackageInfo info(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 33) {
                return pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0));
            }
            return pm.getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private Access() {}
}
