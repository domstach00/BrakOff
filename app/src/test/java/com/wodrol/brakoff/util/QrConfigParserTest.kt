package com.wodrol.brakoff.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrConfigParserTest {

    @Test
    fun `parse returns config for valid qr json`() {
        val raw = """
            {
              "server_address": "https://brakoff.mpdwodrol.pl",
              "token": "YWUL-Vjov-uKu4-ag31"
            }
        """.trimIndent()

        val parsed = QrConfigParser.parse(raw)

        requireNotNull(parsed)
        assertEquals("https://brakoff.mpdwodrol.pl", parsed.serverAddress)
        assertEquals("YWUL-Vjov-uKu4-ag31", parsed.token)
    }

    @Test
    fun `parse returns null for invalid qr json`() {
        assertNull(QrConfigParser.parse("not-json"))
    }
}
