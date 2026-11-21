package com.example.data

import android.content.Context
import com.example.R
import com.example.model.VenueCategory
import com.example.model.SpotFeedback
import com.example.model.WifiSecurityType
import com.example.model.WifiSpot
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WifiSpotRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Bitte zuerst mit Google anmelden.")

    private fun contributorDisplayLabel(): String {
        val raw = auth.currentUser?.displayName?.trim().orEmpty()
        val firstWord = raw.substringBefore(' ').trim()
        return firstWord.ifBlank { "Community-Gast" }.take(40)
    }

    fun observeCommunitySpots(currentUserId: String): Flow<List<WifiSpot>> = flow {
        require(currentUserId.isNotBlank()) { "Ungültige Benutzer-ID" }
        val path = COLLECTION_WIFI_SPOTS
        emitAll(
            db.collection(path)
                .whereEqualTo("sharedPublicly", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WifiSpot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMySpots(userId: String): Flow<List<WifiSpot>> = flow {
        require(userId.isNotBlank()) { "Ungültige Benutzer-ID" }
        val path = COLLECTION_WIFI_SPOTS
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WifiSpot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicFeedback(currentUserId: String): Flow<List<SpotFeedback>> = flow {
        require(currentUserId.isNotBlank()) { "Ungültige Benutzer-ID" }
        val path = COLLECTION_SPOT_FEEDBACK
        emitAll(
            db.collection(path)
                .whereEqualTo("publicFeedback", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SpotFeedback::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFavoriteSpotIds(userId: String): Flow<Set<String>> = flow {
        require(userId.isNotBlank()) { "Ungültige Benutzer-ID" }
        val path = "users/$userId/favorites"
        emitAll(
            db.collection("users").document(userId).collection("favorites")
                .snapshots()
                .map { snapshot -> snapshot.documents.map { it.id }.toSet() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createWifiSpot(
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
    ): Result<String> {
        var docPath = COLLECTION_WIFI_SPOTS
        return try {
            val uid = requireUserId()
            val cleanVenue = venueName.trim().take(80)
            val cleanSsid = ssid.trim().take(64)
            val cleanPassword = if (securityType == WifiSecurityType.OPEN) "" else password.trim().take(128)
            val cleanNotes = notes?.trim()?.take(240)?.takeIf { it.isNotEmpty() }
            val cleanArea = cityOrArea?.trim()?.take(80)?.takeIf { it.isNotEmpty() }

            require(cleanVenue.isNotEmpty()) { "Bitte einen Ortsnamen (z. B. Barname) eingeben." }
            require(cleanSsid.isNotEmpty()) { "Bitte den WLAN-Namen (SSID) eingeben." }
            if (latitude != null) require(latitude in -90.0..90.0) { "Ungültiger Breitengrad." }
            if (longitude != null) require(longitude in -180.0..180.0) { "Ungültiger Längengrad." }

            val docRef = db.collection(COLLECTION_WIFI_SPOTS).document()
            docPath = docRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "contributorLabel" to contributorDisplayLabel(),
                "venueName" to cleanVenue,
                "venueCategory" to category.name,
                "ssid" to cleanSsid,
                "password" to cleanPassword,
                "securityType" to securityType.name,
                "sharedPublicly" to sharedPublicly,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (cleanNotes != null) payload["notes"] = cleanNotes
            if (cleanArea != null) payload["cityOrArea"] = cleanArea
            if (latitude != null && longitude != null) {
                payload["latitude"] = latitude
                payload["longitude"] = longitude
            }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateWifiSpot(
        spotId: String,
        venueName: String,
        category: VenueCategory,
        ssid: String,
        password: String,
        securityType: WifiSecurityType,
        sharedPublicly: Boolean,
        notes: String?,
        cityOrArea: String?,
    ): Result<Unit> {
        var docPath = "$COLLECTION_WIFI_SPOTS/$spotId"
        return try {
            requireUserId()
            require(spotId.isNotBlank()) { "Ungültige Spot-ID." }
            val cleanVenue = venueName.trim().take(80)
            val cleanSsid = ssid.trim().take(64)
            val cleanPassword = if (securityType == WifiSecurityType.OPEN) "" else password.trim().take(128)
            val cleanNotes = notes?.trim()?.take(240) ?: ""
            val cleanArea = cityOrArea?.trim()?.take(80) ?: ""

            require(cleanVenue.isNotEmpty()) { "Bitte einen Ortsnamen eingeben." }
            require(cleanSsid.isNotEmpty()) { "Bitte den WLAN-Namen (SSID) eingeben." }

            val docRef = db.collection(COLLECTION_WIFI_SPOTS).document(spotId)
            docPath = docRef.path

            val updates = mapOf<String, Any>(
                "contributorLabel" to contributorDisplayLabel(),
                "venueName" to cleanVenue,
                "venueCategory" to category.name,
                "ssid" to cleanSsid,
                "password" to cleanPassword,
                "securityType" to securityType.name,
                "sharedPublicly" to sharedPublicly,
                "notes" to cleanNotes,
                "cityOrArea" to cleanArea,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWifiSpot(spotId: String): Result<Unit> {
        val docPath = "$COLLECTION_WIFI_SPOTS/$spotId"
        return try {
            requireUserId()
            require(spotId.isNotBlank()) { "Ungültige Spot-ID." }
            db.collection(COLLECTION_WIFI_SPOTS).document(spotId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitSpotFeedback(spotId: String, working: Boolean): Result<Unit> {
        var docPath = COLLECTION_SPOT_FEEDBACK
        return try {
            val uid = requireUserId()
            require(spotId.isNotBlank()) { "Ungültige Spot-ID." }
            val feedbackId = "${spotId}_${uid}"
            val docRef = db.collection(COLLECTION_SPOT_FEEDBACK).document(feedbackId)
            docPath = docRef.path

            val existingSnapshot = docRef.get().await()
            if (existingSnapshot.exists()) {
                docRef.update(
                    mapOf(
                        "working" to working,
                        "publicFeedback" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "spotId" to spotId,
                        "userId" to uid,
                        "working" to working,
                        "publicFeedback" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setFavorite(spotId: String, isFavorite: Boolean): Result<Unit> {
        var docPath = "users/unknown/favorites/$spotId"
        return try {
            val uid = requireUserId()
            require(spotId.isNotBlank()) { "Ungültige Spot-ID." }
            val docRef = db.collection("users").document(uid)
                .collection("favorites").document(spotId)
            docPath = docRef.path

            if (isFavorite) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "spotId" to spotId,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_WIFI_SPOTS = "wifi_spots"
        const val COLLECTION_SPOT_FEEDBACK = "spot_feedback"
    }
}
