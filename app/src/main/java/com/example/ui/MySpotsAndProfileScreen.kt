package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOutUser
import com.example.model.UiState
import com.example.model.WifiSpot
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private enum class MySpotsTab(val label: String) {
    SHARED("Meine Spots"),
    FAVORITES("Favoriten"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySpotsAndProfileScreen(
    viewModel: WifiSpotsViewModel,
    onConnectWifi: (WifiSpot) -> Unit,
    onCopyPassword: (WifiSpot) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser = Firebase.auth.currentUser

    val mySpotsState by viewModel.mySpotsUiState.collectAsStateWithLifecycle()
    val favSpotsState by viewModel.favoriteSpotsUiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MySpotsTab.SHARED) }
    var selectedSpotId by remember { mutableStateOf<String?>(null) }
    var editingSpot by remember { mutableStateOf<WifiSpot?>(null) }

    val mySpotsList = (mySpotsState as? UiState.Success)?.data.orEmpty()
    val favSpotsList = (favSpotsState as? UiState.Success)?.data.orEmpty()
    val totalConfirmations = mySpotsList.sumOf { it.workingCount }

    val activeState = if (selectedTab == MySpotsTab.SHARED) mySpotsState else favSpotsState
    val activeList = if (selectedTab == MySpotsTab.SHARED) mySpotsList else favSpotsList
    val detailItem = remember(mySpotsList, favSpotsList, selectedSpotId) {
        (mySpotsList + favSpotsList).firstOrNull { it.spot.id == selectedSpotId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Meine Spots & Profil",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatChipCard(
                    icon = Icons.Filled.Public,
                    value = mySpotsList.size.toString(),
                    label = "Geteilt",
                    modifier = Modifier.weight(1f),
                )
                StatChipCard(
                    icon = Icons.Filled.Favorite,
                    value = favSpotsList.size.toString(),
                    label = "Favoriten",
                    modifier = Modifier.weight(1f),
                )
                StatChipCard(
                    icon = Icons.Filled.ThumbUp,
                    value = totalConfirmations.toString(),
                    label = "Bestätigt",
                    modifier = Modifier.weight(1f),
                )
            }

            // Tab Switcher
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MySpotsTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = MySpotsTab.entries.size,
                        ),
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label) },
                    )
                }
            }

            when (val state = activeState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (activeList.isEmpty()) {
                            item {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = if (selectedTab == MySpotsTab.SHARED) {
                                            "Du hast noch keine eigenen WLAN-Spots geteilt. Tippe unten auf „Scannen“, um deinen ersten Spot zu speichern."
                                        } else {
                                            "Noch keine Favoriten gespeichert. Tippe auf das Herz bei einem WLAN-Spot, um ihn hier abzulegen."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(18.dp),
                                    )
                                }
                            }
                        } else {
                            items(activeList, key = { it.spot.id }) { item ->
                                WifiSpotCard(
                                    item = item,
                                    isSelected = item.spot.id == selectedSpotId,
                                    onClick = { selectedSpotId = item.spot.id },
                                    onConnectClick = { onConnectWifi(item.spot) },
                                    onCopyPasswordClick = { onCopyPassword(item.spot) },
                                    onFavoriteClick = { viewModel.toggleFavorite(item.spot.id, item.isFavorite) },
                                )
                            }
                        }

                        // Account Footer
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(38.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentUser?.displayName ?: "Google-Konto",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text = currentUser?.email.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            signOutUser(
                                                credentialManager = credentialManager,
                                                scope = scope,
                                            )
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier.testTag("sign_out_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.auth_sign_out))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (detailItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedSpotId = null },
            sheetState = sheetState,
        ) {
            WifiSpotDetailContent(
                item = detailItem,
                onConnectClick = { onConnectWifi(detailItem.spot) },
                onCopyPasswordClick = { onCopyPassword(detailItem.spot) },
                onVoteWorking = { working -> viewModel.voteSpot(detailItem.spot.id, working) },
                onToggleFavorite = { viewModel.toggleFavorite(detailItem.spot.id, detailItem.isFavorite) },
                onEditClick = if (detailItem.isOwnedByCurrentUser) {
                    { editingSpot = detailItem.spot }
                } else null,
                onDeleteClick = if (detailItem.isOwnedByCurrentUser) {
                    {
                        viewModel.deleteSpot(detailItem.spot) {
                            selectedSpotId = null
                        }
                    }
                } else null,
            )
        }
    }

    editingSpot?.let { spotToEdit ->
        EditWifiSpotDialog(
            spot = spotToEdit,
            isSaving = isSaving,
            onDismiss = { editingSpot = null },
            onSave = { venueName, category, ssid, password, securityType, sharedPublicly, notes, cityOrArea ->
                viewModel.updateSpot(
                    existingSpot = spotToEdit,
                    venueName = venueName,
                    category = category,
                    ssid = ssid,
                    password = password,
                    securityType = securityType,
                    sharedPublicly = sharedPublicly,
                    notes = notes,
                    cityOrArea = cityOrArea,
                    onUpdated = { editingSpot = null },
                )
            },
        )
    }
}

@Composable
private fun StatChipCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
