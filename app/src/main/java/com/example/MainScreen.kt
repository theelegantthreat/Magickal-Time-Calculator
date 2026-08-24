package com.example

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isWideScreen = config.screenWidthDp >= 720 || config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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
    val logsList by viewModel.allLogs.collectAsStateWithLifecycle()

    // Panel states requested via Hamburger menu
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showLogsPanel by remember { mutableStateOf(false) }

    // Inline quick note & date picker states
    var noteInputText by remember { mutableStateOf("") }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // 1. Cosmic Settings & Modifiers Panel
    if (showSettingsPanel) {
        CosmicSettingsPanel(
            darkTheme = darkTheme,
            notificationsEnabled = notificationsEnabled,
            hapticsEnabled = hapticsEnabled,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            sunriseOverride = sunriseOverride,
            sunsetOverride = sunsetOverride,
            tomorrowSunriseOverride = tomorrowSunriseOverride,
            onDarkThemeChanged = { viewModel.setDarkTheme(it) },
            onNotificationsChanged = { viewModel.setNotificationsEnabled(it) },
            onHapticsChanged = { viewModel.setHapticsEnabled(it) },
            onLocationUpdated = { lat, lon, name -> viewModel.updateLocation(lat, lon, name) },
            onManualSunriseOverride = { time, type -> viewModel.manualOverrideSunrise(time, type) },
            onDismiss = { showSettingsPanel = false }
        )
    }

    // 2. Export to Local CSV Panel
    if (showExportDialog) {
        ExportCsvDialog(
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

    // 3. Recorded Shift Logs Panel
    if (showLogsPanel) {
        RecordedShiftLogsPanel(
            darkTheme = darkTheme,
            logsList = logsList,
            onDeleteLog = { viewModel.deleteLog(it) },
            onClearLogs = { viewModel.clearLogs() },
            onAddLog = { viewModel.logShiftExperience(it) },
            onDismiss = { showLogsPanel = false }
        )
    }

    // Date Picker Dialog
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF7F4EC),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("navigation_drawer")
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // App branding header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD0BCFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFF381E72),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AstroChronos",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (darkTheme) Color.White else Color.Black
                        )
                        Text(
                            text = "Planetary & Tattwic Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = CelestialGold
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB)
                )

                // Menu Item 1: Cosmic Settings & Modifiers
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Cosmic Settings & Modifiers",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cosmic Settings & Modifiers",
                            tint = CelestialGold
                        )
                    },
                    selected = showSettingsPanel,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSettingsPanel = true
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("drawer_menu_settings")
                )

                // Menu Item 2: Export to Local CSV
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Export to Local CSV",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export to Local CSV",
                            tint = CelestialGold
                        )
                    },
                    selected = showExportDialog,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showExportDialog = true
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("drawer_menu_export")
                )

                // Menu Item 3: Recorded Shift Logs
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Recorded Shift Logs",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Recorded Shift Logs",
                            tint = CelestialGold
                        )
                    },
                    badge = {
                        if (logsList.isNotEmpty()) {
                            Badge(
                                containerColor = CelestialGold,
                                contentColor = Color.Black
                            ) {
                                Text("${logsList.size}", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    selected = showLogsPanel,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showLogsPanel = true
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("drawer_menu_logs")
                )

                Spacer(modifier = Modifier.weight(1f))

                // Footer in drawer
                Text(
                    text = "v1.0 • Magickal Timekeeping",
                    style = MaterialTheme.typography.labelSmall,
                    color = CelestialMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
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
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Menu",
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD0BCFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "AstroChronos Star Badge",
                                    tint = Color(0xFF381E72),
                                    modifier = Modifier.size(18.dp)
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
                        containerColor = if (darkTheme) Color(0xFF141318).copy(alpha = 0.95f) else Color(0xFFF1EDE0).copy(alpha = 0.95f)
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        if (darkTheme) {
                            Brush.verticalGradient(listOf(Color(0xFF141318), Color(0xFF1C1B1F), Color(0xFF191724)))
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFFF9F6EE), Color(0xFFF1EDE0), Color(0xFFEDE8DB)))
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header & Date selection card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("summary_card"),
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
                                Text(
                                    text = "📍 $locationName",
                                    color = CelestialGold,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.testTag("location_indicator")
                                )
                                Text(
                                    text = "🕒 ${TimeFormatUtils.formatSecToHms(currentTimeSec)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

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
                                        text = "Day-Today: $currentDateStr",
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

                            if (isManualDateSelected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.goToToday() },
                                        label = { Text("↺ Reset to Live Astrological Day", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("reset_today_chip")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🌅 Sunrise Today: $sunriseOverride",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                                )
                                Text(
                                    text = "🌇 Sunset: $sunsetOverride",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                                )
                                Text(
                                    text = "🌅 Sunrise Next Day: $tomorrowSunriseOverride",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CelestialGold
                                )
                            }
                        }
                    }

                    // Current Active Cycle Display Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (viewMode) {
                            ViewMode.PLANETARY_HOURS -> {
                                if (curPlanetaryHour != null) {
                                    val ph = curPlanetaryHour!!
                                    val remainingTotalSec = (ph.endSecondOfDay - currentTimeSec).roundToInt().coerceAtLeast(0)
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
                                        timeRemainingStr = TimeFormatUtils.formatRemainingTime(remainingTotalSec),
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
                                        timeRemainingStr = TimeFormatUtils.formatRemainingTime(remainingTotalSec),
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
                                        timeRemainingStr = TimeFormatUtils.formatRemainingTime(remainingTotalSec),
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

                        // Side-by-side status card sections
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

                    // Shift Logging Input
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

                    // View Mode Chooser
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
                                                        timeRangeText = "${TimeFormatUtils.formatSecToLocalTime(h.startSecondOfDay)} – ${TimeFormatUtils.formatSecToLocalTime(h.endSecondOfDay)}",
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
                                                        timeRangeText = "${TimeFormatUtils.formatSecToLocalTime(tv.startSecondOfDay)} – ${TimeFormatUtils.formatSecToLocalTime(tv.endSecondOfDay)}"
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
                                                        timeRangeText = "${TimeFormatUtils.formatSecToLocalTime(cr.startSecondOfDay)} – ${TimeFormatUtils.formatSecToLocalTime(cr.endSecondOfDay)}"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
