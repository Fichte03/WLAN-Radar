package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.data.WifiSpotRepository
import com.example.model.WifiSpot
import com.example.ui.MySpotsAndProfileScreen
import com.example.ui.RadarScreen
import com.example.ui.ScannerAndAddScreen
import com.example.ui.WifiSpotsViewModel
import com.example.ui.theme.WlanRadarTheme
import com.example.util.WifiConnectOutcome
import com.example.util.WifiConnector
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class AppDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    RADAR(R.string.nav_discover, Icons.Filled.WifiFind, Icons.Outlined.WifiFind, "nav_radar"),
    SCAN(R.string.nav_scan, Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner, "nav_scan"),
    MY_SPOTS(R.string.nav_my_spots, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_my_spots"),
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            WlanRadarTheme {
                AppNavigation(windowSizeClass = windowSizeClass)
            }
        }
    }
}

@Composable
fun AppNavigation(windowSizeClass: WindowSizeClass) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            WlanRadarMainShell(
                currentUserId = user.uid,
                windowSizeClass = windowSizeClass,
            )
        }
    }
}

@Composable
private fun WlanRadarMainShell(
    currentUserId: String,
    windowSizeClass: WindowSizeClass,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: WifiSpotsViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                WifiSpotsViewModel(
                    repository = WifiSpotRepository(db),
                    currentUserId = currentUserId,
                )
            }
        },
    )

    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.RADAR) }

    BackHandler(enabled = currentDestination != AppDestination.RADAR) {
        currentDestination = AppDestination.RADAR
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val wifiAddLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val message = if (result.resultCode == Activity.RESULT_OK) {
            "WLAN in Android gespeichert – Verbindung wird hergestellt!"
        } else {
            "WLAN-Dialog geschlossen. Passwort ist in der Zwischenablage."
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val handleConnectWifi: (WifiSpot) -> Unit = { spot ->
        when (val outcome = WifiConnector.prepareConnection(context, spot)) {
            is WifiConnectOutcome.LaunchAddNetworksIntent -> {
                try {
                    wifiAddLauncher.launch(outcome.intent)
                } catch (_: Exception) {
                    viewModel.emitMessage(
                        if (outcome.passwordCopied) {
                            "Passwort kopiert – wähle „${spot.ssid}“ in den WLAN-Einstellungen."
                        } else {
                            "Bitte wähle „${spot.ssid}“ in den WLAN-Einstellungen."
                        }
                    )
                }
            }
            is WifiConnectOutcome.SuggestionRegistered -> {
                try {
                    context.startActivity(outcome.openSettingsIntent)
                } catch (_: Exception) {
                }
                viewModel.emitMessage(
                    if (outcome.passwordCopied) {
                        "WLAN vorgeschlagen & Passwort kopiert!"
                    } else {
                        "WLAN für automatische Verbindung vorgeschlagen!"
                    }
                )
            }
            is WifiConnectOutcome.FallbackToSettings -> {
                try {
                    context.startActivity(outcome.settingsIntent)
                } catch (_: Exception) {
                }
                viewModel.emitMessage(
                    if (outcome.passwordCopied) {
                        "Passwort kopiert – einfach in den WLAN-Einstellungen einfügen."
                    } else {
                        "Öffne WLAN-Einstellungen für „${spot.ssid}“."
                    }
                )
            }
        }
    }

    val handleCopyPassword: (WifiSpot) -> Unit = { spot ->
        val copied = WifiConnector.copyPasswordToClipboard(context, spot.password)
        viewModel.emitMessage(
            if (copied) "Passwort für „${spot.ssid}“ kopiert!"
            else "Dieses WLAN hat kein Passwort."
        )
    }

    val useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val isExpandedWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!useRail) {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    AppDestination.entries.forEach { dest ->
                        val selected = currentDestination == dest
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentDestination = dest },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes),
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.testTag),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (useRail) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    AppDestination.entries.forEach { dest ->
                        val selected = currentDestination == dest
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentDestination = dest },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes),
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.testTag),
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (currentDestination) {
                    AppDestination.RADAR -> RadarScreen(
                        viewModel = viewModel,
                        isExpandedWidth = isExpandedWidth,
                        onNavigateToScan = { currentDestination = AppDestination.SCAN },
                        onConnectWifi = handleConnectWifi,
                        onCopyPassword = handleCopyPassword,
                    )

                    AppDestination.SCAN -> ScannerAndAddScreen(
                        viewModel = viewModel,
                        onSpotSaved = { currentDestination = AppDestination.RADAR },
                    )

                    AppDestination.MY_SPOTS -> MySpotsAndProfileScreen(
                        viewModel = viewModel,
                        onConnectWifi = handleConnectWifi,
                        onCopyPassword = handleCopyPassword,
                    )
                }
            }
        }
    }
}
