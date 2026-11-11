package com.babisnap.app;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Picker order, search, and the rule that a row tap is the only selection. */
public class ContactPickTest {
    private static WhatsAppRecipients.Contact person(String display, String given, String phone) {
        return new WhatsAppRecipients.Contact(display, given, "", "",
            Arrays.asList(new WhatsAppRecipients.Phone(phone, false, true)));
    }

    private final WhatsAppRecipients.Contact rivi = person("ריבי חברה תמר", "ריבי", "0501111111");
    private final WhatsAppRecipients.Contact ohad = person("אוהד תמר", "אוהד", "0522222222");
    private final WhatsAppRecipients.Contact tamara = person("תמרה לוי", "תמרה", "0533333333");
    private final WhatsAppRecipients.Contact tamarDavid = person("תמר דוד", "תמר", "0501234567");
    private final WhatsAppRecipients.Contact tamar = person("תמר", "תמר", "0544444444");
    private final List<WhatsAppRecipients.Contact> book = Arrays.asList(rivi, ohad, tamara, tamarDavid, tamar);

    @Test public void exactFirstOrFullNameThenPrefixThenTheRest() {
        List<WhatsAppRecipients.Contact> shown = ContactPick.present("תמר", book, "");
        assertEquals(Arrays.asList(rivi, ohad, tamarDavid, tamar, tamara), shown);
    }

    @Test public void niqqudAndSpacesStillCountAsTheSpokenName() {
        List<WhatsAppRecipients.Contact> shown = ContactPick.present("תָּמָ ר", book, "");
        assertTrue(shown.indexOf(tamarDavid) < shown.indexOf(tamara));
        assertTrue(shown.indexOf(tamar) < shown.indexOf(tamara));
        assertSame(rivi, shown.get(0));
    }

    @Test public void searchFiltersByNameOrNumberWithoutDroppingWeakerNames() {
        assertEquals(book.size(), ContactPick.present("תמר", book, "תמר").size());
        assertEquals(Arrays.asList(ohad), ContactPick.present("תמר", book, "אוהד"));
        assertEquals(Arrays.asList(tamarDavid), ContactPick.present("תמר", book, "050-123"));
        assertEquals(Arrays.asList(tamarDavid), ContactPick.present("תמר", book, "972501234567"));
        assertTrue(ContactPick.present("תמר", book, "אין כזה").isEmpty());
    }

    @Test public void searchIgnoresNiqqudAndSpaces() {
        List<WhatsAppRecipients.Contact> shown = ContactPick.present("תמר", book, "תָּמָ ר");
        assertTrue(shown.contains(tamarDavid));
        assertTrue(shown.contains(tamar));
        assertFalse(shown.contains(ohad) && shown.size() == 1);
        assertTrue(shown.contains(ohad));
    }

    @Test public void aTapSelectsThatRowAndNothingElseActs() {
        List<WhatsAppRecipients.Contact> shown = ContactPick.present("תמר", book, "אוהד");
        assertSame(ohad, ContactPick.picked(shown, 0));
        assertNull(ContactPick.picked(shown, -1));
        assertNull(ContactPick.picked(shown, 1));
        assertNull(ContactPick.picked(null, 0));
        assertFalse(ContactPick.autoActs());
    }

    @Test public void bothPickersShareOneListAndTheDialogCannotEatTheTap() throws Exception {
        String views = text("src/main/java/com/babisnap/app/InviteViews.java");
        assertTrue(views.contains("requestDisallowInterceptTouchEvent(true)"));
        assertTrue(views.contains("FOCUS_BLOCK_DESCENDANTS"));
        assertTrue(views.contains("setFocusable(true)"));
        assertTrue(views.contains("setFocusable(false)"));
        assertTrue(views.contains("\"ביטול\""));
        assertTrue(views.contains("\"חיפוש איש קשר\""));
        assertTrue(views.contains("setMinHeight(dp(c, 48))"));
        String voice = text("src/main/java/com/babisnap/app/VoiceActivity.java");
        assertEquals(2, count(voice, "InviteViews.peoplePicker"));
        assertFalse(voice.contains("choiceList"));
        assertTrue(voice.contains("setFinishOnTouchOutside(false)"));
        assertTrue(voice.contains("SOFT_INPUT_STATE_HIDDEN"));
        int callPicker = voice.indexOf("private void showCallPicker");
        int callConfirm = voice.indexOf("private void showCallConfirm");
        assertTrue(callPicker > 0 && callConfirm > callPicker);
        String callBody = voice.substring(callPicker, callConfirm);
        assertTrue(callBody.contains("callConfirming = false"));
        assertTrue(callBody.contains("removeCallbacks(callExpire)"));
        assertFalse(callBody.contains("postDelayed"));
        int waPicker = voice.indexOf("private void showWaChooser");
        int waDirect = voice.indexOf("private void showWaDirect");
        assertTrue(waPicker > 0 && waDirect > waPicker);
        String waBody = voice.substring(waPicker, waDirect);
        assertTrue(waBody.contains("waConfirming = false"));
        assertTrue(waBody.contains("removeCallbacks(waExpire)"));
        assertFalse(waBody.contains("postDelayed"));
        int paint = voice.indexOf("private void paintCallCard");
        int onCall = voice.indexOf("private void onCallAction");
        assertTrue(paint > 0 && onCall > paint);
        String card = voice.substring(paint, onCall);
        assertTrue(card.contains("InviteViews.roundAction"));
        assertTrue(card.contains("InviteViews.DIAL_GREEN"));
        assertTrue(card.contains("InviteViews.CANCEL_RED"));
        assertTrue(card.contains("R.drawable.ic_dial_answer"));
        assertTrue(card.contains("R.drawable.ic_dial_hangup"));
        assertTrue(card.contains("new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)"));
        assertTrue(card.contains("undo.setVisibility(View.GONE)"));
        assertTrue(card.contains("onCallAction(action)"));
        assertTrue(card.contains("cancelCall(CallDial.CANCELLED)"));
        assertFalse(card.contains("pill_purple"));
        assertTrue(views.contains("CIRCLE_DP = 72"));
        assertTrue(views.contains("setContentDescription(label)"));
        String waCard = voice.substring(voice.indexOf("private void paintWaCard"), voice.indexOf("private void openGroupShare"));
        assertFalse(waCard.contains("roundAction"));
        assertTrue(voice.contains("EXTRA_MAX_RESULTS"));
        assertTrue(voice.contains("SilenceDetector.hypotheses"));
        assertTrue(voice.contains("CallDial.matchNames"));
        assertTrue(voice.contains("שיחה · בחירת איש קשר"));
    }

    @Test public void nearNamesSortAfterTheExactOneAndUseIsOnlyOrder() {
        WhatsAppRecipients.Contact roni = person("רוני", "רוני", "0522222222");
        WhatsAppRecipients.Contact rani = person("רני", "רני", "0501111111");
        WhatsAppRecipients.Contact yossi = person("יוסי", "יוסי", "0533333333");
        List<WhatsAppRecipients.Contact> shown = ContactPick.present("רני", Arrays.asList(yossi, roni, rani), "");
        assertEquals(Arrays.asList(rani, roni, yossi), shown);
        WhatsAppRecipients.Contact often = new WhatsAppRecipients.Contact("רוני", "רוני", "", "",
            Arrays.asList(new WhatsAppRecipients.Phone("0501234567", false, true)), 9, 50L);
        WhatsAppRecipients.Contact rare = new WhatsAppRecipients.Contact("רוני לוי", "רוני", "לוי", "",
            Arrays.asList(new WhatsAppRecipients.Phone("0521111111", false, true)), 1, 10L);
        List<WhatsAppRecipients.Contact> used = ContactPick.present("רוני", Arrays.asList(rare, often), "");
        assertSame(often, used.get(0));
        assertFalse(ContactPick.autoActs());
    }

    private static int count(String hay, String needle) {
        int n = 0, at = 0;
        while ((at = hay.indexOf(needle, at)) >= 0) { n++; at += needle.length(); }
        return n;
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
