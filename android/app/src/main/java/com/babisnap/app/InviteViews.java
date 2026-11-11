package com.babisnap.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Stage-2 invite views for the meeting card, drawn to match the invites mockup (colors/sizes from invites-preview.html). */
final class InviteViews {
    interface OnPick { void picked(ContactMatcher.Match m); }

    /** White icon on this green clears 4.5:1. Same family as the call chip, a step darker. */
    static final int DIAL_GREEN = 0xFF0E7A3A;
    /** White icon on this red clears 4.5:1. */
    static final int CANCEL_RED = 0xFFB42318;
    static final int CIRCLE_DP = 72;

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
        no.setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7)); no.setGravity(Gravity.CENTER);
        no.setMinHeight(dp(c, 48)); no.setMinimumHeight(dp(c, 48)); no.setMinWidth(dp(c, 48)); no.setMinimumWidth(dp(c, 48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8); lp.gravity = Gravity.CENTER_HORIZONTAL;
        no.setOnClickListener(v -> onPick.picked(null));
        root.addView(no, lp);
        return root;
    }

    private static View row(Context c, ContactMatcher.Match m, View.OnClickListener tap) {
        LinearLayout r = new LinearLayout(c); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(c, 58)); r.setPadding(dp(c, 11), dp(c, 9), dp(c, 11), dp(c, 9));
        r.setOnClickListener(tap);
        armRow(r);
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

    /**
     * WhatsApp and call multi-match picker. Search filters live and does not open the keyboard
     * until the field is tapped. «ביטול» is always on screen. No countdown.
     */
    static View peoplePicker(Context c, String question, String spoken,
            java.util.List<WhatsAppRecipients.Contact> people,
            java.util.function.Function<WhatsAppRecipients.Contact, String> detail,
            java.util.function.Consumer<WhatsAppRecipients.Contact> onPick,
            Runnable onCancel, String skip, Runnable onSkip) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(full(c, 4));

        EditText field = searchField(c);
        root.addView(field, full(c, 8));

        TextView qt = new TextView(c);
        qt.setText(question == null ? "" : question);
        qt.setTextColor(0xFF2A1B7A);
        qt.setTextSize(14);
        qt.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(qt, full(c, 10));

        PickerScroll scroll = new PickerScroll(c);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(box(c, 0xFFFFFFFF, 0xFFEADFB5, 1.5f, 13));
        int pad = dp(c, 1.5f);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, full(c, 7));

        TextView empty = new TextView(c);
        empty.setText("אין התאמה");
        empty.setTextColor(0xFF7A7888);
        empty.setTextSize(13);
        empty.setGravity(Gravity.CENTER);
        empty.setMinHeight(dp(c, 48));
        empty.setMinimumHeight(dp(c, 48));
        empty.setVisibility(View.GONE);
        root.addView(empty, full(c, 6));

        Runnable bind = () -> {
            String q = field.getText() == null ? "" : field.getText().toString();
            java.util.List<WhatsAppRecipients.Contact> shown = ContactPick.present(spoken, people, q);
            list.removeAllViews();
            for (int i = 0; i < shown.size(); i++) {
                if (i > 0) {
                    View div = new View(c);
                    div.setBackgroundColor(0xFFF1EBD3);
                    div.setClickable(false);
                    list.addView(div, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1)));
                }
                final int index = i;
                final java.util.List<WhatsAppRecipients.Contact> rows = shown;
                WhatsAppRecipients.Contact person = ContactPick.picked(rows, index);
                String line = person == null || detail == null ? "" : detail.apply(person);
                list.addView(choiceRow(c, person == null ? "" : person.label(), line, v -> {
                    WhatsAppRecipients.Contact chosen = ContactPick.picked(rows, index);
                    if (chosen != null && onPick != null) onPick.accept(chosen);
                }));
            }
            boolean none = shown.isEmpty();
            empty.setVisibility(none ? View.VISIBLE : View.GONE);
            scroll.setVisibility(none ? View.GONE : View.VISIBLE);
        };
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { bind.run(); }
        });
        bind.run();

        if (skip != null && !skip.isEmpty() && onSkip != null) root.addView(textButton(c, skip, false, v -> onSkip.run()), full(c, 8));
        root.addView(textButton(c, "ביטול", true, v -> { if (onCancel != null) onCancel.run(); }), full(c, 8));
        return root;
    }

    /** Not focusable until tapped, so the dialog does not open the keyboard by itself. */
    private static EditText searchField(Context c) {
        EditText field = new EditText(c);
        field.setHint("חיפוש איש קשר");
        field.setContentDescription("חיפוש איש קשר");
        field.setSingleLine(true);
        field.setTextSize(15);
        field.setTextColor(0xFF2A1B7A);
        field.setHintTextColor(0xFF7A7888);
        field.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        field.setMinHeight(dp(c, 48));
        field.setMinimumHeight(dp(c, 48));
        field.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        field.setBackground(box(c, 0xFFFFFFFF, 0xFFEADFB5, 1.5f, 12));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setShowSoftInputOnFocus(true);
        field.setClickable(true);
        field.setFocusable(false);
        field.setFocusableInTouchMode(false);
        field.setOnTouchListener((v, e) -> {
            if (v.isFocused()) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                v.setFocusable(true);
                v.setFocusableInTouchMode(true);
                v.requestFocus();
                InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
            }
            return true;
        });
        return field;
    }

    private static TextView textButton(Context c, String label, boolean filled, View.OnClickListener tap) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setContentDescription(label);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(filled ? 0xFF2A1B7A : 0xFF6A5FA8);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setMinWidth(dp(c, 48));
        b.setMinimumWidth(dp(c, 48));
        b.setBackground(box(c, 0xFFFFFFFF, filled ? 0xFF2A1B7A : 0xFFD9D0F0, 1.5f, 12));
        b.setOnClickListener(tap);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    /** Clickable and focusable, and the name text must not take the tap away from the row. */
    private static void armRow(LinearLayout r) {
        r.setClickable(true);
        r.setFocusable(true);
        r.setFocusableInTouchMode(false);
        r.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        r.setMinimumHeight(Math.max(r.getMinimumHeight(), dp(r.getContext(), 48)));
        android.util.TypedValue tv = new android.util.TypedValue();
        if (r.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true) && tv.resourceId != 0)
            r.setBackgroundResource(tv.resourceId);
    }

    private static View choiceRow(Context c, String title, String detail, View.OnClickListener tap) {
        ContactMatcher.Match m = new ContactMatcher.Match(title == null ? "" : title, detail == null ? "" : detail, null);
        return row(c, m, tap);
    }

    /**
     * The floating dialog scrolls its whole card when the list is taller than the window,
     * and that outer scroll eats the row tap. This scroller is height-capped and tells
     * every parent to leave the gesture alone, so a tap reaches the row and only the list scrolls.
     */
    private static final class PickerScroll extends ScrollView {
        PickerScroll(Context c) {
            super(c);
            setFillViewport(false);
            setNestedScrollingEnabled(false);
            setClickable(false);
            setFocusable(false);
            setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) holdParents();
            return super.onInterceptTouchEvent(e);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) holdParents();
            return super.onTouchEvent(e);
        }

        private void holdParents() {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int max = maxListPx(getContext());
            int mode = View.MeasureSpec.getMode(hSpec);
            int size = View.MeasureSpec.getSize(hSpec);
            int cap = (mode == View.MeasureSpec.UNSPECIFIED) ? max : Math.min(max, size);
            super.onMeasure(wSpec, View.MeasureSpec.makeMeasureSpec(Math.max(cap, 0), View.MeasureSpec.AT_MOST));
        }

        private static int maxListPx(Context c) {
            int screen = c.getResources().getDisplayMetrics().heightPixels;
            int room = screen - dp(c, 420);
            int floor = dp(c, 160);
            int ceil = dp(c, 280);
            if (room < floor) return floor;
            return Math.min(room, ceil);
        }
    }

    /**
     * One dialer-style action: a 72dp circle, a short label under it, and the whole column as the touch target.
     * The content description is the label, once.
     */
    static View roundAction(Context c, String label, int color, int icon, View.OnClickListener tap) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int wide = dp(c, 72);
        col.setMinimumWidth(wide);
        col.setPadding(dp(c, 6), dp(c, 4), dp(c, 6), dp(c, 4));
        col.setClickable(true);
        col.setFocusable(true);
        col.setContentDescription(label);
        col.setOnClickListener(tap);
        col.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        ImageView glyph = new ImageView(c);
        glyph.setImageResource(icon);
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        glyph.setColorFilter(0xFFFFFFFF);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int box = dp(c, CIRCLE_DP);
        int inset = dp(c, 18);
        glyph.setPadding(inset, inset, inset, inset);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        glyph.setBackground(circle);
        col.addView(glyph, new LinearLayout.LayoutParams(box, box));

        TextView caption = new TextView(c);
        caption.setText(label);
        caption.setTextColor(0xFF2A1B7A);
        caption.setTextSize(13);
        caption.setTypeface(Typeface.DEFAULT_BOLD);
        caption.setGravity(Gravity.CENTER);
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(c, 6);
        col.addView(caption, cp);
        return col;
    }

    private InviteViews() {}
}
