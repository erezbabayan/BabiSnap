package com.babisnap.app;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ContactMatcherTest {
    static ContactMatcher.Contact c(String name) { return new ContactMatcher.Contact(name); }

    static final List<ContactMatcher.Contact> BOOK = Arrays.asList(
        c("תמר כהן").field("תמר").field("כהן").org("דוגמה בע\"מ", "מנהלת מוצר").group("עבודה").email("tamar.cohen@example.com"),
        c("תמר לוי").field("תמר").field("לוי").email("tamar.levi@example.net").email("tamar.l@example.org"),
        c("תָּמָר רִי").field("תָּמָר").field("רִי").group("חוג יוגה").email("tamar.ri@example.org"),
        c("תמרה").email("tamara@example.com"),
        c("דני כהן").field("דני").field("כהן").email("dani@example.com"),
        c("דני מהמכולת").field("דני"),                                   // no email
        c("יוסי").field("Yossi").field("שכן מקומה 3").email("yossi@example.com"),
        c("Avi Ben").field("Avi").field("Ben").org("Shivuk Ltd", null).group("שיווק").email("avi@example.com"),
        c("רונית").field("רונית").field("חברה של אמא מהעבודה").email("ronit@example.com"),
        c("מיכאל").field("מיכאל").email("michael@example.com"),
        c("Tamar Cohen (old)").email("TAMAR.COHEN@example.com"));       // same address, other card

    static List<ContactMatcher.Match> find(String q) { return ContactMatcher.find(q, BOOK).matches; }

    @Test public void normalizesNiqqudFinalLettersAndPunctuation() {
        assertEquals("תמר רי", ContactMatcher.norm("תָּמָר רִי"));
        assertEquals("מיכאל", ContactMatcher.norm("מיכאל"));
        assertEquals(ContactMatcher.norm("כהנים"), ContactMatcher.norm("כהנימ"));
        assertEquals(ContactMatcher.norm("בעמ"), ContactMatcher.norm("בע\"מ"));
        assertEquals("tamar cohen example com", ContactMatcher.norm("Tamar.Cohen@Example.com"));
    }

    @Test public void oneNameSeveralMatchesWholeWordOnly() {
        List<ContactMatcher.Match> m = find("תמר");
        assertEquals(3, m.size());                               // תמר כהן (deduped with the old card), תמר לוי, תמר רי (niqqud) – not תמרה
        assertEquals("תמר כהן", m.get(0).name);
        assertEquals("tamar.cohen@example.com", m.get(0).email);
        assertEquals("עבודה · דוגמה בע\"מ", m.get(0).tag);
        assertEquals("tamar.levi@example.net", m.get(1).email); // first address
        assertEquals("חוג יוגה", m.get(2).tag);
    }

    @Test public void allWordsMustMatch() {
        assertEquals(Arrays.asList("tamar.cohen@example.com"), emails("תמר עבודה"));   // group
        assertEquals(Arrays.asList("tamar.cohen@example.com"), emails("תמר מהעבודה")); // marker with prefix מה
        assertEquals(Arrays.asList("tamar.cohen@example.com"), emails("תמר כהן"));     // family name
        assertEquals(Arrays.asList("tamar.cohen@example.com"), emails("תמר דוגמה"));   // company
        assertEquals(Arrays.asList("tamar.cohen@example.com"), emails("תמר מנהלת"));   // job title
        assertEquals(Arrays.asList("tamar.levi@example.net"), emails("תמר לוי"));
        assertEquals(Arrays.asList("tamar.ri@example.org"), emails("תמר יוגה"));
        assertEquals(Arrays.asList("avi@example.com"), emails("Avi מהשיווק"));
        assertEquals(Arrays.asList("yossi@example.com"), emails("יוסי השכן"));          // note/nickname field
        assertEquals(Arrays.asList("ronit@example.com"), emails("רונית"));
        assertTrue(emails("תמר שיווק").isEmpty());
    }

    @Test public void emailFieldIsSearchedToo() {
        assertEquals(Arrays.asList("michael@example.com"), emails("michael"));
    }

    @Test public void contactWithoutEmailIsNotInvitable() {
        ContactMatcher.Result r = ContactMatcher.find("דני מהמכולת", BOOK);
        assertTrue(r.matches.isEmpty());
        assertTrue(r.matchedWithoutEmail);
        assertEquals(Arrays.asList("dani@example.com"), emails("דני"));                // the one with an email only
        ContactMatcher.Result none = ContactMatcher.find("שלומית", BOOK);
        assertTrue(none.matches.isEmpty());
        assertFalse(none.matchedWithoutEmail);
    }

    @Test public void emptyQueryMatchesNothing() {
        assertTrue(find("").isEmpty());
        assertTrue(find("  ").isEmpty());
    }

    static List<String> emails(String q) {
        java.util.ArrayList<String> e = new java.util.ArrayList<>();
        for (ContactMatcher.Match m : find(q)) e.add(m.email);
        return e;
    }

    // v2.6: STT glues a name and a marker ("תמר רי" -> "תמרי")
    @Test public void gluedNameAndMarkerSplits() {
        List<ContactMatcher.Match> m = find("תמרי");
        assertEquals(1, m.size());
        assertEquals("tamar.ri@example.org", m.get(0).email);
    }
    @Test public void gluedNameWithUnknownRemainderShowsAllWithThatName() {
        List<ContactMatcher.Match> m = find("תמרז");
        assertEquals(3, m.size());   // picker: every "תמר" (remainder not a marker of anyone)
    }
    @Test public void gluedNameStillNeedsOtherWords() {
        List<ContactMatcher.Match> m = find("תמרז עבודה");
        assertEquals(1, m.size());
        assertEquals("tamar.cohen@example.com", m.get(0).email);
    }
    @Test public void exactNameWinsOverSplit() {
        assertEquals("tamara@example.com", find("תמרה").get(0).email);
        assertEquals(1, find("תמרה").size());
    }
    @Test public void namePrefixMatch() {
        List<ContactMatcher.Match> m = find("מיכ");
        assertEquals(1, m.size());
        assertEquals("michael@example.com", m.get(0).email);
    }
    @Test public void noMatchStaysEmpty() { assertTrue(find("זאב").isEmpty()); }
}
