package com.babisnap.grouptest;

import android.content.Context;

/** Lite build has no accessibility service and no notification listener. */
final class SpecialAccess {
    static boolean notifications(Context context) {
        return false;
    }

    static boolean accessibility(Context context) {
        return false;
    }

    private SpecialAccess() {}
}
