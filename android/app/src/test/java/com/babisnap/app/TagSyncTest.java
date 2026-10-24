package com.babisnap.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TagSyncTest {
    static final long H = 60L * 60 * 1000;
    @Test public void parsesDisplayOrder() {
        List<String> n = TagSync.parse("{\"tags\":[{\"name\":\"טיולים\",\"color\":\"#ff0000\"},{\"name\":\" #עבודה \",\"color\":\"blue\"},{\"name\":\"טיולים\",\"color\":\"x\"}]}");
        assertEquals(Arrays.asList("טיולים", "עבודה"), n);
    }
    @Test public void parsesEmptyAndSkipsBad() {
        assertEquals(Arrays.asList(), TagSync.parse("{\"tags\":[]}"));
        assertEquals(Arrays.asList("a"), TagSync.parse("{\"tags\":[{\"color\":\"x\"},{\"name\":\"\"},{\"name\":\"a\"},\"\"]}"));
        StringBuilder longName = new StringBuilder(); for (int i = 0; i < 41; i++) longName.append('x');
        assertEquals(Arrays.asList(), TagSync.parse("{\"tags\":[{\"name\":\"" + longName + "\"}]}"));
    }
    @Test public void badBodyIsNullSoCacheIsKept() {
        assertNull(TagSync.parse("{\"error\":\"unauthorized\"}"));
        assertNull(TagSync.parse("not json"));
        assertNull(TagSync.parse(null));
        assertNull(TagSync.parse("{\"tags\":\"x\"}"));
    }
    @Test public void throttle12h() {
        long t0 = 1_000_000_000L;
        assertTrue(TagSync.due(0, 0, t0, false)); // never fetched
        long next = TagSync.nextAfter(200, t0);
        assertEquals(t0 + 12 * H, next);
        assertFalse(TagSync.due(next, t0, t0 + 11 * H, false));
        assertTrue(TagSync.due(next, t0, t0 + 12 * H, false));
        assertTrue(TagSync.due(next, t0, t0 + H, true)); // pairing forces
        assertEquals(t0 + 12 * H, TagSync.nextAfter(401, t0));
        assertEquals(t0 + 12 * H, TagSync.nextAfter(429, t0));
        assertEquals(t0 + H, TagSync.nextAfter(-1, t0)); // network error retries sooner
        assertTrue(TagSync.due(next, t0, t0 - H, false)); // clock moved back
    }
    @Test public void userTagsFirstThenFallback() {
        List<String> m = Tags.merge(TagSync.parse("{\"tags\":[{\"name\":\"טיולים\",\"color\":\"g\"},{\"name\":\"עבודה\",\"color\":\"b\"}]}"));
        assertEquals("טיולים", m.get(0)); assertEquals("עבודה", m.get(1));
        assertEquals(1, m.stream().filter("עבודה"::equals).count());
        assertTrue(m.containsAll(Tags.FALLBACK));
        assertEquals(Tags.FALLBACK, Tags.merge(null));
    }
}
