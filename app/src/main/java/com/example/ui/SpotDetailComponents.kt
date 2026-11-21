package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.VenueCategory
import com.example.model.WifiSecurityType
import com.example.model.WifiSpot
import com.example.model.WifiSpotUiItem
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.LocationHelper
import com.example.util.WifiConnector

fun VenueCategory.icon(): ImageVector = when (this) {
    VenueCategory.BAR -> Icons.Filled.LocalBar
    VenueCategory.CAFE -> Icons.Filled.Coffee
    VenueCategory.RESTAURANT -> Icons.Filled.Restaurant
    VenueCategory.HOTEL -> Icons.Filled.Hotel
    VenueCategory.PUBLIC -> Icons.Filled.Public
    VenueCategory.OTHER -> Icons.Filled.Wifi
}

@Composable
fun WifiSpotCard(
    item: WifiSpotUiItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onConnectClick: () -> Unit,
    onCopyPasswordClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spot = item.spot
    val category = spot.safeCategory()
    val security = spot.safeSecurityType()
    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("spot_card_${spot.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = category.labelDe,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spot.venueName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = category.labelDe,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        val locationSubtitle = buildString {
                            item.distanceMeters?.let { append(LocationHelper.formatDistanceGerman(it)) }
                            val area = spot.cityOrArea?.takeIf { it.isNotBlank() }
                            if (area != null) {
                                if (isNotEmpty()) append(" · ")
                                append(area)
                            }
                        }
                        if (locationSubtitle.isNotEmpty()) {
                            Text(
                                text = "· $locationSubtitle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_toggle_favorite),
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // SSID & Verification badges
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (security == WifiSecurityType.OPEN) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = security.labelDe,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = spot.ssid,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    VerificationPill(
                        workingCount = item.workingCount,
                        brokenCount = item.brokenCount,
                        sharedPublicly = spot.sharedPublicly,
                    )
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onConnectClick,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Verbinden", style = MaterialTheme.typography.labelLarge)
                }

                if (security != WifiSecurityType.OPEN && spot.password.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCopyPasswordClick,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Passwort", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun VerificationPill(
    workingCount: Int,
    brokenCount: Int,
    sharedPublicly: Boolean,
) {
    val (bg, fg, icon, label) = when {
        !sharedPublicly -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Lock,
            "Privat",
        )
        brokenCount > workingCount && brokenCount > 0 -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.Warning,
            "$brokenCount× veraltet",
        )
        workingCount > 0 -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.CheckCircle,
            "$workingCount× bestätigt",
        )
        else -> Quad(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Filled.Public,
            "Neu geteilt",
        )
    }

    Surface(shape = CircleShape, color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun WifiSpotDetailContent(
    item: WifiSpotUiItem,
    onConnectClick: () -> Unit,
    onCopyPasswordClick: () -> Unit,
    onVoteWorking: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spot = item.spot
    val security = spot.safeSecurityType()
    val category = spot.safeCategory()
    var showPassword by remember(spot.id) { mutableStateOf(true) }
    var showQr by remember(spot.id) { mutableStateOf(false) }
    var confirmDelete by remember(spot.id) { mutableStateOf(false) }

    val qrBitmap = remember(spot.id, spot.ssid, spot.password, spot.securityType) {
        if (showQr) {
            WifiConnector.generateQrBitmap(WifiConnector.buildWifiQrPayload(spot))
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon(),
                    contentDescription = category.labelDe,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spot.venueName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(category.labelDe)
                        if (!spot.cityOrArea.isNullOrBlank()) append(" · ${spot.cityOrArea}")
                        item.distanceMeters?.let { append(" · ${LocationHelper.formatDistanceGerman(it)}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_toggle_favorite),
                    tint = if (item.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Credentials Box
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NETZWERKNAME (SSID)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = spot.ssid,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = security.labelDe,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                if (security != WifiSecurityType.OPEN && spot.password.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PASSWORT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (showPassword) spot.password else "•".repeat(spot.password.length.coerceIn(6, 16)),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password),
                            )
                        }
                        IconButton(
                            onClick = onCopyPasswordClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_password_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_password),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Offenes WLAN ohne Passwort",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!spot.notes.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = spot.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Primary Connect Button
        Button(
            onClick = onConnectClick,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("connect_wifi_button"),
        ) {
            Icon(imageVector = Icons.Filled.Wifi, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Direkt über Android verbinden",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // Share QR Code Toggle
        OutlinedButton(
            onClick = { showQr = !showQr },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(imageVector = Icons.Filled.QrCode2, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (showQr) "QR-Code ausblenden" else "QR-Code zum Teilen anzeigen")
        }

        AnimatedVisibility(visible = showQr && qrBitmap != null) {
            qrBitmap?.let { bmp ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = stringResource(R.string.cd_wifi_qr_code),
                            modifier = Modifier
                                .size(190.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Text(
                            text = "Freunde können diesen Code direkt mit der Kamera scannen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Community Verification Section (only for public community spots)
        if (spot.sharedPublicly) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Geteilt von ${spot.contributorLabel} · Community-Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = item.userVoteWorking == true,
                        onClick = { onVoteWorking(true) },
                        label = { Text("Funktioniert (${item.workingCount})") },
                        leadingIcon = {
                            Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = item.userVoteWorking == false,
                        onClick = { onVoteWorking(false) },
                        label = { Text("Veraltet (${item.brokenCount})") },
                        leadingIcon = {
                            Icon(Icons.Filled.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Text(
                text = "Privater Spot – nur für dich sichtbar.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Owner Actions
        if (item.isOwnedByCurrentUser && (onEditClick != null || onDeleteClick != null)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onEditClick != null) {
                    OutlinedButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bearbeiten")
                    }
                }
                if (onDeleteClick != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Löschen")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (confirmDelete && onDeleteClick != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("WLAN-Spot löschen?") },
            text = { Text("Möchtest du „${spot.venueName}“ dauerhaft aus der Community entfernen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWifiSpotDialog(
    spot: WifiSpot,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        venueName: String,
        category: VenueCategory,
        ssid: String,
        password: String,
        securityType: WifiSecurityType,
        sharedPublicly: Boolean,
        notes: String?,
        cityOrArea: String?,
    ) -> Unit,
) {
    var venueName by remember(spot.id) { mutableStateOf(spot.venueName) }
    var category by remember(spot.id) { mutableStateOf(spot.safeCategory()) }
    var ssid by remember(spot.id) { mutableStateOf(spot.ssid) }
    var password by remember(spot.id) { mutableStateOf(spot.password) }
    var securityType by remember(spot.id) { mutableStateOf(spot.safeSecurityType()) }
    var sharedPublicly by remember(spot.id) { mutableStateOf(spot.sharedPublicly) }
    var cityOrArea by remember(spot.id) { mutableStateOf(spot.cityOrArea.orEmpty()) }
    var notes by remember(spot.id) { mutableStateOf(spot.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spot bearbeiten") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = venueName,
                    onValueChange = { if (it.length <= 80) venueName = it },
                    label = { Text("Ortsname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VenueCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.labelDe) },
                        )
                    }
                }
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { if (it.length <= 64) ssid = it },
                    label = { Text("WLAN-Name (SSID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WifiSecurityType.entries.forEach { sec ->
                        FilterChip(
                            selected = securityType == sec,
                            onClick = { securityType = sec },
                            label = { Text(sec.labelDe) },
                        )
                    }
                }
                if (securityType != WifiSecurityType.OPEN) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { if (it.length <= 128) password = it },
                        label = { Text("Passwort") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = cityOrArea,
                    onValueChange = { if (it.length <= 80) cityOrArea = it },
                    label = { Text("Stadtteil / Ort") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 240) notes = it },
                    label = { Text("Hinweis (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Mit Community teilen", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = sharedPublicly, onCheckedChange = { sharedPublicly = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && venueName.isNotBlank() && ssid.isNotBlank(),
                onClick = {
                    onSave(venueName, category, ssid, password, securityType, sharedPublicly, notes, cityOrArea)
                },
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}
