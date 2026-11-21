package com.example

import com.example.model.WifiSecurityType
import com.example.util.WifiSignParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WifiSignParserTest {

    @Test
    fun parseQrPayload_parsesWpaAndEscapedCharacters() {
        val parsed = WifiSignParser.parseQrPayload("WIFI:T:WPA;S:Bar\\;Centrale;P:Prost\\:2026;;")
        assertNotNull(parsed)
        assertEquals("Bar;Centrale", parsed?.ssid)
        assertEquals("Prost:2026", parsed?.password)
        assertEquals(WifiSecurityType.WPA2, parsed?.securityType)
    }

    @Test
    fun parseQrPayload_parsesOpenNetwork() {
        val parsed = WifiSignParser.parseQrPayload("WIFI:T:nopass;S:Cafe_Free;;")
        assertNotNull(parsed)
        assertEquals("Cafe_Free", parsed?.ssid)
        assertEquals("", parsed?.password)
        assertEquals(WifiSecurityType.OPEN, parsed?.securityType)
    }

    @Test
    fun parseQrPayload_returnsNullForNonWifiQr() {
        assertNull(WifiSignParser.parseQrPayload("https://example.com"))
    }

    @Test
    fun parseOcrText_extractsGermanBarSignInline() {
        val ocr = """
            FREE WIFI
            WLAN: Bar_Zum_Anker
            Passwort: „KiezBier2026!“
        """.trimIndent()
        val parsed = WifiSignParser.parseOcrText(ocr)
        assertEquals("Bar_Zum_Anker", parsed.ssid)
        assertEquals("KiezBier2026!", parsed.password)
        assertEquals(WifiSecurityType.WPA2, parsed.securityType)
    }

    @Test
    fun parseQrPayload_warnsWhenEncryptedNetworkMissingPassword() {
        val parsed = WifiSignParser.parseQrPayload("WIFI:T:WPA;S:Bar_Missing_Pw;;")
        assertNotNull(parsed)
        assertEquals("Bar_Missing_Pw", parsed?.ssid)
        assertEquals("", parsed?.password)
        assertEquals(WifiSecurityType.WPA2, parsed?.securityType)
        assertEquals(true, parsed?.hasMissingPasswordWarning)
    }

    @Test
    fun parseOcrText_extractsMultiLineLabels() {
        val ocr = """
            Netzwerk:
            Rösterei_Gast
            PW:
            Espresso99
        """.trimIndent()
        val parsed = WifiSignParser.parseOcrText(ocr)
        assertEquals("Rösterei_Gast", parsed.ssid)
        assertEquals("Espresso99", parsed.password)
    }
}
