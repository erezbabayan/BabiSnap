package com.babisnap.app;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TagsTest {
    static final List<String> ALL = Tags.merge(null);
    static Tags.Result x(String s) { return Tags.extract(s, ALL); }

    @Test public void fallbackHasEveryBabiTkTagAndMiluim() {
        for (String t : new String[]{"בית", "עבודה", "לימודים", "סטארטאפ", "קודים", "רעיונות", "פיננסי", "משפחה", "קניות", "בריאות", "אישי", "מילואים"})
            assertTrue(t, ALL.contains(t));
    }
    @Test public void explicitCommandStripped() {
        Tags.Result r = x("לשלוח טופס 3010 תתייג מילואים");
        assertEquals(Arrays.asList("מילואים"), r.tags); assertEquals("לשלוח טופס 3010", r.text);
        r = x("לקנות מדים בתגית מילואים מחר");
        assertEquals(Arrays.asList("מילואים"), r.tags); assertEquals("לקנות מדים מחר", r.text);
        r = x("תייג את המילואים להתקשר למפקד");
        assertEquals(Arrays.asList("מילואים"), r.tags); assertEquals("להתקשר למפקד", r.text);
        r = x("להגיש דוח עם התגית של המילואים");
        assertEquals(Arrays.asList("מילואים"), r.tags); assertEquals("להגיש דוח", r.text);
        r = x("לשלם ארנונה, תתייג פיננסי");
        assertEquals(Arrays.asList("פיננסי"), r.tags); assertEquals("לשלם ארנונה", r.text);
    }
    @Test public void naturalWordsTaggedAndKept() {
        Tags.Result r = x("להגיש טופס החזר למילואים");
        assertEquals(Arrays.asList("מילואים"), r.tags); assertEquals("להגיש טופס החזר למילואים", r.text);
        r = x("סנאט מילואים ביום שלישי");
        assertEquals(Arrays.asList("מילואים"), r.tags);
        r = x("רעיון לסטארטאפ ולמשפחה");
        assertTrue(r.tags.containsAll(Arrays.asList("סטארטאפ", "משפחה")));
    }
    @Test public void severalExplicitTags() {
        Tags.Result r = x("לתאם חופש תתייג עבודה ומילואים");
        assertTrue(r.tags.containsAll(Arrays.asList("עבודה", "מילואים")));
        assertFalse(r.text.contains("תתייג"));
    }
    @Test public void noFalsePositives() {
        assertTrue(x("לקנות חלב").tags.isEmpty());
        assertTrue(x("מילואימניק חדש").tags.isEmpty());
        assertEquals("תתייג משהו", x("תתייג משהו").text); // unknown tag: leave as is
    }
    @Test public void cachedUserTagsMerged() {
        List<String> n = Tags.merge(Arrays.asList("#טיולים", " מילואים "));
        assertEquals(Arrays.asList("טיולים"), Tags.extract("לתכנן טיול תתייג טיולים", n).tags);
        assertEquals(1, n.stream().filter("מילואים"::equals).count());
    }
    @Test public void payloadCarriesTags() {
        String j = BabiTkJson.build("t", null, null, null, null, "task", "x", Arrays.asList("מילואים", "#עבודה", "מילואים"));
        assertTrue(j, j.contains("\"tags\":[\"מילואים\",\"עבודה\"]"));
        assertFalse(BabiTkJson.build("t", null, null, null, null, "task", "x").contains("tags"));
    }
}
