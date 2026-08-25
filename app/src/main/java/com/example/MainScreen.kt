package com.example

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isWideScreen = config.screenWidthDp >= 720 || config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // ViewModel Stateflows
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val latitude by viewModel.latitude.collectAsStateWithLifecycle()
    val longitude by viewModel.longitude.collectAsStateWithLifecycle()
    val locationName by viewModel.locationName.collectAsStateWithLifecycle()
    val currentDateStr by viewModel.currentDateString.collectAsStateWithLifecycle()
    val isManualDateSelected by viewModel.isManualDateSelected.collectAsStateWithLifecycle()

    val sunriseOverride by viewModel.sunriseOverride.collectAsStateWithLifecycle()
    val sunsetOverride by viewModel.sunsetOverride.collectAsStateWithLifecycle()
    val tomorrowSunriseOverride by viewModel.tomorrowSunriseOverride.collectAsStateWithLifecycle()

    val calcResults by viewModel.calculationResults.collectAsStateWithLifecycle()
    val currentTimeSec by viewModel.currentTimeSeconds.collectAsStateWithLifecycle()
    val curPlanetaryHour by viewModel.currentPlanetaryHour.collectAsStateWithLifecycle()
    val curTattva by viewModel.currentTattva.collectAsStateWithLifecycle()
    val curCombined by viewModel.currentCombined.collectAsStateWithLifecycle()

    val planetFilters by viewModel.activePlanetFilters.collectAsStateWithLifecycle()
    val tattvaFilters by viewModel.activeTattvaFilters.collectAsStateWithLifecycle()
    val tattvaMode by viewModel.tattvaDisplayMode.collectAsStateWithLifecycle()

    val logsList by viewModel.allLogs.collectAsStateWithLifecycle()
    val isLocating by viewModel.isLocating.collectAsStateWithLifecycle()

    // Screen Interactive states
    var showSettingsState by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }
    var showLogLevelsState by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Android 13+ Notification Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Shift notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
            viewModel.setNotificationsEnabled(false)
        }
    }

    // Interactive custom location inputs
    var inputLat by remember(latitude) { mutableStateOf(String.format(Locale.US, "%.5f", latitude)) }
    var inputLon by remember(longitude) { mutableStateOf(String.format(Locale.US, "%.5f", longitude)) }
    var inputLocName by remember(locationName) { mutableStateOf(locationName) }

    // Android Location Permission Launcher using FusedLocationProviderClient
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "Acquiring GPS coordinates...", Toast.LENGTH_SHORT).show()
            viewModel.acquireCurrentLocation { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location permission was denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Dedicated Panel 1: Cosmic Settings & Modifiers
    if (showSettingsState) {
        CosmicSettingsBottomSheet(
            darkTheme = darkTheme,
            notificationsEnabled = notificationsEnabled,
            hapticsEnabled = hapticsEnabled,
            inputLocName = locationName,
            inputLat = String.format(Locale.US, "%.5f", latitude),
            inputLon = String.format(Locale.US, "%.5f", longitude),
            isLocating = isLocating,
            sunriseOverride = sunriseOverride,
            sunsetOverride = sunsetOverride,
            tomorrowSunriseOverride = tomorrowSunriseOverride,
            onDarkThemeChange = { viewModel.setDarkTheme(it) },
            onNotificationsChange = {
                viewModel.setNotificationsEnabled(it)
                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onHapticsChange = { viewModel.setHapticsEnabled(it) },
            onUpdateLocation = { lat, lon, name ->
                viewModel.updateLocation(lat, lon, name)
            },
            onAutoDetectRequest = {
                if (viewModel.hasLocationPermission()) {
                    viewModel.acquireCurrentLocation { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onSunriseOverrideChange = { viewModel.manualOverrideSunrise(it, 0) },
            onSunsetOverrideChange = { viewModel.manualOverrideSunrise(it, 1) },
            onTomorrowSunriseOverrideChange = { viewModel.manualOverrideSunrise(it, 2) },
            onDismiss = { showSettingsState = false }
        )
    }

    // Dedicated Panel 2: Export to Local CSV
    if (showExportDialog) {
        ExportCsvBottomSheet(
            darkTheme = darkTheme,
            logsCount = logsList.size,
            currentDateStr = currentDateStr,
            onDismiss = { showExportDialog = false },
            onExportLogs = { saveLocally ->
                viewModel.exportShiftLogsCsv(context, saveLocally) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                showExportDialog = false
            },
            onExportCycles = { saveLocally ->
                viewModel.exportTrackedCyclesCsv(context, saveLocally) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                showExportDialog = false
            },
            onExportComplete = { saveLocally ->
                viewModel.exportCompleteHistoryAndCyclesCsv(context, saveLocally) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                showExportDialog = false
            }
        )
    }

    // Dedicated Panel 3: Recorded Shift Logs
    if (showLogLevelsState) {
        RecordedShiftLogsBottomSheet(
            darkTheme = darkTheme,
            logsList = logsList,
            onDeleteLog = { viewModel.deleteLog(it) },
            onClearLogs = { viewModel.clearLogs() },
            onOpenExport = {
                showLogLevelsState = false
                showExportDialog = true
            },
            onDismiss = { showLogLevelsState = false }
        )
    }

    if (showDatePickerDialog) {
        val dateParts = currentDateStr.split("-")
        val initialYear = dateParts.getOrNull(0)?.toIntOrNull() ?: 2026
        val initialMonth = (dateParts.getOrNull(1)?.toIntOrNull() ?: 6) - 1
        val initialDay = dateParts.getOrNull(2)?.toIntOrNull() ?: 1

        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                viewModel.setDateString(formatted, isManual = true)
                showDatePickerDialog = false
            },
            initialYear,
            initialMonth,
            initialDay
        )
        DisposableEffect(Unit) {
            datePickerDialog.setOnDismissListener { showDatePickerDialog = false }
            datePickerDialog.show()
            onDispose {
                datePickerDialog.dismiss()
            }
        }
    }

    // Generates star twinkles purely inside Compose Canvas background
    val starsPositions = remember {
        List(110) {
            Offset(
                x = (Math.random() * 2000).toFloat(),
                y = (Math.random() * 2000).toFloat()
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 320.dp),
                drawerContainerColor = if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF5F2EA),
                drawerContentColor = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD0BCFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Cosmic Star",
                            tint = Color(0xFF381E72),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "AstroChronos",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = CelestialGold
                    )
                    Text(
                        text = "Planetary Hours & Tattwic Tides",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (darkTheme) Color.LightGray else Color.DarkGray
                    )
                }

                HorizontalDivider(
                    color = if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 1. Cosmic Settings & Modifiers
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cosmic Settings & Modifiers",
                            tint = CelestialGold
                        )
                    },
                    label = {
                        Text(
                            text = "Cosmic Settings & Modifiers",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    selected = showSettingsState,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showSettingsState = true
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("nav_drawer_settings")
                )

                // 2. Export to Local CSV
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export to Local CSV",
                            tint = CelestialGold
                        )
                    },
                    label = {
                        Text(
                            text = "Export to Local CSV",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    selected = showExportDialog,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showExportDialog = true
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("nav_drawer_export")
                )

                // 3. Recorded Shift Logs
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Recorded Shift Logs",
                            tint = CelestialGold
                        )
                    },
                    label = {
                        Text(
                            text = "Recorded Shift Logs",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    badge = {
                        Badge(
                            containerColor = CelestialGold,
                            contentColor = Color.Black
                        ) {
                            Text("${logsList.size}")
                        }
                    },
                    selected = showLogLevelsState,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showLogLevelsState = true
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("nav_drawer_logs")
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_scaffold"),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hamburger_menu_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Cosmic Navigation Menu",
                                tint = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD0BCFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "AstroChronos Star Badge",
                                    tint = Color(0xFF381E72),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "AstroChronos",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (darkTheme) Color(0xFF25232A) else Color(0xFFEDE8DB)
                    ),
                    modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                )
            }
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (darkTheme) SpaceBackground else Color(0xFFF9F6EE))
                .drawBehind {
                    if (darkTheme) {
                        for (star in starsPositions) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.35f),
                                radius = 2.dp.toPx(),
                                center = star
                            )
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Info Location Stats Top Banner Indicator
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) Color(0xFF131326) else Color(0xFFEBE5D8)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "📍 $locationName",
                                    color = CelestialGold,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("location_indicator")
                                )
                                IconButton(
                                    onClick = {
                                        if (viewModel.hasLocationPermission()) {
                                            viewModel.acquireCurrentLocation { _, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(24.dp).testTag("quick_gps_locate_btn")
                                ) {
                                    if (isLocating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = CelestialGold,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "Auto-detect location with GPS",
                                            tint = CelestialGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "🕒 ${formatSecToString(currentTimeSec)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (darkTheme) Color.LightGray else Color.DarkGray
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Date Navigation Row (Day-Today to Day-NextDay)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.goToPreviousDay() },
                                modifier = Modifier.height(36.dp).testTag("prev_day_btn"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                            ) {
                                Text("◄ Prev", fontSize = 12.sp, color = if (darkTheme) Color.LightGray else Color.DarkGray)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable { showDatePickerDialog = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("date_picker_trigger")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Pick Date",
                                    tint = CelestialGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (calcResults != null) "${calcResults!!.dayName} (${calcResults!!.date})" else currentDateStr,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = CelestialGold
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.goToNextDay() },
                                modifier = Modifier.height(36.dp).testTag("next_day_btn"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                            ) {
                                Text("Next ►", fontSize = 12.sp, color = if (darkTheme) Color.LightGray else Color.DarkGray)
                            }
                        }

                        if (calcResults != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (darkTheme) SpaceBackground else Color(0xFFEFEBE1),
                                border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "👑 Day Ruler:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.LightGray else Color.DarkGray
                                        )
                                        Text(
                                            text = "${calcResults!!.dayRulerSymbol} ${calcResults!!.dayRulerName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CelestialGold
                                        )
                                    }
                                    Text(
                                        text = "Sunrise → Next Sunrise",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (darkTheme) Color.Gray else Color.DarkGray,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }

                        if (isManualDateSelected) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.goToToday() },
                                    label = { Text("↺ Reset to Live Planetary Day", fontSize = 11.sp) },
                                    modifier = Modifier.testTag("reset_today_chip")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Solar bounds summary spanning Sunrise to Next Sunrise
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌅 Sunrise (Hour 1 / Tattwa 1): $sunriseOverride",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌇 Sunset (Night Hour 13): $sunsetOverride",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌅 Next Sunrise (Cycle End / Tattwa 60): $tomorrowSunriseOverride",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CelestialGold
                                )
                            }
                        }
                    }
                }

                // Settings & Custom Inputs Overlay Modal Block (Expanded state)
                AnimatedVisibility(
                    visible = showSettingsState,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .testTag("settings_overlay"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (darkTheme) StarrySlateCard else Color(0xFFEDE8DB),
                        border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Cosmic Settings & Modifiers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CelestialGold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Settings row: Mode switches
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                maxItemsInEachRow = 3,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = darkTheme,
                                        onCheckedChange = { viewModel.setDarkTheme(it) },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_checkbox")
                                    )
                                    Text("Dark Universe Mode", style = MaterialTheme.typography.bodyMedium)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = notificationsEnabled,
                                        onCheckedChange = {
                                            viewModel.setNotificationsEnabled(it)
                                            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("notifications_checkbox")
                                    )
                                    Text("Shift Reminders", style = MaterialTheme.typography.bodyMedium)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = hapticsEnabled,
                                        onCheckedChange = { viewModel.setHapticsEnabled(it) },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("haptics_checkbox")
                                    )
                                    Text("Haptic Feedback Trigger", style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))

                            // Location detection edits
                            Text(
                                text = "Personalized Geocentric Coordinates",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CelestialGold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inputLocName,
                                    onValueChange = { inputLocName = it },
                                    label = { Text("Location Name") },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .testTag("location_name_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = inputLat,
                                    onValueChange = { inputLat = it },
                                    label = { Text("Latitude") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("latitude_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = inputLon,
                                    onValueChange = { inputLon = it },
                                    label = { Text("Longitude") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("longitude_input"),
                                    singleLine = true
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (viewModel.hasLocationPermission()) {
                                            viewModel.acquireCurrentLocation { _, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    enabled = !isLocating,
                                    modifier = Modifier.weight(1.3f).testTag("live_autodetect_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    if (isLocating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Locating...")
                                    } else {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto-Detect GPS")
                                    }
                                }

                                Button(
                                    onClick = {
                                        val parsedLat = inputLat.toDoubleOrNull() ?: 40.7128
                                        val parsedLon = inputLon.toDoubleOrNull() ?: -74.0060
                                        viewModel.updateLocation(parsedLat, parsedLon, inputLocName)
                                        Toast.makeText(context, "Location metrics updated dynamically!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = CelestialGold)
                                ) {
                                    Text("Apply Setup")
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))

                            // Manual override Solar clocks
                            Text(
                                text = "Bespoke Solar Event Manual Overrides",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CelestialGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = sunriseOverride,
                                    onValueChange = { viewModel.manualOverrideSunrise(it, 0) },
                                    label = { Text("Sunrise") },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("HH:MM:SS") }
                                )
                                OutlinedTextField(
                                    value = sunsetOverride,
                                    onValueChange = { viewModel.manualOverrideSunrise(it, 1) },
                                    label = { Text("Sunset") },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("HH:MM:SS") }
                                )
                                OutlinedTextField(
                                    value = tomorrowSunriseOverride,
                                    onValueChange = { viewModel.manualOverrideSunrise(it, 2) },
                                    label = { Text("Tmrw Sunrise") },
                                    modifier = Modifier.weight(1.1f),
                                    placeholder = { Text("HH:MM:SS") }
                                )
                            }
                        }
                    }
                }

                // Log Experience Sidebar Overlay
                AnimatedVisibility(
                    visible = showLogLevelsState,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .testTag("logs_overlay"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (darkTheme) StarrySlateCard else Color(0xFFECE7D9),
                        border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .heightIn(max = 280.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌌 Transition shift log history",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CelestialGold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showExportDialog = true },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("export_csv_btn")
                                    ) {
                                        Icon(Icons.Default.Share, "Export CSV options dialog", tint = CelestialGold)
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearLogs() },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, "Clear all history logs", tint = Color.Red)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (logsList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No shifts logged yet.\nTweak notes above to record transitions offline.",
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    items(logsList) { log ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(
                                                    if (darkTheme) SpaceBackground else Color(0xFFF9F6EE),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "🌌 Hour: ${log.planetName}  ·  Tattva: ${log.tattvaName}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = CelestialGold
                                                    )
                                                }
                                                Text(
                                                    text = log.notes,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontStyle = FontStyle.Italic,
                                                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                                                )
                                                Text(
                                                    text = "@ ${log.locationName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteLog(log.id) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Default.Delete, "Remove item log", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Current feature layouts (Vibrant Palette)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Dynamic main featured status card based on selected view mode
                    when (viewMode) {
                        ViewMode.PLANETARY_HOURS -> {
                            if (curPlanetaryHour != null) {
                                val ph = curPlanetaryHour!!
                                val remainingTotalSec = (ph.endSecondOfDay - currentTimeSec).roundToInt().coerceAtLeast(0)
                                val timeRemainingStr = String.format(Locale.US, "%02d:%02d", remainingTotalSec / 60, remainingTotalSec % 60)
                                val subtitle = when (ph.planetName.lowercase(Locale.US)) {
                                    "mars" -> "Phase of Energy, Focus & Vitality"
                                    "sun" -> "Phase of Influence, Power & Cosmic Light"
                                    "moon" -> "Phase of Reflection, Intuition & Transition"
                                    "mercury" -> "Phase of Logic, Wisdom & Communication"
                                    "jupiter" -> "Phase of Expansion, Abundance & Learning"
                                    "venus" -> "Phase of Harmony, Beauty & Artistic Resonance"
                                    "saturn" -> "Phase of Structure, Discipline & Grounding"
                                    else -> "Phase of celestial alignment and cosmic cycle"
                                }
                                FeaturedCycleCard(
                                    testTag = "current_planet_card",
                                    containerColor = if (darkTheme) Color(0xFF31111D) else Color(0xFFFDE7EC),
                                    borderColor = if (darkTheme) Color(0xFF4E102E) else Color(0xFFF0B3C4),
                                    badgeTint = if (darkTheme) Color(0xFFFFB1C8) else Color(0xFF31111D),
                                    titleText = "CURRENT PLANETARY HOUR",
                                    titleColor = if (darkTheme) Color(0xFFFFD8E4) else Color(0xFF8C3E52),
                                    headlineText = "${ph.planetSymbol} ${ph.planetName}",
                                    headlineColor = if (darkTheme) Color(0xFFFFB1C8) else Color(0xFF801A34),
                                    subtitleText = subtitle,
                                    subtitleColor = (if (darkTheme) Color(0xFFFFD8E4) else Color(0xFF8C3E52)).copy(alpha = 0.8f),
                                    timeRemainingStr = timeRemainingStr,
                                    timerColor = if (darkTheme) Color.White else Color(0xFF31111D),
                                    timerLabelColor = (if (darkTheme) Color(0xFFFFD8E4) else Color(0xFF8C3E52)).copy(alpha = 0.7f)
                                )
                            } else {
                                FeaturedCycleCard(
                                    testTag = "current_planet_card",
                                    containerColor = if (darkTheme) Color(0xFF31111D) else Color(0xFFFDE7EC),
                                    borderColor = if (darkTheme) Color(0xFF4E102E) else Color(0xFFF0B3C4),
                                    badgeTint = Color.Gray,
                                    titleText = "", titleColor = Color.Transparent, headlineText = "", headlineColor = Color.Transparent, subtitleText = "", subtitleColor = Color.Transparent, timeRemainingStr = "", timerColor = Color.Transparent, timerLabelColor = Color.Transparent, emptyMessage = "No Planetary Hour Loaded"
                                )
                            }
                        }
                        ViewMode.TATTWIC_TIDES -> {
                            if (curTattva != null) {
                                val tv = curTattva!!
                                val rgbColor = remember(tv.colorHex) { Color(android.graphics.Color.parseColor(tv.colorHex)) }
                                val remainingTotalSec = (tv.endSecondOfDay - currentTimeSec).roundToInt().coerceAtLeast(0)
                                val timeRemainingStr = String.format(Locale.US, "%02d:%02d", remainingTotalSec / 60, remainingTotalSec % 60)
                                FeaturedCycleCard(
                                    testTag = "current_tattva_featured_card",
                                    containerColor = if (darkTheme) Color(0xFF1B262C) else Color(0xFFE8F1F5),
                                    borderColor = if (darkTheme) Color(0xFF0F4C5C) else Color(0xFF90E0EF),
                                    badgeTint = if (darkTheme) Color(0xFF3282B8) else Color(0xFF1B262C),
                                    titleText = "CURRENT TATTWIC TIDE",
                                    titleColor = if (darkTheme) Color(0xFFBBE1FA) else Color(0xFF0F4C5C),
                                    headlineText = "${tv.symbol} ${tv.name}",
                                    headlineColor = rgbColor,
                                    subtitleText = "Element: ${tv.element} — ${tv.description}",
                                    subtitleColor = (if (darkTheme) Color(0xFFBBE1FA) else Color(0xFF0F4C5C)).copy(alpha = 0.8f),
                                    timeRemainingStr = timeRemainingStr,
                                    timerColor = if (darkTheme) Color.White else Color(0xFF1B262C),
                                    timerLabelColor = (if (darkTheme) Color(0xFFBBE1FA) else Color(0xFF0F4C5C)).copy(alpha = 0.7f)
                                )
                            } else {
                                FeaturedCycleCard(
                                    testTag = "current_tattva_featured_card",
                                    containerColor = if (darkTheme) Color(0xFF1B262C) else Color(0xFFE8F1F5),
                                    borderColor = if (darkTheme) Color(0xFF0F4C5C) else Color(0xFF90E0EF),
                                    badgeTint = Color.Gray,
                                    titleText = "", titleColor = Color.Transparent, headlineText = "", headlineColor = Color.Transparent, subtitleText = "", subtitleColor = Color.Transparent, timeRemainingStr = "", timerColor = Color.Transparent, timerLabelColor = Color.Transparent, emptyMessage = "No Tattwic Tide Loaded"
                                )
                            }
                        }
                        ViewMode.COMBINED_VIEW -> {
                            if (curCombined != null) {
                                val cb = curCombined!!
                                val remainingTotalSec = (cb.endSecondOfDay - currentTimeSec).roundToInt().coerceAtLeast(0)
                                val timeRemainingStr = String.format(Locale.US, "%02d:%02d", remainingTotalSec / 60, remainingTotalSec % 60)
                                FeaturedCycleCard(
                                    testTag = "current_combined_featured_card",
                                    containerColor = if (darkTheme) Color(0xFF1D1A27) else Color(0xFFF3F1F8),
                                    borderColor = if (darkTheme) Color(0xFF4F378B) else Color(0xFFE8DDFF),
                                    badgeTint = if (darkTheme) Color(0xFFD0BCFF) else Color(0xFF1D1A27),
                                    titleText = "CURRENT COMBINED VIEW",
                                    titleColor = CelestialGold,
                                    headlineText = "${cb.planetSymbol} ${cb.planetName} + ${cb.tattvaSymbol} ${cb.tattvaName}",
                                    headlineColor = if (darkTheme) Color.White else Color.Black,
                                    subtitleText = "Alignment: Planet ${cb.planetName} harmonizes with Elemental Tide ${cb.tattvaName}.",
                                    subtitleColor = (if (darkTheme) Color(0xFFCAC4D0) else Color(0xFF1D1A27)).copy(alpha = 0.8f),
                                    timeRemainingStr = timeRemainingStr,
                                    timerColor = if (darkTheme) Color.White else Color(0xFF1D1A27),
                                    timerLabelColor = (if (darkTheme) Color(0xFFCAC4D0) else Color(0xFF1D1A27)).copy(alpha = 0.7f)
                                )
                            } else {
                                FeaturedCycleCard(
                                    testTag = "current_combined_featured_card",
                                    containerColor = if (darkTheme) Color(0xFF1D1A27) else Color(0xFFF3F1F8),
                                    borderColor = if (darkTheme) Color(0xFF4F378B) else Color(0xFFE8DDFF),
                                    badgeTint = Color.Gray,
                                    titleText = "", titleColor = Color.Transparent, headlineText = "", headlineColor = Color.Transparent, subtitleText = "", subtitleColor = Color.Transparent, timeRemainingStr = "", timerColor = Color.Transparent, timerLabelColor = Color.Transparent, emptyMessage = "No Combined Alignment Loaded"
                                )
                            }
                        }
                    }

                    // 2. Tattwa & Haptics side-by-side status card sections
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SideStatusCard(
                            modifier = Modifier.weight(1f),
                            testTag = "current_tattva_card",
                            stripeColor = VibrantAccentPurple,
                            darkTheme = darkTheme,
                            label = "TATTWA",
                            title = curTattva?.let { "${it.symbol} ${it.name}" } ?: "—",
                            titleColor = curTattva?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color.Gray,
                            subtitle = curTattva?.let { "Element: ${it.element}" } ?: "No Tattva Loaded"
                        )

                        SideStatusCard(
                            modifier = Modifier.weight(1f),
                            testTag = "haptics_card",
                            stripeColor = if (hapticsEnabled) VibrantMint else Color.Gray,
                            darkTheme = darkTheme,
                            label = "HAPTICS",
                            title = if (hapticsEnabled) "Enabled" else "Muted",
                            titleColor = if (hapticsEnabled) VibrantMint else if (darkTheme) Color.Gray else Color.DarkGray,
                            subtitle = if (hapticsEnabled) "Precision active" else "Silent state",
                            onClick = { viewModel.setHapticsEnabled(!hapticsEnabled) }
                        )
                    }
                }

                // Quick transition logging action card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) Color(0xFF0C161C) else Color(0xFFDFE9EB)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (darkTheme) Color(0xFF1E353F) else Color(0xFFB4C8CD))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = noteInputText,
                            onValueChange = { noteInputText = it },
                            placeholder = { Text("Log active shift ritual or mood...", fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("note_input_field"),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CelestialGold,
                                unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (noteInputText.isNotBlank()) {
                                    viewModel.logShiftExperience(noteInputText)
                                    noteInputText = ""
                                    Toast.makeText(context, "Transition Shift logged offline!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("submit_note_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CelestialGold),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Log State", fontSize = 12.sp)
                        }
                    }
                }

                // Direct View Chooser Button Segmented Row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) StarrySlateCard else Color(0xFFEDE8DB)
                    ),
                    border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "CHOOSE ACTIVE VIEW CYCLE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = CelestialGold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val viewOptions = listOf(
                                ViewMode.PLANETARY_HOURS to "Planetary\nHours",
                                ViewMode.TATTWIC_TIDES to "Tattwic\nTides",
                                ViewMode.COMBINED_VIEW to "Combined\nView"
                            )

                            viewOptions.forEach { (option, label) ->
                                val isSelected = viewMode == option
                                OutlinedButton(
                                    onClick = { viewModel.setViewMode(option) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_view_${option.name.lowercase(Locale.US)}")
                                        .height(54.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) {
                                            if (darkTheme) Color(0xFF381E72) else Color(0xFFD0BCFF)
                                        } else {
                                            Color.Transparent
                                        },
                                        contentColor = if (isSelected) {
                                            if (darkTheme) Color(0xFFD0BCFF) else Color(0xFF381E72)
                                        } else {
                                            if (darkTheme) Color.White else Color.Black
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) {
                                            if (darkTheme) Color(0xFFD0BCFF) else Color(0xFF381E72)
                                        } else {
                                            if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB)
                                        }
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Filters Block
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) StarrySlateCard else Color(0xFFEDE8DB)
                    ),
                    border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "DENSE SYSTEM FILTERING ACCENTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = CelestialMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Planets list filter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AstronomyEngine.PLANET_ORDER.forEach { p ->
                                val active = planetFilters.contains(p)
                                val baseColor = remember(p) { Color(android.graphics.Color.parseColor(AstronomyEngine.PLANET_COLORS[p])) }
                                Button(
                                    onClick = { viewModel.togglePlanetFilter(p) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) baseColor.copy(alpha = 0.25f) else Color.Transparent,
                                        contentColor = if (active) baseColor else Color.Gray
                                    ),
                                    border = BorderStroke(1.dp, if (active) baseColor else Color.LightGray.copy(alpha = 0.3f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(text = "${AstronomyEngine.PLANET_SYMBOLS[p]} $p", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Tattva cycle list filter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AstronomyEngine.TATTVA_ORDER.forEach { t ->
                                val active = tattvaFilters.contains(t.name)
                                val baseColor = remember(t.colorHex) { Color(android.graphics.Color.parseColor(t.colorHex)) }
                                Button(
                                    onClick = { viewModel.toggleTattvaFilter(t.name) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) baseColor.copy(alpha = 0.25f) else Color.Transparent,
                                        contentColor = if (active) baseColor else Color.Gray
                                    ),
                                    border = BorderStroke(1.dp, if (active) baseColor else Color.LightGray.copy(alpha = 0.3f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(text = "${t.symbol} ${t.name}", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val columns = if (isWideScreen) 2 else 1

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp, max = 560.dp)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = if (darkTheme) Color(0xFF2B2930) else Color(0xFFEDE8DB),
                    border = BorderStroke(1.dp, if (darkTheme) Color(0xFF3B3840) else Color(0xFFD4CBBB))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "UPCOMING SHIFTS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = if (darkTheme) Color(0xFFCAC4D0) else Color(0xFF49454F)
                            )
                            Text(
                                text = "Filtered Cycles grid",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (darkTheme) Color(0xFF938F99) else Color(0xFF79747E)
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 480.dp)) {
                            when (viewMode) {
                                ViewMode.PLANETARY_HOURS -> {
                                    val list = calcResults?.planetaryHours?.filter { planetFilters.contains(it.planetName) } ?: emptyList()
                                    if (list.isEmpty()) {
                                        Text("No Planetary Hours match filters.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(list) { h ->
                                                val rgbColor = remember(h.colorHex) { Color(android.graphics.Color.parseColor(h.colorHex)) }
                                                val isActive = remember(h, currentTimeSec) { currentTimeSec >= h.startSecondOfDay && currentTimeSec < h.endSecondOfDay }

                                                CycleListItem(
                                                    isActive = isActive,
                                                    darkTheme = darkTheme,
                                                    itemColor = rgbColor,
                                                    titleText = "${h.planetSymbol} ${h.planetName}",
                                                    subtitleText = if (h.isNight) "Night Hour" else "Day Hour",
                                                    timeRangeText = "${formatSecToLocalTime(h.startSecondOfDay)} – ${formatSecToLocalTime(h.endSecondOfDay)}",
                                                    leadingNumber = h.number.toString()
                                                )
                                            }
                                        }
                                    }
                                }

                                ViewMode.TATTWIC_TIDES -> {
                                    val list = calcResults?.tattvas?.filter { tattvaFilters.contains(it.name) } ?: emptyList()
                                    if (list.isEmpty()) {
                                        Text("No Tattwas match filters.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(list) { tv ->
                                                val rgbColor = remember(tv.colorHex) { Color(android.graphics.Color.parseColor(tv.colorHex)) }
                                                val isActive = remember(tv, currentTimeSec) { currentTimeSec >= tv.startSecondOfDay && currentTimeSec < tv.endSecondOfDay }

                                                CycleListItem(
                                                    isActive = isActive,
                                                    darkTheme = darkTheme,
                                                    itemColor = rgbColor,
                                                    titleText = "${tv.symbol} ${tv.name}",
                                                    subtitleText = tv.element,
                                                    timeRangeText = "${formatSecToLocalTime(tv.startSecondOfDay)} – ${formatSecToLocalTime(tv.endSecondOfDay)}"
                                                )
                                            }
                                        }
                                    }
                                }

                                ViewMode.COMBINED_VIEW -> {
                                    val list = calcResults?.combined?.filter {
                                        planetFilters.contains(it.planetName) && tattvaFilters.contains(it.tattvaName)
                                    } ?: emptyList()

                                    if (list.isEmpty()) {
                                        Text("No Mixed Intersections match filters.", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(list) { cr ->
                                                val rgbPlanet = remember(cr.planetColorHex) { Color(android.graphics.Color.parseColor(cr.planetColorHex)) }
                                                val rgbTattva = remember(cr.tattvaColorHex) { Color(android.graphics.Color.parseColor(cr.tattvaColorHex)) }
                                                val isActive = remember(cr, currentTimeSec) { currentTimeSec >= cr.startSecondOfDay && currentTimeSec < cr.endSecondOfDay }

                                                CycleListItem(
                                                    isActive = isActive,
                                                    darkTheme = darkTheme,
                                                    itemColor = rgbPlanet,
                                                    titleText = "Hour: ${cr.planetSymbol} ${cr.planetName}",
                                                    secondTitleText = "Tide: ${cr.tattvaSymbol} ${cr.tattvaName}",
                                                    secondItemColor = rgbTattva,
                                                    timeRangeText = "${formatSecToLocalTime(cr.startSecondOfDay)} – ${formatSecToLocalTime(cr.endSecondOfDay)}"
                                                )
                                            }
                                        }
                                    }
                                }
                            } // closes when
                        } // closes Box
                    } // closes Column
                } // closes Surface
            } // closes Column
        } // closes Box
    } // closes Scaffold content lambda
} // closes ModalNavigationDrawer
} // closes MainScreen

@Composable
private fun FeaturedCycleCard(
    testTag: String,
    containerColor: Color,
    borderColor: Color,
    badgeTint: Color,
    titleText: String,
    titleColor: Color,
    headlineText: String,
    headlineColor: Color,
    subtitleText: String,
    subtitleColor: Color,
    timeRemainingStr: String,
    timerColor: Color,
    timerLabelColor: Color,
    emptyMessage: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        if (emptyMessage != null) {
            Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = Color.Gray)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = badgeTint.copy(alpha = 0.08f),
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.TopEnd)
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = titleColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = headlineText,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = headlineColor
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = subtitleColor
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = timeRemainingStr,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = timerColor
                        )
                        Text(
                            text = "REMAINING",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = timerLabelColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideStatusCard(
    modifier: Modifier = Modifier,
    testTag: String,
    stripeColor: Color,
    darkTheme: Boolean,
    label: String,
    title: String,
    titleColor: Color,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) Color(0xFF2B2930) else Color(0xFFF5F0F6)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF3B3840) else Color(0xFFD4CBBB))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (darkTheme) Color(0xFFCAC4D0) else Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (darkTheme) Color.Gray else Color.DarkGray
                )
            }
        }
    }
}

@Composable
private fun CycleListItem(
    isActive: Boolean,
    darkTheme: Boolean,
    itemColor: Color,
    titleText: String,
    subtitleText: String? = null,
    timeRangeText: String,
    leadingNumber: String? = null,
    secondTitleText: String? = null,
    secondItemColor: Color? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) itemColor.copy(alpha = 0.14f) else if (darkTheme) Color(0xFF1C1B1F) else Color(0xFFFFFBFF),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) (if (secondItemColor != null) (if (darkTheme) Color(0xFFD0BCFF) else Color(0xFF381E72)) else itemColor) else if (darkTheme) Color(0xFF3B3840) else Color(0xFFCAC4D0)
        )
    ) {
        if (secondTitleText != null && secondItemColor != null) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeRangeText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (darkTheme) Color.LightGray else Color.DarkGray
                    )
                    if (isActive) {
                        Text(
                            text = "✦ ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (darkTheme) Color(0xFFD0BCFF) else Color(0xFF381E72)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = titleText,
                        fontSize = 13.sp,
                        color = itemColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = secondTitleText,
                        fontSize = 13.sp,
                        color = secondItemColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingNumber != null) {
                        Text(
                            text = leadingNumber,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CelestialMuted,
                            modifier = Modifier.width(28.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(itemColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = titleText,
                            fontWeight = FontWeight.Bold,
                            color = itemColor
                        )
                        if (subtitleText != null) {
                            Text(
                                text = subtitleText,
                                fontSize = 11.sp,
                                color = CelestialMuted
                            )
                        }
                    }
                }
                Text(
                    text = timeRangeText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                )
            }
        }
    }
}

// Global timestamp text helpers
private fun formatSecToString(secValue: Double): String {
    val normalized = ((secValue % 86400) + 86400) % 86400
    val totalSecs = normalized.roundToInt() % 86400
    val h = (totalSecs / 3600) % 24
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
}

private fun formatSecToLocalTime(secValue: Double): String {
    val normalized = ((secValue % 86400) + 86400) % 86400
    val totalMins = (normalized / 60.0).roundToInt()
    val h = (totalMins / 60) % 24
    val m = totalMins % 60
    return String.format(Locale.getDefault(), "%02d:%02d", h, m)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CosmicSettingsBottomSheet(
    darkTheme: Boolean,
    notificationsEnabled: Boolean,
    hapticsEnabled: Boolean,
    inputLocName: String,
    inputLat: String,
    inputLon: String,
    isLocating: Boolean = false,
    sunriseOverride: String,
    sunsetOverride: String,
    tomorrowSunriseOverride: String,
    onDarkThemeChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onUpdateLocation: (Double, Double, String) -> Unit,
    onAutoDetectRequest: () -> Unit,
    onSunriseOverrideChange: (String) -> Unit,
    onSunsetOverrideChange: (String) -> Unit,
    onTomorrowSunriseOverrideChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var locNameState by remember(inputLocName) { mutableStateOf(inputLocName) }
    var latState by remember(inputLat) { mutableStateOf(inputLat) }
    var lonState by remember(inputLon) { mutableStateOf(inputLon) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF7F4EC),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .testTag("settings_overlay")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CelestialGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Cosmic Settings & Modifiers",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CelestialGold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Settings")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Settings row: Mode switches
            Text(
                text = "Application Preferences & Modifiers",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (darkTheme) Color(0xFFFFD8E4) else Color(0xFF801A34)
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = darkTheme,
                        onCheckedChange = onDarkThemeChange,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_checkbox")
                    )
                    Text("Dark Universe Mode", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsChange,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("notifications_checkbox")
                    )
                    Text("Shift Reminders", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hapticsEnabled,
                        onCheckedChange = onHapticsChange,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("haptics_checkbox")
                    )
                    Text("Haptic Feedback Trigger", style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB)
            )

            // Location detection edits
            Text(
                text = "Personalized Geocentric Coordinates",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CelestialGold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = locNameState,
                    onValueChange = { locNameState = it },
                    label = { Text("Location Name") },
                    modifier = Modifier.weight(1.5f).testTag("location_name_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = latState,
                    onValueChange = { latState = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f).testTag("latitude_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lonState,
                    onValueChange = { lonState = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f).testTag("longitude_input"),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAutoDetectRequest,
                    enabled = !isLocating,
                    modifier = Modifier.weight(1.3f).testTag("live_autodetect_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Locating...")
                    } else {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Detect GPS")
                    }
                }

                Button(
                    onClick = {
                        val parsedLat = latState.toDoubleOrNull() ?: 40.7128
                        val parsedLon = lonState.toDoubleOrNull() ?: -74.0060
                        onUpdateLocation(parsedLat, parsedLon, locNameState)
                        Toast.makeText(context, "Location metrics updated!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CelestialGold, contentColor = Color.Black)
                ) {
                    Text("Apply Setup", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB)
            )

            // Manual override Solar clocks
            Text(
                text = "Bespoke Solar Event Manual Overrides",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CelestialGold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sunriseOverride,
                    onValueChange = onSunriseOverrideChange,
                    label = { Text("Sunrise") },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("HH:MM:SS") }
                )
                OutlinedTextField(
                    value = sunsetOverride,
                    onValueChange = onSunsetOverrideChange,
                    label = { Text("Sunset") },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("HH:MM:SS") }
                )
                OutlinedTextField(
                    value = tomorrowSunriseOverride,
                    onValueChange = onTomorrowSunriseOverrideChange,
                    label = { Text("Tmrw Sunrise") },
                    modifier = Modifier.weight(1.1f),
                    placeholder = { Text("HH:MM:SS") }
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportCsvBottomSheet(
    darkTheme: Boolean,
    logsCount: Int,
    currentDateStr: String,
    onDismiss: () -> Unit,
    onExportLogs: (saveLocally: Boolean) -> Unit,
    onExportCycles: (saveLocally: Boolean) -> Unit,
    onExportComplete: (saveLocally: Boolean) -> Unit
) {
    var selectedOption by remember { mutableStateOf(ExportOption.SHIFT_LOGS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF7F4EC),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("export_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = CelestialGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Export to Local CSV",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CelestialGold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Export Panel")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Select the dataset to export into standard CSV format for spreadsheets or local records:",
                style = MaterialTheme.typography.bodyMedium,
                color = if (darkTheme) Color.LightGray else Color.DarkGray
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 1: Shift Logs History
            ExportOptionCard(
                title = "Shift Logs & Ritual Notes",
                subtitle = "$logsCount recorded transitions with planet, tattva & location",
                isSelected = selectedOption == ExportOption.SHIFT_LOGS,
                darkTheme = darkTheme,
                onClick = { selectedOption = ExportOption.SHIFT_LOGS }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 2: Tracked Daily Cycles Schedule
            ExportOptionCard(
                title = "Tracked Daily Cycles Schedule",
                subtitle = "All 24h planetary hours, tattwic tides & alignments for $currentDateStr",
                isSelected = selectedOption == ExportOption.DAILY_CYCLES,
                darkTheme = darkTheme,
                onClick = { selectedOption = ExportOption.DAILY_CYCLES }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 3: Complete Archive (Cycles + Logs)
            ExportOptionCard(
                title = "Complete Archive (All Cycles & Logs)",
                subtitle = "Comprehensive full dataset with header metadata and user logs",
                isSelected = selectedOption == ExportOption.COMPLETE_ARCHIVE,
                darkTheme = darkTheme,
                onClick = { selectedOption = ExportOption.COMPLETE_ARCHIVE }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Files are saved to your device's Downloads/MagickalTime folder.",
                style = MaterialTheme.typography.labelSmall,
                color = if (darkTheme) Color.Gray else Color.DarkGray,
                fontStyle = FontStyle.Italic
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        when (selectedOption) {
                            ExportOption.SHIFT_LOGS -> onExportLogs(false)
                            ExportOption.DAILY_CYCLES -> onExportCycles(false)
                            ExportOption.COMPLETE_ARCHIVE -> onExportComplete(false)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }

                Button(
                    onClick = {
                        when (selectedOption) {
                            ExportOption.SHIFT_LOGS -> onExportLogs(true)
                            ExportOption.DAILY_CYCLES -> onExportCycles(true)
                            ExportOption.COMPLETE_ARCHIVE -> onExportComplete(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CelestialGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1.3f).testTag("save_local_csv_confirm_btn")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to Local CSV", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedShiftLogsBottomSheet(
    darkTheme: Boolean,
    logsList: List<LoggedShift>,
    onDeleteLog: (Long) -> Unit,
    onClearLogs: () -> Unit,
    onOpenExport: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF7F4EC),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("logs_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("logs_overlay")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = CelestialGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Recorded Shift Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CelestialGold
                        )
                        Text(
                            text = "${logsList.size} logged transitions & notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (darkTheme) Color.LightGray else Color.DarkGray
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenExport,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("export_csv_btn")
                    ) {
                        Icon(Icons.Default.Share, "Export CSV options", tint = CelestialGold)
                    }
                    if (logsList.isNotEmpty()) {
                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Delete, "Clear all history logs", tint = Color.Red)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Shift Logs Panel")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (logsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No shift transitions logged yet.\nEnter notes on the main screen to record shifts offline.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logsList, key = { it.id }) { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (darkTheme) SpaceBackground else Color(0xFFF9F6EE),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🌌 Hour: ${log.planetName}  ·  Tattva: ${log.tattvaName}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CelestialGold
                                )
                                if (log.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = log.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = if (darkTheme) Color.LightGray else Color.DarkGray
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@ ${log.locationName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(
                                onClick = { onDeleteLog(log.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Delete, "Remove item log", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

private enum class ExportOption {
    SHIFT_LOGS,
    DAILY_CYCLES,
    COMPLETE_ARCHIVE
}

@Composable
private fun ExportOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CelestialGold.copy(alpha = 0.15f) else (if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF5F2EA)),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CelestialGold else (if (darkTheme) Color(0xFF3B3840) else Color(0xFFD4CBBB))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = CelestialGold,
                    unselectedColor = if (darkTheme) Color.Gray else Color.DarkGray
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) CelestialGold else (if (darkTheme) Color.White else Color.Black)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                )
            }
        }
    }
}

