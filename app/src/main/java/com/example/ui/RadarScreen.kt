package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.UiState
import com.example.model.VenueCategory
import com.example.model.WifiSpot
import com.example.model.WifiSpotUiItem
import com.example.util.LocationHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    viewModel: WifiSpotsViewModel,
    isExpandedWidth: Boolean,
    onNavigateToScan: () -> Unit,
    onConnectWifi: (WifiSpot) -> Unit,
    onCopyPassword: (WifiSpot) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.radarUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val userCoords by viewModel.userCoordinates.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(LocationHelper.hasLocationPermission(context))
    }
    var selectedSpotId by remember { mutableStateOf<String?>(null) }
    var editingSpot by remember { mutableStateOf<WifiSpot?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            scope.launch {
                val coords = LocationHelper.fetchCurrentCoordinates(context)
                viewModel.updateUserCoordinates(coords)
                if (coords != null) {
                    viewModel.emitMessage("Standort aktualisiert – sortiert nach Nähe.")
                }
            }
        } else {
            viewModel.emitMessage("Standort verweigert – du kannst über die Suche filtern.")
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && userCoords == null) {
            viewModel.updateUserCoordinates(LocationHelper.fetchCurrentCoordinates(context))
        }
    }

    val itemsList = (uiState as? UiState.Success)?.data.orEmpty()
    val activeDetailItem: WifiSpotUiItem? = remember(itemsList, selectedSpotId) {
        itemsList.firstOrNull { it.spot.id == selectedSpotId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + Location Action
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Freies WLAN in der Nähe",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (uiState is UiState.Success) {
                            "${itemsList.size} geteilte Community-Spots"
                        } else {
                            "Community-Radar wird geladen…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    onClick = {
                        if (hasLocationPermission) {
                            scope.launch {
                                val coords = LocationHelper.fetchCurrentCoordinates(context)
                                viewModel.updateUserCoordinates(coords)
                                viewModel.emitMessage(
                                    if (coords != null) "Standort aktualisiert."
                                    else "Standort momentan nicht verfügbar."
                                )
                            }
                        } else {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                )
                            )
                        }
                    },
                    shape = CircleShape,
                    color = if (userCoords != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.testTag("location_filter_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Standort aktualisieren",
                            tint = if (userCoords != null) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = userCoords?.areaLabel ?: if (hasLocationPermission) "Umkreis" else "Standort",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (userCoords != null) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Suche nach Bar, Café, WLAN-Name oder Stadtteil…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_dialog))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_spots_input"),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("Alle") },
                    )
                }
                items(VenueCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            viewModel.selectCategory(if (selectedCategory == cat) null else cat)
                        },
                        leadingIcon = {
                            Icon(cat.icon(), contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(cat.labelDe) },
                    )
                }
            }
        }

        // Body
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    EmptyRadarState(
                        hasFilter = searchQuery.isNotBlank() || selectedCategory != null,
                        onResetFilter = {
                            viewModel.updateSearchQuery("")
                            viewModel.selectCategory(null)
                        },
                        onScanClick = onNavigateToScan,
                    )
                } else if (isExpandedWidth) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.data, key = { it.spot.id }) { item ->
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

                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight()
                                .padding(bottom = 16.dp),
                        ) {
                            val detailItem = activeDetailItem ?: state.data.first()
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
                                        viewModel.deleteSpot(detailItem.spot.id) {
                                            selectedSpotId = null
                                        }
                                    }
                                } else null,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.data, key = { it.spot.id }) { item ->
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
                }
            }
        }
    }

    if (!isExpandedWidth && activeDetailItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedSpotId = null },
            sheetState = sheetState,
        ) {
            WifiSpotDetailContent(
                item = activeDetailItem,
                onConnectClick = { onConnectWifi(activeDetailItem.spot) },
                onCopyPasswordClick = { onCopyPassword(activeDetailItem.spot) },
                onVoteWorking = { working -> viewModel.voteSpot(activeDetailItem.spot.id, working) },
                onToggleFavorite = { viewModel.toggleFavorite(activeDetailItem.spot.id, activeDetailItem.isFavorite) },
                onEditClick = if (activeDetailItem.isOwnedByCurrentUser) {
                    { editingSpot = activeDetailItem.spot }
                } else null,
                onDeleteClick = if (activeDetailItem.isOwnedByCurrentUser) {
                    {
                        viewModel.deleteSpot(activeDetailItem.spot.id) {
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
                    spotId = spotToEdit.id,
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
private fun EmptyRadarState(
    hasFilter: Boolean,
    onResetFilter: () -> Unit,
    onScanClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WifiFind,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = if (hasFilter) "Keine passenden WLAN-Spots gefunden" else "Noch keine Community-Spots",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (hasFilter) {
                    "Passe deine Suche oder die Kategorie-Filter an."
                } else {
                    "Du bist in einer Bar oder einem Café? Scanne das WLAN-Schild und teile es mit anderen!"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasFilter) {
                Button(onClick = onResetFilter) {
                    Text("Filter zurücksetzen")
                }
            } else {
                Button(onClick = onScanClick) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ersten Spot scannen & teilen")
                }
            }
        }
    }
}
