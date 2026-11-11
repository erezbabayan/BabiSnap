package com.babisnap.app;

/**
 * Group send opens WhatsApp's own forward picker: recent chats and groups, with WhatsApp's search.
 * No system share sheet, no chooser, no phone, no jid.
 */
public final class WhatsAppGroupShare {
    public static final String ACTION = "android.intent.action.SEND";
    public static final String TYPE = "text/plain";

    public static final class Spec {
        public final String action;
        public final String type;
        /** com.whatsapp, else com.whatsapp.w4b. Null when neither app is installed. */
        public final String pkg;
        public final String text;
        public final boolean includeJid;
        public final boolean systemChooser;

        Spec(String action, String type, String pkg, String text, boolean includeJid, boolean systemChooser) {
            this.action = action;
            this.type = type;
            this.pkg = pkg;
            this.text = text == null ? "" : text;
            this.includeJid = includeJid;
            this.systemChooser = systemChooser;
        }

        public boolean opensWhatsAppPicker() {
            return ACTION.equals(action) && TYPE.equals(type) && pkg != null
                && (WhatsAppLogic.PKG.equals(pkg) || WhatsAppLogic.PKG_BUSINESS.equals(pkg))
                && !includeJid && !systemChooser;
        }
    }

    /** Consumer app first. Never a chooser and never a jid. */
    public static Spec spec(boolean consumerInstalled, boolean businessInstalled, String text) {
        return new Spec(ACTION, TYPE, WhatsAppLogic.packageName(consumerInstalled, businessInstalled), text, false, false);
    }

    private WhatsAppGroupShare() {}
}
