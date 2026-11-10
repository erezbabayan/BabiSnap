package com.babisnap.grouptest;

/** In-process arm for one test send. Names and text stay in memory, never in an Intent of ours. */
final class Arm {
    static final class Payload {
        final String expectedTitle;
        final String kind;
        final String alias;
        final String name;
        final String transcript;
        final boolean checked;
        volatile boolean cancelled;
        volatile boolean consumed;
        volatile boolean counting;

        Payload(String expectedTitle, String kind, String alias, String name, String transcript, boolean checked) {
            this.expectedTitle = expectedTitle == null ? "" : expectedTitle;
            this.kind = kind == null ? "" : kind;
            this.alias = alias == null ? "" : alias;
            this.name = name == null ? "" : name;
            this.transcript = transcript == null ? "" : transcript;
            this.checked = checked;
        }
    }

    private static volatile Payload current;

    static void arm(Payload payload) {
        current = payload;
    }

    static Payload get() {
        return current;
    }

    static void cancel() {
        Payload payload = current;
        if (payload != null) payload.cancelled = true;
    }

    static boolean ready() {
        Payload payload = current;
        return payload != null && !payload.cancelled && !payload.consumed;
    }

    private Arm() {}
}
