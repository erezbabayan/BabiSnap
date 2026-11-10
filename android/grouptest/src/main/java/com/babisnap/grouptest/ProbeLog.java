package com.babisnap.grouptest;

import android.util.Log;

import java.util.regex.Pattern;

/** Logcat is codes only. A value that is not a code is dropped, not printed. */
final class ProbeLog {
    private static final Pattern OK = Pattern.compile("[A-Z0-9_]{1,48}");

    static void code(String code) {
        if (code == null || !OK.matcher(code).matches()) return;
        Log.i("BabiSnapK0", code);
    }

    private ProbeLog() {}
}
