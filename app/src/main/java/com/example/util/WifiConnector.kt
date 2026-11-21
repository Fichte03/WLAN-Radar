package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.WifiSecurityType
import com.example.model.WifiSpot
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

sealed interface WifiConnectOutcome {
    data class LaunchAddNetworksIntent(val intent: Intent, val passwordCopied: Boolean) : WifiConnectOutcome
    data class SuggestionRegistered(val openSettingsIntent: Intent, val passwordCopied: Boolean) : WifiConnectOutcome
    data class FallbackToSettings(val settingsIntent: Intent, val passwordCopied: Boolean) : WifiConnectOutcome
}

object WifiConnector {

    fun copyPasswordToClipboard(context: Context, password: String): Boolean {
        if (password.isBlank()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newPlainText("WLAN-Passwort", password)
        clipboard.setPrimaryClip(clip)
        return true
    }

    fun prepareConnection(context: Context, spot: WifiSpot): WifiConnectOutcome {
        val security = spot.safeSecurityType()
        val copied = if (security != WifiSecurityType.OPEN && spot.password.isNotBlank()) {
            copyPasswordToClipboard(context, spot.password)
        } else {
            false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val suggestion = buildSuggestion(spot.ssid, spot.password, security)
            val bundle = Bundle().apply {
                putParcelableArrayList(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
            }
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                putExtras(bundle)
            }
            return WifiConnectOutcome.LaunchAddNetworksIntent(intent, copied)
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val suggestion = buildSuggestion(spot.ssid, spot.password, security)
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val status = wifiManager?.addNetworkSuggestions(listOf(suggestion))
            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
            return if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                WifiConnectOutcome.SuggestionRegistered(panelIntent, copied)
            } else {
                WifiConnectOutcome.FallbackToSettings(panelIntent, copied)
            }
        }

        return WifiConnectOutcome.FallbackToSettings(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            copied,
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildSuggestion(
        ssid: String,
        password: String,
        security: WifiSecurityType,
    ): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
        when (security) {
            WifiSecurityType.OPEN -> Unit
            WifiSecurityType.WPA3 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && password.isNotBlank()) {
                    builder.setWpa3Passphrase(password)
                }
            }
            WifiSecurityType.WPA2, WifiSecurityType.WEP -> {
                if (password.isNotBlank()) {
                    builder.setWpa2Passphrase(password)
                }
            }
        }
        return builder.build()
    }

    fun buildWifiQrPayload(spot: WifiSpot): String {
        val sec = spot.safeSecurityType()
        val escapedSsid = escapeQr(spot.ssid)
        val escapedPass = escapeQr(spot.password)
        return if (sec == WifiSecurityType.OPEN || spot.password.isBlank()) {
            "WIFI:T:nopass;S:$escapedSsid;;"
        } else {
            "WIFI:T:${sec.qrToken};S:$escapedSsid;P:$escapedPass;;"
        }
    }

    fun generateQrBitmap(payload: String, sizePx: Int = 512): ImageBitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeQr(raw: String): String =
        raw.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
}
