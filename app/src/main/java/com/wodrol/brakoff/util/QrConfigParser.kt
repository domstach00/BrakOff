package com.wodrol.brakoff.util

import com.google.gson.JsonParser

data class QrConnectionConfig(
    val serverAddress: String,
    val token: String
)

object QrConfigParser {
    fun parse(rawValue: String): QrConnectionConfig? {
        return try {
            val json = JsonParser.parseString(rawValue).asJsonObject
            val serverAddress = json.get("server_address")?.asString?.trim().orEmpty()
            val token = json.get("token")?.asString?.trim().orEmpty()
            if (serverAddress.isBlank() || token.isBlank()) {
                null
            } else {
                QrConnectionConfig(
                    serverAddress = serverAddress,
                    token = token
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
