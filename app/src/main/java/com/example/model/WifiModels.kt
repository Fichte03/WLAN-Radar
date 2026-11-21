package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class VenueCategory(val labelDe: String) {
    BAR("Bar"),
    CAFE("Café"),
    RESTAURANT("Restaurant"),
    HOTEL("Hotel"),
    PUBLIC("Öffentlich"),
    OTHER("Sonstiges");

    companion object {
        fun fromRaw(raw: String?): VenueCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

enum class WifiSecurityType(val labelDe: String, val qrToken: String) {
    WPA2("WPA2", "WPA"),
    WPA3("WPA3", "WPA"),
    WEP("WEP", "WEP"),
    OPEN("Offen", "nopass");

    companion object {
        fun fromRaw(raw: String?): WifiSecurityType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WPA2
    }
}

@Keep
@IgnoreExtraProperties
data class WifiSpot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val contributorLabel: String = "",
    val venueName: String = "",
    val venueCategory: String = VenueCategory.BAR.name,
    val ssid: String = "",
    val password: String = "",
    val securityType: String = WifiSecurityType.WPA2.name,
    val sharedPublicly: Boolean = true,
    val notes: String? = null,
    val cityOrArea: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun safeCategory(): VenueCategory = VenueCategory.fromRaw(venueCategory)
    fun safeSecurityType(): WifiSecurityType = WifiSecurityType.fromRaw(securityType)
}

@Keep
@IgnoreExtraProperties
data class SpotFeedback(
    @DocumentId val id: String = "",
    val spotId: String = "",
    val userId: String = "",
    val working: Boolean = true,
    val publicFeedback: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FavoriteSpot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val spotId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class WifiSpotUiItem(
    val spot: WifiSpot,
    val workingCount: Int,
    val brokenCount: Int,
    val userVoteWorking: Boolean?,
    val isFavorite: Boolean,
    val distanceMeters: Float?,
    val isOwnedByCurrentUser: Boolean,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
