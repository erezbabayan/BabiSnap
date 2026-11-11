package com.babisnap.grouptest;

import android.view.View;
import android.widget.TextView;

/** Binds the shared countdown card. The transcript is shown in full. */
final class SendCard {
    static void show(View root, String headline, String seconds, String check, String transcript, String cancel) {
        if (root == null) return;
        text(root, R.id.card_headline, headline);
        text(root, R.id.card_seconds, seconds);
        text(root, R.id.card_check, check);
        text(root, R.id.card_body, transcript == null ? "" : transcript);
        text(root, R.id.card_cancel, cancel);
    }

    private static void text(View root, int id, String value) {
        TextView view = root.findViewById(id);
        if (view != null) view.setText(value == null ? "" : value);
    }

    private SendCard() {}
}
