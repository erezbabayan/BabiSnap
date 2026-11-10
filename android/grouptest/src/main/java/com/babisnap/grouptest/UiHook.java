package com.babisnap.grouptest;

/** The probe screen refreshes when the accessibility service sees a new result. */
final class UiHook {
    interface Listener {
        void onChange();
    }

    private static volatile Listener listener;

    static void set(Listener next) {
        listener = next;
    }

    static void ping() {
        Listener current = listener;
        if (current != null) current.onChange();
    }

    private UiHook() {}
}
