package com.univote.evoting.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DateFormatsTest {
    @Test fun parse_thenFormat_roundTrips() {
        val millis = DateFormats.parse("2026-12-01 08:30")
        assertNotNull(millis)
        assertEquals("2026-12-01 08:30", DateFormats.format(millis!!))
    }

    @Test fun parse_badText_returnsNull() {
        assertNull(DateFormats.parse("tomorrow"))
        assertNull(DateFormats.parse("2026-13-40 99:99"))
    }
}