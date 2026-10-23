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
}
