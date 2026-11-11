package com.babisnap.grouptest;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

/**
 * Presses WhatsApp Send only inside a short window, and only when the open chat
 * title and the compose box match the armed private recipient. No overlay.
 */
public class AutoA11yService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Arm.Payload seen;
    private boolean finished;
    private boolean timing;
    private String lastReason = "TIMEOUT";

    private final Runnable deadline = () -> {
        if (finished) return;
        Arm.Payload payload = Arm.get();
        if (payload == null || payload.consumed) return;
        finish(lastReason);
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !isWhatsApp(event.getPackageName())) return;
        Arm.Payload payload = Arm.get();
        if (payload == null || payload.cancelled || payload.consumed || !payload.checked) return;
        if (payload != seen) {
            seen = payload;
            finished = false;
            timing = false;
            lastReason = "TIMEOUT";
            handler.removeCallbacks(deadline);
        }
        if (finished) return;
        if (payload.deadlineMs > 0 && SystemClock.elapsedRealtime() > payload.deadlineMs) {
            finish(lastReason);
            return;
        }
        if (!timing && payload.deadlineMs > 0) {
            timing = true;
            long wait = Math.max(0L, payload.deadlineMs - SystemClock.elapsedRealtime());
            handler.postDelayed(deadline, wait);
            ProbeLog.code("AUTO_WINDOW");
        }
        Observation obs = observe();
        if (!obs.whatsApp) {
            lastReason = "NOT_FOREGROUND";
            return;
        }
        boolean title = ProbeLogic.titlesMatch(obs.title, payload.expectedTitle);
        boolean text = ProbeLogic.textContains(obs.entry, payload.transcript);
        Store.get(this).setObservation(
                obs.title == null || obs.title.isEmpty() ? "UNKNOWN" : (title ? "YES" : "NO"),
                obs.sendNode == null ? "NO" : "YES",
                obs.sendNode == null ? "NONE" : ProbeLogic.descCode(obs.sendDesc),
                null);
        if (obs.title == null || obs.title.isEmpty()) {
            lastReason = "NO_TITLE";
            return;
        }
        if (!title) {
            lastReason = "TITLE_MISMATCH";
            return;
        }
        if (!text) {
            lastReason = "TEXT_MISMATCH";
            return;
        }
        if (obs.sendNode == null) {
            lastReason = "NO_BUTTON";
            return;
        }
        if (!ProbeLogic.descConfirmsSend(obs.sendDesc)) {
            lastReason = "SEND_UNCONFIRMED";
            return;
        }
        boolean clicked = false;
        try {
            clicked = obs.sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (RuntimeException e) {
            clicked = false;
        }
        finish(clicked ? "PRESSED" : "CLICK_FAILED");
    }

    @Override
    public void onInterrupt() {
        Arm.cancel();
        finish("CANCELLED");
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void finish(String code) {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(deadline);
        Arm.Payload payload = Arm.get();
        if (payload != null) payload.consumed = true;
        Store.get(this).putCode("autoResult", code);
        ProbeLog.code("AUTO_" + code);
        if (!"PRESSED".equals(code) && !"CANCELLED".equals(code)) {
            Toast.makeText(this, "לא נשלח, לחץ שלח ידנית", Toast.LENGTH_LONG).show();
        } else if ("PRESSED".equals(code)) {
            Toast.makeText(this, "נלחץ שלח", Toast.LENGTH_SHORT).show();
        }
        UiHook.ping();
    }

    private Observation observe() {
        Observation obs = new Observation();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return obs;
        CharSequence pkg = root.getPackageName();
        obs.whatsApp = isWhatsApp(pkg);
        if (obs.whatsApp) walk(root, obs);
        return obs;
    }

    /** Title from the toolbar id, compose text from the entry id, send control by id or label. */
    private void walk(AccessibilityNodeInfo node, Observation obs) {
        if (node == null) return;
        String id = safeId(node);
        String desc = safeDesc(node);
        if (obs.title == null && ProbeLogic.idIsTitle(id)) {
            String text = safeText(node);
            if (text == null || text.isEmpty()) text = desc;
            if (text != null && !text.isEmpty()) obs.title = text;
        }
        if ((obs.entry == null || obs.entry.isEmpty()) && ProbeLogic.idIsEntry(id)) {
            String text = safeText(node);
            if (text != null && !text.isEmpty()) obs.entry = text;
        }
        if (obs.sendNode == null && ProbeLogic.isSendControl(id, desc, node.isClickable())) {
            obs.sendNode = node;
            obs.sendDesc = desc;
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), obs);
    }

    private static String safeId(AccessibilityNodeInfo node) {
        try {
            return node.getViewIdResourceName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeDesc(AccessibilityNodeInfo node) {
        try {
            CharSequence value = node.getContentDescription();
            return value == null ? null : value.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeText(AccessibilityNodeInfo node) {
        try {
            CharSequence value = node.getText();
            return value == null ? null : value.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isWhatsApp(CharSequence pkg) {
        if (pkg == null) return false;
        String value = pkg.toString();
        return Access.WA.equals(value) || Access.W4B.equals(value);
    }

    private static final class Observation {
        boolean whatsApp;
        String title;
        String entry;
        String sendDesc;
        AccessibilityNodeInfo sendNode;
    }
}
