package com.babisnap.app;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

/** Share sheet into the WhatsApp app installed on the phone. No network call from BabiSnap. */
final class WhatsAppShare {
    static final int REQ = 5;
    static final String AUTHORITY_SUFFIX = ".wa";

    static String resolvePackage(Context c) {
        PackageManager pm = c.getPackageManager();
        return WhatsAppLogic.packageName(installed(pm, WhatsAppLogic.PKG), installed(pm, WhatsAppLogic.PKG_BUSINESS));
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static Intent text(String pkg, String text) {
        return new Intent(Intent.ACTION_SEND).setPackage(pkg).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
    }

    /** Private chat with the text already in the box. The user taps Send in WhatsApp. */
    static Intent textToJid(String pkg, String jid, String text) {
        return text(pkg, text == null ? "" : text).putExtra("jid", jid);
    }

    /** wa.me fallback. pkg may be null when the package-targeted view did not resolve. */
    static Intent waMe(String pkg, String e164Digits, String text) {
        String digits = e164Digits == null ? "" : (e164Digits.startsWith("+") ? e164Digits.substring(1) : e164Digits);
        Uri uri = Uri.parse(WhatsAppRecipients.waMeUrl(digits, text));
        Intent view = new Intent(Intent.ACTION_VIEW, uri);
        if (pkg != null) view.setPackage(pkg);
        return view;
    }

    /** Cache file via FileProvider. Caller grants read to the target package only. */
    static Intent voice(Context c, String pkg, File file, String mime) {
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + AUTHORITY_SUFFIX, file);
        Intent i = new Intent(Intent.ACTION_SEND).setPackage(pkg).setType(mime).putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri("voice", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    private WhatsAppShare() {}
}
