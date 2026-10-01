package com.wodrol.brakoff.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {

    fun parseIsoToMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    fun formatIsoToLocalDisplay(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = Instant.parse(isoString)
            val zonedDateTime = instant.atZone(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            zonedDateTime.format(formatter)
        } catch (_: Exception) {
            try {
                val odt = OffsetDateTime.parse(isoString)
                val zonedDateTime = odt.atZoneSameInstant(ZoneId.systemDefault())
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                zonedDateTime.format(formatter)
            } catch (_: Exception) {
                isoString
            }
        }
    }

    fun formatMillisToLocalDisplay(millis: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(millis)
            val zonedDateTime = instant.atZone(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            zonedDateTime.format(formatter)
        } catch (_: Exception) {
            ""
        }
    }

    fun currentIsoUtcString(): String {
        return Instant.now().toString()
    }
}
