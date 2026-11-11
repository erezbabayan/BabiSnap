package com.babisnap.app;

import static com.babisnap.app.Classifier.Kind.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class RouteMarkTest {
    @Test public void eachKindGetsItsRouteGlyph() {
        assertEquals(RouteMark.Route.CALENDAR, RouteMark.of(MEETING));
        assertEquals(RouteMark.Route.BABITK, RouteMark.of(TASK));
        assertEquals(RouteMark.Route.BABITK, RouteMark.of(NOTE));
        assertEquals(RouteMark.Route.WHATSAPP, RouteMark.of(WHATSAPP));
        assertEquals(RouteMark.Route.CALL, RouteMark.of(CALL));
        assertEquals(R.drawable.ic_route_calendar, RouteMark.drawable(RouteMark.Route.CALENDAR));
        assertEquals(R.drawable.ic_route_babitk, RouteMark.drawable(RouteMark.Route.BABITK));
        assertEquals(R.drawable.ic_route_whatsapp, RouteMark.drawable(RouteMark.Route.WHATSAPP));
        assertEquals(R.drawable.ic_route_call, RouteMark.drawable(RouteMark.Route.CALL));
        assertNotEquals(RouteMark.drawable(RouteMark.Route.CALENDAR), RouteMark.drawable(RouteMark.Route.WHATSAPP));
    }
}
