package com.example.util

import com.example.model.WifiSecurityType

data class ParsedWifiScan(
    val ssid: String? = null,
    val password: String? = null,
    val securityType: WifiSecurityType = WifiSecurityType.WPA2,
    val candidateLines: List<String> = emptyList(),
    val fromQrCode: Boolean = false,
    val hasMissingPasswordWarning: Boolean = false,
)

object WifiSignParser {
    private val ssidKeywords = listOf(
        "ssid", "wlan", "wi-fi", "wifi", "netzwerk", "network", "name", "hotspot"
    )
    private val passwordKeywords = listOf(
        "passwort", "password", "pass", "pw", "kennwort", "schlüssel", "schluessel", "key", "code", "pin"
    )
    private val openKeywords = listOf(
        "kein passwort", "no password", "ohne passwort", "offen", "free", "open"
    )

    fun parseQrPayload(raw: String): ParsedWifiScan? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("WIFI:", ignoreCase = true)) return null
        val body = trimmed.substring(5)
        val tokens = splitEscaped(body, ';')

        var ssid: String? = null
        var password = ""
        var security = WifiSecurityType.WPA2

        for (token in tokens) {
            if (token.isBlank()) continue
            val colonIdx = token.indexOf(':')
            if (colonIdx <= 0) continue
            val key = token.substring(0, colonIdx).uppercase()
            val value = unescapeWifiValue(token.substring(colonIdx + 1))
            when (key) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> security = when (value.uppercase()) {
                    "NOPASS", "NONE", "" -> WifiSecurityType.OPEN
                    "WEP" -> WifiSecurityType.WEP
                    "SAE", "WPA3" -> WifiSecurityType.WPA3
                    else -> WifiSecurityType.WPA2
                }
            }
        }

        if (ssid.isNullOrBlank()) return null
        val missingPassword = password.isBlank() && security != WifiSecurityType.OPEN
        return ParsedWifiScan(
            ssid = ssid.trim(),
            password = password,
            securityType = security,
            candidateLines = listOfNotNull(ssid.trim(), password.takeIf { it.isNotBlank() }),
            fromQrCode = true,
            hasMissingPasswordWarning = missingPassword,
        )
    }

    fun parseOcrText(rawText: String): ParsedWifiScan {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        var detectedSsid: String? = null
        var detectedPassword: String? = null
        var detectedSecurity = WifiSecurityType.WPA2

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()

            if (openKeywords.any { lower == it || lower.contains("kein passwort") || lower.contains("ohne passwort") }) {
                detectedSecurity = WifiSecurityType.OPEN
            }

            val inlinePair = extractKeyValue(line)
            if (inlinePair != null) {
                val (label, value) = inlinePair
                val labelLower = label.lowercase()
                if (detectedSsid == null && ssidKeywords.any { labelLower.contains(it) }) {
                    if (value.isNotBlank()) {
                        detectedSsid = cleanCandidate(value)
                    } else if (i + 1 < lines.size) {
                        detectedSsid = cleanCandidate(lines[i + 1])
                    }
                    continue
                }
                if (detectedPassword == null && passwordKeywords.any { labelLower.contains(it) }) {
                    if (value.isNotBlank()) {
                        detectedPassword = cleanCandidate(value)
                    } else if (i + 1 < lines.size) {
                        detectedPassword = cleanCandidate(lines[i + 1])
                    }
                    continue
                }
            }

            // Label on current line without colon, value on next line
            val strippedLabel = lower.trimEnd(':', '=', ' ')
            if (detectedSsid == null && ssidKeywords.any { strippedLabel == it } && i + 1 < lines.size) {
                detectedSsid = cleanCandidate(lines[i + 1])
            } else if (detectedPassword == null && passwordKeywords.any { strippedLabel == it } && i + 1 < lines.size) {
                detectedPassword = cleanCandidate(lines[i + 1])
            }
        }

        if (detectedPassword == null && detectedSecurity == WifiSecurityType.OPEN) {
            detectedPassword = ""
        }

        val candidates = lines
            .flatMap { line ->
                val pair = extractKeyValue(line)
                if (pair != null && pair.second.isNotBlank()) {
                    listOf(cleanCandidate(pair.second), line)
                } else {
                    listOf(line)
                }
            }
            .map { it.trim() }
            .filter { it.length in 2..64 }
            .distinct()
            .take(12)

        return ParsedWifiScan(
            ssid = detectedSsid,
            password = detectedPassword,
            securityType = detectedSecurity,
            candidateLines = candidates,
            fromQrCode = false,
        )
    }

    private fun extractKeyValue(line: String): Pair<String, String>? {
        val sepIndex = line.indexOfFirst { it == ':' || it == '=' }
        if (sepIndex <= 0) return null
        val key = line.substring(0, sepIndex).trim()
        val value = line.substring(sepIndex + 1).trim()
        return key to value
    }

    private fun cleanCandidate(raw: String): String =
        raw.trim().trim('"', '\'', '„', '“', '”')

    private fun splitEscaped(input: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                current.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == delimiter) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun unescapeWifiValue(value: String): String {
        val out = StringBuilder()
        var escaped = false
        for (ch in value) {
            if (escaped) {
                out.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }
}
