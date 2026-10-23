package com.babisnap.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Stage-2 invite views for the meeting card, drawn to match the invites mockup (colors/sizes from invites-preview.html). */
final class InviteViews {
    interface OnPick { void picked(ContactMatcher.Match m); }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable box(Context c, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    private static LinearLayout.LayoutParams full(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp); return lp;
    }

    /** Blue "✉️ זימון יישלח ל: name · email" or orange "⚠️ לא נשלח זימון – ..." strip. */
    static View strip(Context c, boolean ok, String text, String boldName) {
        TextView t = new TextView(c);
        String full = (ok ? "✉️ " : "⚠️ ") + text;
        SpannableString s = new SpannableString(full);
        int at = boldName == null ? -1 : full.indexOf(boldName);
        if (at >= 0) s.setSpan(new StyleSpan(Typeface.BOLD), at, at + boldName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(s);
        t.setTextSize(13); t.setLineSpacing(0, 1.2f); t.setGravity(Gravity.START);
        t.setTextColor(ok ? 0xFF1A3E8A : 0xFF9A4A00);
        t.setBackground(box(c, ok ? 0xFFEEF3FE : 0xFFFFF1E6, ok ? 0xFFC9D8F6 : 0xFFF5D3B3, 1.5f, 12));
        t.setPadding(dp(c, 11), dp(c, 8), dp(c, 11), dp(c, 8));
        t.setLayoutParams(full(c, 8));
        return t;
    }

    /** "✉️ למי לשלוח זימון? … N אנשי קשר", the list (avatar, name + tag, email, ‹) and "בלי זימון". */
    static View picker(Context c, InviteFlow.Invitee inv, int people, OnPick onPick) {
        LinearLayout root = new LinearLayout(c); root.setOrientation(LinearLayout.VERTICAL); root.setLayoutParams(full(c, 4));

        LinearLayout q = new LinearLayout(c); q.setGravity(Gravity.CENTER_VERTICAL); q.setLayoutParams(full(c, 12));
        TextView qt = new TextView(c); qt.setText("✉️ " + InviteFlow.pickerQuestion(inv, people));
        qt.setTextColor(0xFF2A1B7A); qt.setTextSize(14); qt.setTypeface(Typeface.DEFAULT_BOLD);
        q.addView(qt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView cnt = new TextView(c); cnt.setText(InviteFlow.pickerCount(inv.matches.size())); cnt.setTextColor(0xFF7A7888); cnt.setTextSize(12);
        q.addView(cnt);
        root.addView(q);

        LinearLayout list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(box(c, 0xFFFFFFFF, 0xFFEADFB5, 1.5f, 13)); list.setLayoutParams(full(c, 7));
        int pad = dp(c, 1.5f); list.setPadding(pad, pad, pad, pad);
        for (int i = 0; i < inv.matches.size(); i++) {
            if (i > 0) { View div = new View(c); div.setBackgroundColor(0xFFF1EBD3); list.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1))); }
            final ContactMatcher.Match m = inv.matches.get(i);
            list.addView(row(c, m, v -> onPick.picked(m)));
        }
        root.addView(list);

        TextView no = new TextView(c); no.setText("בלי זימון"); no.setTextColor(0xFF6A5FA8); no.setTextSize(13.5f);
        no.setPadding(dp(c, 7), dp(c, 7), dp(c, 7), dp(c, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8); lp.gravity = Gravity.CENTER_HORIZONTAL;
        no.setOnClickListener(v -> onPick.picked(null));
        root.addView(no, lp);
        return root;
    }

    private static View row(Context c, ContactMatcher.Match m, View.OnClickListener tap) {
        LinearLayout r = new LinearLayout(c); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(c, 58)); r.setPadding(dp(c, 11), dp(c, 9), dp(c, 11), dp(c, 9));
        r.setOnClickListener(tap); r.setClickable(true);
        r.setContentDescription(m.name);

        TextView av = new TextView(c);
        av.setText(m.name.isEmpty() ? "?" : m.name.substring(0, m.name.offsetByCodePoints(0, 1)));
        av.setTextColor(0xFFFFC531); av.setTextSize(15); av.setTypeface(Typeface.DEFAULT_BOLD); av.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor(0xFF2A1B7A);
        av.setBackground(circle);
        r.addView(av, new LinearLayout.LayoutParams(dp(c, 34), dp(c, 34)));

        LinearLayout tx = new LinearLayout(c); tx.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams txp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        txp.setMarginStart(dp(c, 10)); txp.setMarginEnd(dp(c, 10));
        LinearLayout nm = new LinearLayout(c); nm.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(c); name.setText(m.name); name.setTextColor(0xFF2A1B7A); name.setTextSize(14); name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        nm.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0));
        if (m.tag != null && !m.tag.isEmpty()) {
            TextView tag = new TextView(c); tag.setText(m.tag); tag.setTextColor(0xFF7A5A00); tag.setTextSize(10.5f); tag.setTypeface(Typeface.DEFAULT_BOLD);
            tag.setSingleLine(true); tag.setEllipsize(TextUtils.TruncateAt.END);
            tag.setBackground(box(c, 0xFFFFF1C4, 0, 0, 8)); tag.setPadding(dp(c, 7), 0, dp(c, 7), 0);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tp.setMarginStart(dp(c, 6));
            nm.addView(tag, tp);
        }
        tx.addView(nm);
        TextView em = new TextView(c); em.setText(m.email); em.setTextColor(0xFF7A7888); em.setTextSize(12);
        em.setTextDirection(View.TEXT_DIRECTION_LTR); em.setGravity(Gravity.RIGHT); em.setSingleLine(true); em.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(em, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(tx, txp);

        TextView chev = new TextView(c); chev.setText("‹"); chev.setTextColor(0xFFB9B2DA); chev.setTextSize(16);
        r.addView(chev);
        return r;
    }

    private InviteViews() {}
}
