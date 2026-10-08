package com.wodrol.brakoff.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtils {

    fun parseIsoToMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val normalized = normalizeTz(isoString)
            Instant.parse(normalized).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                    sdf.parse(isoString)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            }
        }
    }

    fun formatIsoToLocalDisplay(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val normalized = normalizeTz(isoString)
            val instant = Instant.parse(normalized)
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
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                    val date = sdf.parse(isoString)
                    if (date != null) {
                        val sdfOut = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        return sdfOut.format(date)
                    }
                } catch (_: Exception) {}
                isoString
            }
        }
    }

    fun formatIsoToShortDisplay(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val normalized = normalizeTz(isoString)
            val instant = Instant.parse(normalized)
            val zonedDateTime = instant.atZone(ZoneId.systemDefault())
            val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            zonedDateTime.format(formatter)
        } catch (_: Exception) {
            try {
                val odt = OffsetDateTime.parse(isoString)
                val zonedDateTime = odt.atZoneSameInstant(ZoneId.systemDefault())
                val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
                zonedDateTime.format(formatter)
            } catch (_: Exception) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                    val date = sdf.parse(isoString)
                    if (date != null) {
                        val cal = Calendar.getInstance().apply { time = date }
                        val month = String.format("%02d", cal.get(Calendar.MONTH) + 1)
                        val day = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
                        val hour = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
                        val minute = String.format("%02d", cal.get(Calendar.MINUTE))
                        return "$month-$day $hour:$minute"
                    }
                } catch (_: Exception) {}
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

    private fun normalizeTz(isoString: String): String {
        // Jeśli strefa czasowa ma format +0000 lub -0000 (bez dwukropka), zamieniamy na +00:00
        return if (isoString.matches(Regex(".*[+-]\\d{4}$"))) {
            isoString.substring(0, isoString.length - 2) + ":" + isoString.takeLast(2)
        } else {
            isoString
        }
    }
}
