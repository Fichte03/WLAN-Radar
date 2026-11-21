package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WifiSpotRepository
import com.example.model.SpotFeedback
import com.example.model.UiState
import com.example.model.VenueCategory
import com.example.model.WifiSecurityType
import com.example.model.WifiSpot
import com.example.model.WifiSpotUiItem
import com.example.util.LocationHelper
import com.example.util.UserCoordinates
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WifiSpotsViewModel(
    private val repository: WifiSpotRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<VenueCategory?>(null)
    val selectedCategory: StateFlow<VenueCategory?> = _selectedCategory.asStateFlow()

    private val _userCoordinates = MutableStateFlow<UserCoordinates?>(null)
    val userCoordinates: StateFlow<UserCoordinates?> = _userCoordinates.asStateFlow()

    private val _sortByDistance = MutableStateFlow(true)
    val sortByDistance: StateFlow<Boolean> = _sortByDistance.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val communitySpotsResult: StateFlow<UiState<List<WifiSpot>>> =
        repository.observeCommunitySpots(currentUserId)
            .map<List<WifiSpot>, UiState<List<WifiSpot>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "Öffentliche Spots konnten nicht geladen werden.")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    private val mySpotsResult: StateFlow<UiState<List<WifiSpot>>> =
        repository.observeMySpots(currentUserId)
            .map<List<WifiSpot>, UiState<List<WifiSpot>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "Eigene Spots konnten nicht geladen werden.")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    private val feedbackState: StateFlow<List<SpotFeedback>> =
        repository.observePublicFeedback(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Feedback stream error", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList(),
            )

    private val favoriteIdsState: StateFlow<Set<String>> =
        repository.observeFavoriteSpotIds(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Favorites stream error", firestoreError ?: error)
                emit(emptySet())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptySet(),
            )

    private val filterParams = combine(
        _searchQuery,
        _selectedCategory,
        _userCoordinates,
        _sortByDistance,
    ) { query, category, coords, byDistance ->
        FilterParams(query, category, coords, byDistance)
    }

    val radarUiState: StateFlow<UiState<List<WifiSpotUiItem>>> = combine(
        communitySpotsResult,
        feedbackState,
        favoriteIdsState,
        filterParams,
    ) { spotsState, feedbacks, favoriteIds, params ->
        when (spotsState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> spotsState
            is UiState.Success -> {
                val items = buildUiItems(
                    spots = spotsState.data,
                    feedbacks = feedbacks,
                    favoriteIds = favoriteIds,
                    coords = params.coords,
                ).filter { item ->
                    val matchesCategory = params.category == null || item.spot.safeCategory() == params.category
                    val q = params.query.trim().lowercase()
                    val matchesQuery = q.isEmpty() ||
                        item.spot.venueName.lowercase().contains(q) ||
                        item.spot.ssid.lowercase().contains(q) ||
                        (item.spot.cityOrArea?.lowercase()?.contains(q) == true) ||
                        (item.spot.notes?.lowercase()?.contains(q) == true)
                    matchesCategory && matchesQuery
                }.let { list ->
                    if (params.sortByDistance && params.coords != null) {
                        list.sortedWith(
                            compareBy<WifiSpotUiItem> { it.distanceMeters ?: Float.MAX_VALUE }
                                .thenByDescending { it.workingCount - it.brokenCount }
                                .thenByDescending { it.spot.createdAt ?: Timestamp(0, 0) }
                        )
                    } else {
                        list.sortedByDescending { it.spot.createdAt ?: Timestamp(0, 0) }
                    }
                }
                UiState.Success(items)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
    )

    val mySpotsUiState: StateFlow<UiState<List<WifiSpotUiItem>>> = combine(
        mySpotsResult,
        feedbackState,
        favoriteIdsState,
        _userCoordinates,
    ) { spotsState, feedbacks, favoriteIds, coords ->
        when (spotsState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> spotsState
            is UiState.Success -> {
                val items = buildUiItems(spotsState.data, feedbacks, favoriteIds, coords)
                    .sortedByDescending { it.spot.createdAt ?: Timestamp(0, 0) }
                UiState.Success(items)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
    )

    val favoriteSpotsUiState: StateFlow<UiState<List<WifiSpotUiItem>>> = combine(
        communitySpotsResult,
        mySpotsResult,
        feedbackState,
        favoriteIdsState,
        _userCoordinates,
    ) { communityState, myState, feedbacks, favoriteIds, coords ->
        val merged = buildMap<String, WifiSpot> {
            if (communityState is UiState.Success) {
                communityState.data.forEach { put(it.id, it) }
            }
            if (myState is UiState.Success) {
                myState.data.forEach { put(it.id, it) }
            }
        }.values.filter { it.id in favoriteIds }

        when {
            communityState is UiState.Loading && myState is UiState.Loading -> UiState.Loading
            communityState is UiState.Error && myState is UiState.Error -> communityState
            else -> {
                val items = buildUiItems(merged, feedbacks, favoriteIds, coords)
                    .sortedByDescending { it.spot.createdAt ?: Timestamp(0, 0) }
                UiState.Success(items)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: VenueCategory?) {
        _selectedCategory.value = category
    }

    fun updateUserCoordinates(coords: UserCoordinates?) {
        _userCoordinates.value = coords
    }

    fun toggleSortByDistance() {
        _sortByDistance.value = !_sortByDistance.value
    }

    fun emitMessage(message: String) {
        _events.tryEmit(message)
    }

    fun createSpot(
        venueName: String,
        category: VenueCategory,
        ssid: String,
        password: String,
        securityType: WifiSecurityType,
        sharedPublicly: Boolean,
        notes: String?,
        cityOrArea: String?,
        latitude: Double?,
        longitude: Double?,
        onSaved: () -> Unit,
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createWifiSpot(
                venueName = venueName,
                category = category,
                ssid = ssid,
                password = password,
                securityType = securityType,
                sharedPublicly = sharedPublicly,
                notes = notes,
                cityOrArea = cityOrArea,
                latitude = latitude,
                longitude = longitude,
            )
            _isSaving.value = false
            result.onSuccess {
                val msg = if (sharedPublicly) {
                    "WLAN gespeichert & automatisch mit der Community geteilt!"
                } else {
                    "WLAN privat gespeichert."
                }
                _events.tryEmit(msg)
                onSaved()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Speichern fehlgeschlagen.")
            }
        }
    }

    fun updateSpot(
        existingSpot: WifiSpot,
        venueName: String,
        category: VenueCategory,
        ssid: String,
        password: String,
        securityType: WifiSecurityType,
        sharedPublicly: Boolean,
        notes: String?,
        cityOrArea: String?,
        onUpdated: () -> Unit,
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateWifiSpot(
                existingSpot = existingSpot,
                venueName = venueName,
                category = category,
                ssid = ssid,
                password = password,
                securityType = securityType,
                sharedPublicly = sharedPublicly,
                notes = notes,
                cityOrArea = cityOrArea,
            )
            _isSaving.value = false
            result.onSuccess {
                _events.tryEmit("WLAN-Spot aktualisiert.")
                onUpdated()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Aktualisierung fehlgeschlagen.")
            }
        }
    }

    fun deleteSpot(spot: WifiSpot, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteWifiSpot(spot)
            result.onSuccess {
                _events.tryEmit("WLAN-Spot gelöscht.")
                onDeleted()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Löschen fehlgeschlagen.")
            }
        }
    }

    fun voteSpot(spotId: String, working: Boolean) {
        viewModelScope.launch {
            val result = repository.submitSpotFeedback(spotId, working)
            result.onSuccess {
                val msg = if (working) {
                    "Danke! Als funktionierend bestätigt."
                } else {
                    "Hinweis gespeichert: Als veraltet markiert."
                }
                _events.tryEmit(msg)
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Bewertung fehlgeschlagen.")
            }
        }
    }

    fun toggleFavorite(spotId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            val result = repository.setFavorite(spotId, !currentlyFavorite)
            result.onSuccess {
                _events.tryEmit(if (!currentlyFavorite) "Zu Favoriten hinzugefügt." else "Aus Favoriten entfernt.")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Favorit konnte nicht geändert werden.")
            }
        }
    }

    private fun buildUiItems(
        spots: List<WifiSpot>,
        feedbacks: List<SpotFeedback>,
        favoriteIds: Set<String>,
        coords: UserCoordinates?,
    ): List<WifiSpotUiItem> {
        val feedbackBySpot = feedbacks.groupBy { it.spotId }
        return spots.map { spot ->
            val votes = feedbackBySpot[spot.id].orEmpty()
            val workingCount = votes.count { it.working }
            val brokenCount = votes.count { !it.working }
            val userVote = votes.firstOrNull { it.userId == currentUserId }?.working
            val distance = if (coords != null && spot.latitude != null && spot.longitude != null) {
                LocationHelper.distanceMeters(
                    coords.latitude,
                    coords.longitude,
                    spot.latitude,
                    spot.longitude,
                )
            } else {
                null
            }
            WifiSpotUiItem(
                spot = spot,
                workingCount = workingCount,
                brokenCount = brokenCount,
                userVoteWorking = userVote,
                isFavorite = spot.id in favoriteIds,
                distanceMeters = distance,
                isOwnedByCurrentUser = spot.userId == currentUserId,
            )
        }
    }

    private fun unwrapFirestoreError(error: Throwable, fallbackMessage: String): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream failed", root)
        return UiState.Error(root.localizedMessage ?: fallbackMessage)
    }

    private data class FilterParams(
        val query: String,
        val category: VenueCategory?,
        val coords: UserCoordinates?,
        val sortByDistance: Boolean,
    )

    private companion object {
        const val TAG = "WifiSpotsVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
