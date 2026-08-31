package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanetaryObservationsBottomSheet(
    darkTheme: Boolean,
    observationsList: List<PlanetaryObservation>,
    currentPlanetaryHour: AstronomyEngine.PlanetaryHour?,
    currentTattva: AstronomyEngine.TattvaCycle?,
    onOpenAddDialog: () -> Unit,
    onOpenEditDialog: (PlanetaryObservation) -> Unit,
    onDeleteObservation: (Long) -> Unit,
    onClearObservations: () -> Unit,
    onOpenExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedPlanetFilter by remember { mutableStateOf<String?>(null) }
    var selectedMoodFilter by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val filteredObservations = remember(observationsList, searchQuery, selectedPlanetFilter, selectedMoodFilter) {
        observationsList.filter { obs ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                obs.title.contains(searchQuery, ignoreCase = true) ||
                        obs.content.contains(searchQuery, ignoreCase = true) ||
                        obs.planetName.contains(searchQuery, ignoreCase = true) ||
                        obs.tattwaName.contains(searchQuery, ignoreCase = true) ||
                        obs.tags.contains(searchQuery, ignoreCase = true) ||
                        obs.moodOrEnergy.contains(searchQuery, ignoreCase = true) ||
                        obs.dateString.contains(searchQuery, ignoreCase = true)
            }
            val matchesPlanet = selectedPlanetFilter == null || obs.planetName.equals(selectedPlanetFilter, ignoreCase = true)
            val matchesMood = selectedMoodFilter == null || obs.moodOrEnergy.equals(selectedMoodFilter, ignoreCase = true)
            matchesSearch && matchesPlanet && matchesMood
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (darkTheme) Color(0xFF1B1920) else Color(0xFFF7F4EC),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("observations_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag("observations_container")
        ) {
            // Top Bar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CelestialGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = CelestialGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Planetary Observations",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CelestialGold
                        )
                        Text(
                            text = "${observationsList.size} daily journal entries & reflections",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (darkTheme) Color.LightGray else Color.DarkGray
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenExport,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("export_observations_btn")
                    ) {
                        Icon(Icons.Default.Share, "Export Observations", tint = CelestialGold)
                    }
                    if (observationsList.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_observations_btn")
                        ) {
                            Icon(Icons.Default.Delete, "Clear all observations", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("close_observations_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Observations")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Hour Quick Add Banner
            if (currentPlanetaryHour != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (darkTheme) Color(0xFF25222E) else Color(0xFFEBE5D8),
                    border = BorderStroke(1.dp, CelestialGold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("active_hour_quick_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val planetColor = remember(currentPlanetaryHour.planetName) {
                                Color(android.graphics.Color.parseColor(AstronomyEngine.PLANET_COLORS[currentPlanetaryHour.planetName] ?: "#FFD700"))
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(planetColor.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentPlanetaryHour.planetSymbol,
                                    color = planetColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = "Active: ${currentPlanetaryHour.planetName} Hour (${if (currentPlanetaryHour.isNight) "Night" else "Day"} #${currentPlanetaryHour.number})",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (darkTheme) Color.White else Color.Black
                                )
                                Text(
                                    text = if (currentTattva != null) "Tattva: ${currentTattva.symbol} ${currentTattva.name} (${currentTattva.element})" else "Optimal for planetary alignment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                                )
                            }
                        }

                        Button(
                            onClick = onOpenAddDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CelestialGold,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("quick_log_observation_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Button(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = CelestialGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("add_observation_top_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record Planetary Observation", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Search Bar & Filter Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().testTag("observation_search_input"),
                placeholder = { Text("Search reflections, rituals, moods, tags...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Planet Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedPlanetFilter == null,
                        onClick = { selectedPlanetFilter = null },
                        label = { Text("All Planets", fontSize = 11.sp) }
                    )
                }
                items(AstronomyEngine.PLANET_ORDER) { planet ->
                    val isSelected = selectedPlanetFilter.equals(planet, ignoreCase = true)
                    val pColor = remember(planet) {
                        Color(android.graphics.Color.parseColor(AstronomyEngine.PLANET_COLORS[planet] ?: "#FFD700"))
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedPlanetFilter = if (isSelected) null else planet
                        },
                        label = {
                            Text("${AstronomyEngine.PLANET_SYMBOLS[planet]} $planet", fontSize = 11.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = pColor.copy(alpha = 0.3f),
                            selectedLabelColor = if (darkTheme) Color.White else Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Observations List
            if (filteredObservations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedPlanetFilter != null)
                                "No planetary observations match your search."
                            else
                                "No planetary observations recorded yet.\nTap '+ Record Observation' to document meditations, synchronicities, and rituals aligned with planetary hours.",
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
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredObservations, key = { it.id }) { obs ->
                        ObservationCard(
                            observation = obs,
                            darkTheme = darkTheme,
                            onEdit = { onOpenEditDialog(obs) },
                            onDelete = { onDeleteObservation(obs.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing all
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Clear All Observations?") },
            text = { Text("Are you sure you want to permanently delete all ${observationsList.size} planetary observation journal entries? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearObservations()
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "All observations cleared.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ObservationCard(
    observation: PlanetaryObservation,
    darkTheme: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val planetColor = remember(observation.planetName) {
        Color(android.graphics.Color.parseColor(AstronomyEngine.PLANET_COLORS[observation.planetName] ?: "#FFD700"))
    }
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()) }
    val formattedDate = remember(observation.timestamp) { sdf.format(Date(observation.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("observation_card_${observation.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) Color(0xFF222029) else Color(0xFFF3EFE6)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, planetColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Planet Badge + Hour Badge + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Planet Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = planetColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, planetColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = observation.planetSymbol.ifBlank { AstronomyEngine.PLANET_SYMBOLS[observation.planetName] ?: "" },
                                color = planetColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = observation.planetName,
                                color = planetColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Hour Phase Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (darkTheme) Color(0xFF33303D) else Color(0xFFE2DDD2)
                    ) {
                        Text(
                            text = "${if (observation.isNight) "Night" else "Day"} Hour ${observation.hourNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (darkTheme) Color.LightGray else Color.DarkGray,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Tattwa Badge if present
                    if (observation.tattwaName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (darkTheme) Color(0xFF2C2A36) else Color(0xFFDED8CB)
                        ) {
                            Text(
                                text = "${observation.tattwaSymbol} ${observation.tattwaName}".trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (darkTheme) Color(0xFFD0BCFF) else Color(0xFF6750A4),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Edit & Delete Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp).testTag("edit_observation_btn_${observation.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit observation",
                            tint = CelestialGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp).testTag("delete_observation_btn_${observation.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete observation",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title (if provided)
            if (observation.title.isNotBlank()) {
                Text(
                    text = observation.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (darkTheme) Color.White else Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Journal Observation Content
            Text(
                text = observation.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF2B2830),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Metadata Row: Mood chip, Date, Location, Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (observation.moodOrEnergy.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CelestialGold.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = observation.moodOrEnergy,
                                style = MaterialTheme.typography.labelSmall,
                                color = CelestialGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (observation.tags.isNotBlank()) {
                        Text(
                            text = "#${observation.tags.replace(",", " #")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (darkTheme) Color.Gray else Color.DarkGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "$formattedDate  ·  📍 ${observation.locationName.take(15)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditObservationDialog(
    darkTheme: Boolean,
    existingObservation: PlanetaryObservation?,
    currentPlanetaryHour: AstronomyEngine.PlanetaryHour?,
    currentTattva: AstronomyEngine.TattvaCycle?,
    currentDateStr: String,
    onDismiss: () -> Unit,
    onSave: (planet: String, hourNum: Int, isNight: Boolean, tattwa: String, title: String, content: String, mood: String, tags: String) -> Unit
) {
    val isEditMode = existingObservation != null

    var selectedPlanet by remember {
        mutableStateOf(existingObservation?.planetName ?: currentPlanetaryHour?.planetName ?: "Sun")
    }
    var selectedHourNumber by remember {
        mutableStateOf(existingObservation?.hourNumber ?: currentPlanetaryHour?.number ?: 1)
    }
    var isNightHour by remember {
        mutableStateOf(existingObservation?.isNight ?: currentPlanetaryHour?.isNight ?: false)
    }
    var selectedTattwa by remember {
        mutableStateOf(existingObservation?.tattwaName ?: currentTattva?.name ?: "Tejas")
    }
    var titleText by remember { mutableStateOf(existingObservation?.title ?: "") }
    var contentText by remember { mutableStateOf(existingObservation?.content ?: "") }
    var selectedMood by remember { mutableStateOf(existingObservation?.moodOrEnergy ?: "High Focus") }
    var tagsText by remember { mutableStateOf(existingObservation?.tags ?: "") }

    val moodOptions = listOf(
        "🎯 High Focus",
        "🧘 Serene / Meditative",
        "💡 Creative Flow",
        "⚡ Dynamic Action",
        "🌱 Grounding / Healing",
        "🌌 Mystical / Magickal",
        "👑 Authority & Leadership",
        "💖 Love & Harmony"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF7F4EC),
            border = BorderStroke(1.dp, CelestialGold.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .testTag("add_edit_observation_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "Edit Planetary Observation" else "New Planetary Observation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CelestialGold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Planet Selection
                Text(
                    text = "Associated Planet & Ruler",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(AstronomyEngine.PLANET_ORDER) { planet ->
                        val isSelected = selectedPlanet.equals(planet, ignoreCase = true)
                        val pColor = remember(planet) {
                            Color(android.graphics.Color.parseColor(AstronomyEngine.PLANET_COLORS[planet] ?: "#FFD700"))
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) pColor.copy(alpha = 0.35f) else (if (darkTheme) Color(0xFF2B2833) else Color(0xFFEBE5D8)),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) pColor else Color.Gray.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { selectedPlanet = planet }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = AstronomyEngine.PLANET_SYMBOLS[planet] ?: "",
                                    color = if (isSelected) pColor else (if (darkTheme) Color.White else Color.Black),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = planet,
                                    fontSize = 12.sp,
                                    color = if (isSelected) pColor else (if (darkTheme) Color.White else Color.Black),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Planetary Hour & Phase Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day / Night Toggle
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Phase",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (darkTheme) Color.LightGray else Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isNightHour = false },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (!isNightHour) CelestialGold.copy(alpha = 0.25f) else Color.Transparent
                                ),
                                border = BorderStroke(1.dp, if (!isNightHour) CelestialGold else Color.Gray.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("☀️ Day", fontSize = 11.sp, color = if (!isNightHour) CelestialGold else (if (darkTheme) Color.White else Color.Black))
                            }
                            OutlinedButton(
                                onClick = { isNightHour = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isNightHour) CelestialGold.copy(alpha = 0.25f) else Color.Transparent
                                ),
                                border = BorderStroke(1.dp, if (isNightHour) CelestialGold else Color.Gray.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("🌙 Night", fontSize = 11.sp, color = if (isNightHour) CelestialGold else (if (darkTheme) Color.White else Color.Black))
                            }
                        }
                    }

                    // Hour Number (1..12)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hour Number (1-12)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (darkTheme) Color.LightGray else Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (selectedHourNumber > 1) selectedHourNumber-- },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("◀", color = CelestialGold, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Hour $selectedHourNumber",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (darkTheme) Color.White else Color.Black
                            )
                            IconButton(
                                onClick = { if (selectedHourNumber < 12) selectedHourNumber++ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("▶", color = CelestialGold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tattwa Alignment
                Text(
                    text = "Elemental Tattwa",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(AstronomyEngine.TATTVA_ORDER) { tv ->
                        val isSelected = selectedTattwa.equals(tv.name, ignoreCase = true)
                        val tColor = remember(tv.colorHex) {
                            Color(android.graphics.Color.parseColor(tv.colorHex))
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) tColor.copy(alpha = 0.35f) else (if (darkTheme) Color(0xFF2B2833) else Color(0xFFEBE5D8)),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) tColor else Color.Gray.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { selectedTattwa = tv.name }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(text = tv.symbol, color = tColor, fontWeight = FontWeight.Bold)
                                Text(
                                    text = tv.name,
                                    fontSize = 12.sp,
                                    color = if (isSelected) tColor else (if (darkTheme) Color.White else Color.Black),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title Input
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Observation Title (Optional)") },
                    placeholder = { Text("e.g. Solar Invocation, Creative Writing Flow") },
                    modifier = Modifier.fillMaxWidth().testTag("observation_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mood / Mindstate Chips
                Text(
                    text = "State of Mind / Energy",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(moodOptions) { mood ->
                        val isSelected = selectedMood == mood
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMood = mood },
                            label = { Text(mood, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CelestialGold.copy(alpha = 0.3f),
                                selectedLabelColor = if (darkTheme) Color.White else Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Journal / Observation Content
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("Journal Notes & Observations *") },
                    placeholder = { Text("Describe mental clarity, energetic shifts, ritual actions, meditations, synchronicities, or work accomplishments during this planetary hour...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .testTag("observation_content_input"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 4
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tags (Optional)
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags / Categories (comma separated)") },
                    placeholder = { Text("meditation, tarot, creative, focus, dreaming") },
                    modifier = Modifier.fillMaxWidth().testTag("observation_tags_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Save Action Button
                Button(
                    onClick = {
                        val finalContent = contentText.ifBlank { titleText.ifBlank { "Planetary observation recorded." } }
                        onSave(
                            selectedPlanet,
                            selectedHourNumber,
                            isNightHour,
                            selectedTattwa,
                            titleText,
                            finalContent,
                            selectedMood,
                            tagsText
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_observation_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CelestialGold,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditMode) "Update Observation" else "Save Planetary Observation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
