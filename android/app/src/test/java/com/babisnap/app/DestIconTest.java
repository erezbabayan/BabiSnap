package com.babisnap.app;

import static com.babisnap.app.Classifier.Kind.*;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class DestIconTest {
    @Test public void eachKindGetsItsOwnDestination() {
        assertEquals(DestIcon.Dest.CALENDAR, DestIcon.of(MEETING));
        assertEquals(DestIcon.Dest.WHATSAPP, DestIcon.of(WHATSAPP));
        assertEquals(DestIcon.Dest.CALL, DestIcon.of(CALL));
        assertEquals(DestIcon.Dest.TASK, DestIcon.of(TASK));
        assertEquals(DestIcon.Dest.NOTE, DestIcon.of(NOTE));
    }

    @Test public void fiveDistinctIconsInEveryShape() {
        Set<Integer> tiles = new HashSet<>(), rounds = new HashSet<>(), chips = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (DestIcon.Dest d : DestIcon.Dest.values()) {
            tiles.add(DestIcon.tile(d)); rounds.add(DestIcon.round(d)); chips.add(DestIcon.chip(d)); labels.add(DestIcon.label(d));
        }
        assertEquals(5, DestIcon.Dest.values().length);
        assertEquals(5, tiles.size());
        assertEquals(5, rounds.size());
        assertEquals(5, chips.size());
        assertEquals(5, labels.size());
        tiles.retainAll(rounds);
        assertTrue("tile and round variants are separate drawables", tiles.isEmpty());
    }

    @Test public void resourceMappingIsStable() {
        assertEquals(R.drawable.ic_dest_calendar, DestIcon.tile(DestIcon.Dest.CALENDAR));
        assertEquals(R.drawable.ic_dest_note_round, DestIcon.round(DestIcon.Dest.NOTE));
        assertEquals(R.drawable.chip_dest_task, DestIcon.chip(DestIcon.Dest.TASK));
    }
}
