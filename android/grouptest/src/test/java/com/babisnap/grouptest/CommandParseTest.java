package com.babisnap.grouptest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CommandParseTest {
    private static final java.util.List<String> TAMRI = Arrays.asList("תמרי", "אמא");

    @Test
    public void messageForAliasBeforeChannel() {
        CommandParse.Result result = CommandParse.parse("הודעה לתמרי בוואטסאפ אני בדרך", TAMRI);
        assertEquals("OK", result.code);
        assertEquals("תמרי", result.label);
        assertEquals("אני בדרך", result.body);
    }

    @Test
    public void channelThenAliasWithColon() {
        CommandParse.Result result = CommandParse.parse("וואטסאפ לתמרי: אני בדרך", TAMRI);
        assertEquals("OK", result.code);
        assertEquals("תמרי", result.label);
        assertEquals("אני בדרך", result.body);
    }

    @Test
    public void elPrefixAndShinOnBody() {
        CommandParse.Result result = CommandParse.parse("וואטסאפ אל תמרי אני מאחר", TAMRI);
        assertEquals("OK", result.code);
        assertEquals("תמרי", result.label);
        assertEquals("אני מאחר", result.body);

        CommandParse.Result shin = CommandParse.parse("הודעה לאמא בוואטסאפ שאני בדרך", TAMRI);
        assertEquals("OK", shin.code);
        assertEquals("אמא", shin.label);
        assertEquals("אני בדרך", shin.body);
    }

    @Test
    public void keepsShalomAndDropsEmptyOrMissingRecipient() {
        CommandParse.Result greeting = CommandParse.parse("וואטסאפ לתמרי: שלום", TAMRI);
        assertEquals("שלום", greeting.body);

        assertEquals("NONE", CommandParse.parse("וואטסאפ אני בדרך", TAMRI).code);
        assertEquals("EMPTY_BODY", CommandParse.parse("הודעה לתמרי בוואטסאפ", TAMRI).code);
        assertEquals("NONE", CommandParse.parse("הודעה לתמרי אני בדרך", TAMRI).code);
        assertNull(CommandParse.parse(null, TAMRI).label);
    }

    @Test
    public void longestAliasWins() {
        CommandParse.Result result = CommandParse.parse(
                "וואטסאפ לתמרי כהן אני בדרך",
                Arrays.asList("תמרי", "תמרי כהן"));
        assertEquals("OK", result.code);
        assertEquals("תמרי כהן", result.label);
        assertEquals("אני בדרך", result.body);
    }

    @Test
    public void englishChannelWordAndPhoneNormalization() {
        CommandParse.Result result = CommandParse.parse("WhatsApp לתמרי: היי", Collections.singletonList("תמרי"));
        assertEquals("OK", result.code);
        assertEquals("היי", result.body);
        assertEquals("+972501234567", ProbeLogic.toIlE164("0501234567"));
        assertTrue(ProbeLogic.toIlE164("050-123-4567").startsWith("+9725"));
        assertTrue(ProbeLogic.textContains("אני בדרך ", "אני בדרך"));
        assertTrue(ProbeLogic.idIsEntry("com.whatsapp:id/entry"));
    }
}
