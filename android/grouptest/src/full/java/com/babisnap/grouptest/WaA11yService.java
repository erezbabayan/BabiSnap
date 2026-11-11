package com.babisnap.grouptest;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/**
 * Reads the WhatsApp chat title and the send control. Presses send only after the
 * armed countdown, and only when the title matches the saved name exactly.
 */
public class WaA11yService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View overlay;
    private WindowManager windows;
    private Arm.Payload seen;
    private boolean finished;
    private int secondsLeft;
    private String lastSig = "";
    private Observation latest;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (finished) return;
            Arm.Payload payload = Arm.get();
            if (payload == null || payload != seen || payload.consumed) {
                handler.removeCallbacks(this);
                return;
            }
            if (payload.cancelled) {
                finish("CANCELLED");
                return;
            }
            SendCard.show(overlay,
                    ProbeLogic.cardHeadline(payload.kind, payload.name, payload.alias, secondsLeft),
                    secondsLeft <= 0 ? "" : Integer.toString(secondsLeft),
                    ProbeLogic.checkLine(payload.expectedTitle),
                    payload.transcript,
                    getString(R.string.cancel));
            if (secondsLeft <= 0) {
                decide();
                return;
            }
            secondsLeft--;
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !isWhatsApp(event.getPackageName())) return;
        rememberNewArm();
        long now = android.os.SystemClock.uptimeMillis();
        if (latest == null || now - latest.at > 350) {
            latest = observe(event);
            publish(latest);
        }
        maybeStart();
    }

    @Override
    public void onInterrupt() {
        Arm.cancel();
        finish("CANCELLED");
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
        super.onDestroy();
    }

    private void rememberNewArm() {
        Arm.Payload payload = Arm.get();
        if (payload == null || payload == seen) return;
        seen = payload;
        finished = false;
        lastSig = "";
        handler.removeCallbacks(tick);
        hideOverlay();
    }

    private void maybeStart() {
        if (finished) return;
        Arm.Payload payload = Arm.get();
        if (payload == null || payload.counting || payload.cancelled || payload.consumed) return;
        if (!payload.checked || !Store.get(this).bool("autoPress")) return;
        payload.counting = true;
        secondsLeft = 8;
        if (!showOverlay(payload)) return;
        ProbeLog.code("COUNTDOWN_START");
        handler.post(tick);
    }

    private boolean showOverlay(Arm.Payload payload) {
        if (overlay != null) return true;
        try {
            Context themed = new ContextThemeWrapper(this, R.style.ProbeTheme);
            overlay = LayoutInflater.from(themed).inflate(R.layout.card_countdown, null);
            View scroll = overlay.findViewById(R.id.card_scroll);
            scroll.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
            overlay.findViewById(R.id.card_cancel).setOnClickListener(v -> {
                Arm.cancel();
                ProbeLog.code("COUNTDOWN_CANCEL");
                finish("CANCELLED");
            });
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM;
            params.y = 24;
            windows = (WindowManager) getSystemService(WINDOW_SERVICE);
            windows.addView(overlay, params);
            SendCard.show(overlay,
                    ProbeLogic.cardHeadline(payload.kind, payload.name, payload.alias, 8),
                    "8",
                    ProbeLogic.checkLine(payload.expectedTitle),
                    payload.transcript,
                    getString(R.string.cancel));
            return true;
        } catch (RuntimeException e) {
            overlay = null;
            ProbeLog.code("OVERLAY_FAIL");
            finish("OVERLAY_FAIL");
            return false;
        }
    }

    private void decide() {
        if (finished) return;
        Arm.Payload payload = Arm.get();
        if (payload == null || payload.cancelled) {
            finish("CANCELLED");
            return;
        }
        if (!payload.checked) {
            finish("NOT_CHECKED");
            return;
        }
        Observation obs = observe(null);
        publish(obs);
        if (!obs.whatsAppForeground) {
            finish("NOT_FOREGROUND");
            return;
        }
        if (obs.title == null || obs.title.isEmpty()) {
            finish("NO_TITLE");
            return;
        }
        if (!ProbeLogic.titlesMatch(obs.title, payload.expectedTitle)) {
            finish("TITLE_MISMATCH");
            return;
        }
        if (obs.sendNode == null) {
            finish("NO_BUTTON");
            return;
        }
        if (!ProbeLogic.descConfirmsSend(obs.sendDesc)) {
            finish("SEND_UNCONFIRMED");
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

    private void finish(String code) {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(tick);
        Arm.Payload payload = Arm.get();
        if (payload != null) payload.consumed = true;
        Store store = Store.get(this);
        store.putCode("autoResult", code);
        if ("CANCELLED".equals(code) && !"OPENED".equals(store.code("flow", "NONE"))) {
            store.putCode("flow", "CANCELLED");
        }
        ProbeLog.code("AUTO_" + code);
        if (overlay != null && !"CANCELLED".equals(code)) {
            String kind = payload == null ? "" : payload.kind;
            String reason = ProbeLogic.autoReason(code, kind);
            if ("TITLE_MISMATCH".equals(code) && payload != null) {
                String observed = latest == null || latest.title == null ? "—" : latest.title;
                reason = reason + "\nהשם השמור: " + payload.expectedTitle + "\nהכותרת: " + observed;
            }
            SendCard.show(overlay, "PRESSED".equals(code) ? "נלחץ שלח" : "לא נשלח", "", reason,
                    payload == null ? "" : payload.transcript, getString(R.string.close));
            Button cancel = overlay.findViewById(R.id.card_cancel);
            cancel.setOnClickListener(v -> hideOverlay());
            handler.postDelayed(this::hideOverlay, "PRESSED".equals(code) ? 2000 : 7000);
        } else {
            hideOverlay();
        }
        UiHook.ping();
    }

    private void hideOverlay() {
        if (overlay != null && windows != null) {
            try {
                windows.removeView(overlay);
            } catch (RuntimeException ignored) {
                // Already detached.
            }
        }
        overlay = null;
    }

    private void publish(Observation obs) {
        String expected = "";
        Arm.Payload payload = Arm.get();
        if (payload != null && !payload.consumed) expected = payload.expectedTitle;
        else {
            Store.Target target = Store.get(this).target();
            if (target != null) expected = target.name;
        }
        boolean match = ProbeLogic.titlesMatch(obs.title, expected);
        String titleCode = obs.title == null || obs.title.isEmpty() ? "UNKNOWN" : (match ? "YES" : "NO");
        String button = obs.sendFound ? "YES" : "NO";
        String label = obs.sendFound ? ProbeLogic.descCode(obs.sendDesc) : "NONE";
        String sig = titleCode + "|" + button + "|" + label + "|" + (obs.title == null ? "" : obs.title);
        if (sig.equals(lastSig)) return;
        lastSig = sig;
        Store.get(this).setObservation(titleCode, button, label, obs.title);
        UiHook.ping();
    }

    private Observation observe(AccessibilityEvent event) {
        Observation obs = new Observation();
        obs.at = android.os.SystemClock.uptimeMillis();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            CharSequence pkg = root.getPackageName();
            obs.whatsAppForeground = isWhatsApp(pkg);
            if (obs.whatsAppForeground) walk(root, obs);
        }
        if (obs.title == null && event != null
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getText() != null && !event.getText().isEmpty()
                && event.getText().get(0) != null) {
            String windowTitle = event.getText().get(0).toString();
            if (!windowTitle.isEmpty() && windowTitle.length() <= 80) obs.title = windowTitle;
        }
        obs.sendFound = obs.sendNode != null;
        return obs;
    }

    /** Title text is read only from the toolbar id. Other nodes contribute id and description. */
    private void walk(AccessibilityNodeInfo node, Observation obs) {
        if (node == null) return;
        String id = safe(() -> node.getViewIdResourceName());
        String desc = safe(() -> {
            CharSequence value = node.getContentDescription();
            return value == null ? null : value.toString();
        });
        if (obs.title == null && ProbeLogic.idIsTitle(id)) {
            String text = safe(() -> {
                CharSequence value = node.getText();
                return value == null ? null : value.toString();
            });
            if (text == null || text.isEmpty()) text = desc;
            if (text != null && !text.isEmpty()) obs.title = text;
        }
        if (obs.sendNode == null && ProbeLogic.isSendControl(id, desc, node.isClickable())) {
            obs.sendNode = node;
            obs.sendDesc = desc;
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), obs);
    }

    private static String safe(Supplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isWhatsApp(CharSequence pkg) {
        if (pkg == null) return false;
        String value = pkg.toString();
        return Access.WA.equals(value) || Access.W4B.equals(value);
    }

    private interface Supplier {
        String get();
    }

    private static final class Observation {
        long at;
        boolean whatsAppForeground;
        boolean sendFound;
        String title;
        String sendDesc;
        AccessibilityNodeInfo sendNode;
    }
}
