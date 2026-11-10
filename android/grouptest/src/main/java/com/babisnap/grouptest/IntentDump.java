package com.babisnap.grouptest;

import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/** Flattens a shortcut result into strings. Callers must not log the strings. */
final class IntentDump {
    static List<String> strings(Intent intent) {
        List<String> out = new ArrayList<>();
        walk(intent, out, 0);
        return out;
    }

    static String label(Intent data) {
        if (data == null) return null;
        String named = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (named != null && !named.isEmpty()) return named;
        CharSequence seq = data.getCharSequenceExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (seq != null && seq.length() > 0) return seq.toString();
        return labelFrom(data, 0);
    }

    private static void walk(Intent intent, List<String> out, int depth) {
        if (intent == null || depth > 5) return;
        add(out, intent.getDataString());
        try {
            add(out, intent.toUri(Intent.URI_INTENT_SCHEME));
        } catch (RuntimeException ignored) {
            // The uri is only a parse source. Failure just means fewer blobs.
        }
        Bundle extras = extras(intent);
        if (extras == null) return;
        for (String key : extras.keySet()) {
            Object value;
            try {
                value = extras.get(key);
            } catch (RuntimeException ignored) {
                continue;
            }
            addValue(value, out, depth);
        }
    }

    private static String labelFrom(Intent intent, int depth) {
        if (intent == null || depth > 5) return null;
        Bundle extras = extras(intent);
        if (extras == null) return null;
        for (String key : extras.keySet()) {
            Object value;
            try {
                value = extras.get(key);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (value instanceof ShortcutInfo) {
                CharSequence label = ((ShortcutInfo) value).getShortLabel();
                if (label != null && label.length() > 0) return label.toString();
            } else if (value instanceof Intent) {
                String nested = labelFrom((Intent) value, depth + 1);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static Bundle extras(Intent intent) {
        try {
            return intent.getExtras();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static void addValue(Object value, List<String> out, int depth) {
        if (value == null) return;
        if (value instanceof CharSequence || value instanceof Uri) {
            add(out, value.toString());
            return;
        }
        if (value instanceof Intent) {
            walk((Intent) value, out, depth + 1);
            return;
        }
        if (value instanceof Bundle) {
            Bundle bundle = (Bundle) value;
            for (String key : bundle.keySet()) {
                try {
                    addValue(bundle.get(key), out, depth);
                } catch (RuntimeException ignored) {
                    // Skip one bad extra.
                }
            }
            return;
        }
        if (value instanceof ShortcutInfo) {
            ShortcutInfo info = (ShortcutInfo) value;
            add(out, info.getId());
            if (info.getShortLabel() != null) add(out, info.getShortLabel().toString());
            if (Build.VERSION.SDK_INT >= 25) {
                Intent[] intents = info.getIntents();
                if (intents != null) {
                    for (Intent nested : intents) walk(nested, out, depth + 1);
                }
            }
            walk(info.getIntent(), out, depth + 1);
            return;
        }
        if (value instanceof ArrayList) {
            for (Object item : (ArrayList<?>) value) addValue(item, out, depth);
        }
    }

    private static void add(List<String> out, String value) {
        if (value != null && !value.isEmpty()) out.add(value);
    }

    private IntentDump() {}
}
