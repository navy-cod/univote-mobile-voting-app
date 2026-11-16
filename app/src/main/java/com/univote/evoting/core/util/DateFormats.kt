package com.univote.evoting.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateFormats {
    const val PATTERN = "yyyy-MM-dd HH:mm"
    private val formatter = DateTimeFormatter.ofPattern(PATTERN)

    fun format(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)

    /** Returns null when the text is not in [PATTERN]. */
    fun parse(text: String): Long? = try {
        LocalDateTime.parse(text.trim(), formatter)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}