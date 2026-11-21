package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.VenueCategory
import com.example.model.WifiSecurityType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.LocationHelper
import com.example.util.ParsedWifiScan
import com.example.util.WifiSignParser
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private enum class CaptureMode(val label: String) {
    CAMERA("Kamera-Scan"),
    MANUAL("Manuell"),
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerAndAddScreen(
    viewModel: WifiSpotsViewModel,
    onSpotSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val existingCoords by viewModel.userCoordinates.collectAsStateWithLifecycle()

    var captureMode by remember { mutableStateOf(CaptureMode.CAMERA) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var scanPaused by remember { mutableStateOf(false) }
    var lastQrScan by remember { mutableStateOf<ParsedWifiScan?>(null) }
    var lastOcrScan by remember { mutableStateOf<ParsedWifiScan?>(null) }

    // Form Fields
    var venueName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(VenueCategory.BAR) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var securityType by remember { mutableStateOf(WifiSecurityType.WPA2) }
    var cityOrArea by remember { mutableStateOf(existingCoords?.areaLabel.orEmpty()) }
    var notes by remember { mutableStateOf("") }
    var sharedPublicly by remember { mutableStateOf(true) }
    var attachLatitude by remember { mutableStateOf(existingCoords?.latitude) }
    var attachLongitude by remember { mutableStateOf(existingCoords?.longitude) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            viewModel.emitMessage("Kamera nicht erlaubt – du kannst ein Foto wählen oder manuell tippen.")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                val coords = LocationHelper.fetchCurrentCoordinates(context)
                if (coords != null) {
                    viewModel.updateUserCoordinates(coords)
                    attachLatitude = coords.latitude
                    attachLongitude = coords.longitude
                    if (cityOrArea.isBlank() && !coords.areaLabel.isNullOrBlank()) {
                        cityOrArea = coords.areaLabel
                    }
                    viewModel.emitMessage("Standort angehängt.")
                }
            }
        } else {
            viewModel.emitMessage("Standortberechtigung nicht erteilt.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = analyzeImageUri(context, uri)
                if (result.fromQrCode) {
                    lastQrScan = result
                    result.ssid?.let { ssid = it }
                    result.password?.let { password = it }
                    securityType = result.securityType
                    viewModel.emitMessage(
                        if (result.hasMissingPasswordWarning) {
                            "QR-Code ohne Passwort erkannt – bitte prüfen!"
                        } else {
                            "QR-Code aus Foto übernommen!"
                        }
                    )
                } else {
                    lastOcrScan = result
                    if (ssid.isBlank() && !result.ssid.isNullOrBlank()) ssid = result.ssid
                    if (password.isBlank() && !result.password.isNullOrBlank()) password = result.password
                    if (result.securityType == WifiSecurityType.OPEN) securityType = WifiSecurityType.OPEN
                    viewModel.emitMessage("Text aus Foto erkannt – prüfe die Vorschläge unten.")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context) && attachLatitude == null) {
            val coords = LocationHelper.fetchCurrentCoordinates(context)
            if (coords != null) {
                viewModel.updateUserCoordinates(coords)
                attachLatitude = coords.latitude
                attachLongitude = coords.longitude
                if (cityOrArea.isBlank() && !coords.areaLabel.isNullOrBlank()) {
                    cityOrArea = coords.areaLabel
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "WLAN erfassen & teilen",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Richte die Kamera auf ein Bar-Schild oder einen QR-Code, wähle ein Foto oder tippe die Daten direkt ein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Mode Switcher + Photo Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    CaptureMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CaptureMode.entries.size,
                            ),
                            selected = captureMode == mode,
                            onClick = { captureMode = mode },
                            icon = {
                                Icon(
                                    imageVector = if (mode == CaptureMode.CAMERA) {
                                        Icons.Filled.QrCodeScanner
                                    } else {
                                        Icons.Filled.Edit
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text(mode.label) },
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("pick_photo_button"),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Foto")
                }
            }

            // Live Camera Scanner Section
            AnimatedVisibility(visible = captureMode == CaptureMode.CAMERA) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(MaterialTheme.shapes.large)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.large,
                                ),
                        ) {
                            CameraWifiAnalyzerView(
                                isPaused = scanPaused,
                                onQrDetected = { parsed ->
                                    lastQrScan = parsed
                                    parsed.ssid?.let { ssid = it }
                                    parsed.password?.let { password = it }
                                    securityType = parsed.securityType
                                    scanPaused = true
                                    viewModel.emitMessage(
                                        if (parsed.hasMissingPasswordWarning) {
                                            "QR-Code ohne Passwort erkannt – bitte prüfen!"
                                        } else {
                                            "WLAN-QR-Code erkannt!"
                                        }
                                    )
                                },
                                onOcrDetected = { parsed ->
                                    lastOcrScan = parsed
                                    if (ssid.isBlank() && !parsed.ssid.isNullOrBlank()) {
                                        ssid = parsed.ssid
                                    }
                                    if (password.isBlank() && !parsed.password.isNullOrBlank()) {
                                        password = parsed.password
                                    }
                                    if (parsed.securityType == WifiSecurityType.OPEN) {
                                        securityType = WifiSecurityType.OPEN
                                    }
                                },
                            )

                            // Reticle & Status Pill
                            Surface(
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = if (scanPaused) "Scan pausiert" else "Suche QR-Code oder Schild-Text…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }

                            Surface(
                                onClick = { scanPaused = !scanPaused },
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (scanPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (scanPaused) "Weiter scannen" else "Einfrieren",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = "Kamera-Scanner aktivieren",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Erlaube den Kamerazugriff, um WLAN-Schilder und QR-Codes in Bars automatisch auszulesen.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                    modifier = Modifier.testTag("grant_camera_button"),
                                ) {
                                    Text("Kamera erlauben")
                                }
                            }
                        }
                    }

                    // QR Hit Banner
                    lastQrScan?.let { qr ->
                        val isWarning = qr.hasMissingPasswordWarning
                        val bannerBg = if (isWarning) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                        val bannerFg = if (isWarning) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = bannerBg,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = bannerFg,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "QR-Code übernommen: ${qr.ssid.orEmpty()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = bannerFg,
                                    )
                                    Text(
                                        text = if (isWarning) {
                                            "Kein Passwort im QR-Code enthalten – bitte Passwort ergänzen oder auf „Offen“ umstellen."
                                        } else {
                                            "Gib nur noch den Ortsnamen ein (z. B. Barname)."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = bannerFg,
                                    )
                                }
                            }
                        }
                    }

                    // OCR Candidates Chips
                    lastOcrScan?.takeIf { it.candidateLines.isNotEmpty() }?.let { ocr ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Erkannte Schild-Texte (antippen zum Übernehmen)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    ocr.candidateLines.forEach { line ->
                                        FilterChip(
                                            selected = line == ssid || line == password,
                                            onClick = {
                                                when {
                                                    ssid.isBlank() -> ssid = line
                                                    password.isBlank() && line != ssid -> password = line
                                                    else -> password = line
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = line,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Spot Confirmation Form
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "WLAN-Details bestätigen",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    OutlinedTextField(
                        value = venueName,
                        onValueChange = { if (it.length <= 80) venueName = it },
                        label = { Text("Name der Bar / des Ortes *") },
                        placeholder = { Text("z. B. Bar Centrale, Café Kiez") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("venue_name_input"),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Kategorie",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            VenueCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    leadingIcon = {
                                        Icon(cat.icon(), contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text(cat.labelDe) },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { if (it.length <= 64) ssid = it },
                        label = { Text("WLAN-Name (SSID) *") },
                        placeholder = { Text("z. B. BarCentrale_Guest") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ssid_input"),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Verschlüsselung",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WifiSecurityType.entries.forEach { sec ->
                                FilterChip(
                                    selected = securityType == sec,
                                    onClick = {
                                        securityType = sec
                                        if (sec == WifiSecurityType.OPEN) password = ""
                                    },
                                    label = { Text(sec.labelDe) },
                                )
                            }
                        }
                    }

                    if (securityType != WifiSecurityType.OPEN) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { if (it.length <= 128) password = it },
                            label = { Text("Passwort") },
                            placeholder = { Text("Passwort vom Schild") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                        )
                    }

                    OutlinedTextField(
                        value = cityOrArea,
                        onValueChange = { if (it.length <= 80) cityOrArea = it },
                        label = { Text("Stadtteil / Straße (optional)") },
                        placeholder = { Text("z. B. Kreuzberg, Berlin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { if (it.length <= 240) notes = it },
                        label = { Text("Hinweis (optional)") },
                        placeholder = { Text("z. B. Schild hängt rechts neben der Kasse") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Attach Location Button
                    OutlinedButton(
                        onClick = {
                            if (LocationHelper.hasLocationPermission(context)) {
                                scope.launch {
                                    val coords = LocationHelper.fetchCurrentCoordinates(context)
                                    if (coords != null) {
                                        viewModel.updateUserCoordinates(coords)
                                        attachLatitude = coords.latitude
                                        attachLongitude = coords.longitude
                                        if (cityOrArea.isBlank() && !coords.areaLabel.isNullOrBlank()) {
                                            cityOrArea = coords.areaLabel
                                        }
                                        viewModel.emitMessage("GPS-Standort übernommen.")
                                    } else {
                                        viewModel.emitMessage("Standort konnte nicht ermittelt werden.")
                                    }
                                }
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (attachLatitude != null) {
                                "GPS-Standort angehängt (aktualisieren)"
                            } else {
                                "Aktuellen Standort für Umkreissuche anhängen"
                            }
                        )
                    }

                    // Auto-Share Switch
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatisch mit Community teilen",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Andere Nutzer in der Nähe sehen diesen Spot sofort.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = sharedPublicly,
                                onCheckedChange = { sharedPublicly = it },
                                modifier = Modifier.testTag("auto_share_switch"),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.createSpot(
                                venueName = venueName,
                                category = category,
                                ssid = ssid,
                                password = password,
                                securityType = securityType,
                                sharedPublicly = sharedPublicly,
                                notes = notes,
                                cityOrArea = cityOrArea,
                                latitude = attachLatitude,
                                longitude = attachLongitude,
                                onSaved = {
                                    venueName = ""
                                    ssid = ""
                                    password = ""
                                    notes = ""
                                    lastQrScan = null
                                    lastOcrScan = null
                                    scanPaused = false
                                    onSpotSaved()
                                },
                            )
                        },
                        enabled = !isSaving && venueName.isNotBlank() && ssid.isNotBlank(),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_spot_button"),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            Text(
                                text = if (sharedPublicly) "Speichern & automatisch teilen" else "Privat speichern",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraWifiAnalyzerView(
    isPaused: Boolean,
    onQrDetected: (ParsedWifiScan) -> Unit,
    onOcrDetected: (ParsedWifiScan) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val textRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val isProcessing = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner, isPaused) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (_: Exception) {
                return@addListener
            }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var lastFrameMs = 0L
            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val now = System.currentTimeMillis()
                if (isPaused || now - lastFrameMs < 550L || !isProcessing.compareAndSet(false, true)) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                lastFrameMs = now

                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    isProcessing.set(false)
                    imageProxy.close()
                    return@setAnalyzer
                }

                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val qrHit = barcodes.firstNotNullOfOrNull { barcode ->
                            barcode.rawValue?.let { WifiSignParser.parseQrPayload(it) }
                        }
                        if (qrHit != null) {
                            mainExecutor.execute { onQrDetected(qrHit) }
                            isProcessing.set(false)
                            imageProxy.close()
                        } else {
                            textRecognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    if (visionText.text.isNotBlank()) {
                                        val parsed = WifiSignParser.parseOcrText(visionText.text)
                                        if (parsed.candidateLines.isNotEmpty()) {
                                            mainExecutor.execute { onOcrDetected(parsed) }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    isProcessing.set(false)
                                    imageProxy.close()
                                }
                        }
                    }
                    .addOnFailureListener {
                        isProcessing.set(false)
                        imageProxy.close()
                    }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (_: Exception) {
                // Camera unavailable on device
            }
        }, mainExecutor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private suspend fun analyzeImageUri(context: Context, uri: android.net.Uri): ParsedWifiScan {
    val inputImage = InputImage.fromFilePath(context, uri)
    val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    return try {
        val barcodes = barcodeScanner.process(inputImage).await()
        val qrHit = barcodes.firstNotNullOfOrNull { barcode ->
            barcode.rawValue?.let { WifiSignParser.parseQrPayload(it) }
        }
        if (qrHit != null) {
            qrHit
        } else {
            val visionText = textRecognizer.process(inputImage).await()
            WifiSignParser.parseOcrText(visionText.text)
        }
    } finally {
        barcodeScanner.close()
        textRecognizer.close()
    }
}
