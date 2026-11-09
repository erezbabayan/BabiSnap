package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Arrays;

public class EventJsonTest {
    @Test public void attendeesPatchBody() {
        assertEquals("{\"attendees\":[{\"email\":\"tamar.cohen@example.com\"},{\"email\":\"yossi@example.com\"}]}",
            EventJson.attendees(Arrays.asList("tamar.cohen@example.com", "yossi@example.com")));
        assertEquals("{\"attendees\":[{\"email\":\"a\\\"b@example.com\"}]}", EventJson.attendees(Arrays.asList("a\"b@example.com")));
    }

    @Test public void babisnapIdInBody() {
        String body = EventJson.build("פגישה", "2026-11-10 10:00", 60, "", "desc", "bsnap-01234567-89ab-cdef-0123-456789abcdef");
        assertTrue(body.contains("\"id\":"));
        assertTrue(body.contains("babisnapId"));
        assertTrue(body.contains("extendedProperties"));
        String plain = EventJson.build("פגישה", "2026-11-10 10:00", 60, "", "desc");
        assertFalse(plain.contains("extendedProperties"));
    }
}
