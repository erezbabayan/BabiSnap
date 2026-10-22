package com.babisnap.app;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/** Runs the shared JS parser (Code.gs, copied to assets/parser.js) inside Rhino. Pure JVM: unit-testable. */
public class HebrewParser {
    private final ScriptableObject scope;

    public HebrewParser(String jsSource) { this(jsSource, null); }

    public HebrewParser(String jsSource, String extraJs) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1); // interpreted mode: required on Android
            cx.setLanguageVersion(Context.VERSION_ES6);
            scope = cx.initStandardObjects();
            cx.evaluateString(scope, jsSource, "parser.js", 1, null);
            cx.evaluateString(scope,
                "function __parseJson(t, nowJson){ return JSON.stringify(parseHebrewEvent(t, JSON.parse(nowJson))); }",
                "glue", 1, null);
            if (extraJs != null) cx.evaluateString(scope, extraJs, "extra", 1, null);
        } finally { Context.exit(); }
    }

    /** Calls a global JS function with string args, returns its string result. */
    public synchronized String call(String fn, Object... args) {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            Object f = scope.get(fn, scope);
            Object r = ((Function) f).call(cx, scope, scope, args);
            return Context.toString(r);
        } finally { Context.exit(); }
    }

    /** @return JSON string of the parsed event (title, startStr, durationMin, location, confirm, ...). */
    public String parseJson(String text, int y, int m, int d, int dowSunday0, int defaultDuration) {
        String now = "{\"y\":" + y + ",\"m\":" + m + ",\"d\":" + d + ",\"dow\":" + dowSunday0
                + ",\"defaultDuration\":" + defaultDuration + "}";
        return call("__parseJson", text, now);
    }

    @SuppressWarnings("unused") private static Scriptable unused;
}
