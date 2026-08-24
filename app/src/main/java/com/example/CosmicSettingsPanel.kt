package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.CelestialGold
import com.example.ui.theme.StarrySlateBorders
import com.example.ui.theme.StarrySlateCard
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CosmicSettingsPanel(
    darkTheme: Boolean,
    notificationsEnabled: Boolean,
    hapticsEnabled: Boolean,
    latitude: Double,
    longitude: Double,
    locationName: String,
    sunriseOverride: String,
    sunsetOverride: String,
    tomorrowSunriseOverride: String,
    onDarkThemeChanged: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onLocationUpdated: (Double, Double, String) -> Unit,
    onManualSunriseOverride: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputLat by remember(latitude) { mutableStateOf(String.format(Locale.US, "%.5f", latitude)) }
    var inputLon by remember(longitude) { mutableStateOf(String.format(Locale.US, "%.5f", longitude)) }
    var inputLocName by remember(locationName) { mutableStateOf(locationName) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Shift notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
            onNotificationsChanged(false)
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "Acquiring GPS coordinates...", Toast.LENGTH_SHORT).show()
            LocationHelper.handleLocationDetection(
                context = context,
                onLocationDetected = { lat, lon, name ->
                    inputLat = String.format(Locale.US, "%.5f", lat)
                    inputLon = String.format(Locale.US, "%.5f", lon)
                    inputLocName = name
                    onLocationUpdated(lat, lon, name)
                },
                onStatusToast = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Location permission was denied.", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CelestialGold
                    )
                    Text(
                        text = "Cosmic Settings & Modifiers",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (darkTheme) Color.White else Color.Black
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Settings")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Preferences & Feedback",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CelestialGold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = darkTheme,
                            onCheckedChange = { onDarkThemeChanged(it) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_checkbox")
                        )
                        Text("Dark Universe Mode", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                onNotificationsChanged(it)
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("notifications_checkbox")
                        )
                        Text("Shift Reminders", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hapticsEnabled,
                            onCheckedChange = { onHapticsChanged(it) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("haptics_checkbox")
                        )
                        Text("Haptic Feedback Trigger", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(
                    color = if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB)
                )

                Text(
                    text = "Personalized Geocentric Coordinates",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CelestialGold
                )

                OutlinedTextField(
                    value = inputLocName,
                    onValueChange = { inputLocName = it },
                    label = { Text("Location Name") },
                    modifier = Modifier.fillMaxWidth().testTag("location_name_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputLat,
                        onValueChange = { inputLat = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f).testTag("latitude_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inputLon,
                        onValueChange = { inputLon = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f).testTag("longitude_input"),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            val hasCoarse = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasFine || hasCoarse) {
                                Toast.makeText(context, "Acquiring coordinates...", Toast.LENGTH_SHORT).show()
                                LocationHelper.handleLocationDetection(
                                    context = context,
                                    onLocationDetected = { lat, lon, name ->
                                        inputLat = String.format(Locale.US, "%.5f", lat)
                                        inputLon = String.format(Locale.US, "%.5f", lon)
                                        inputLocName = name
                                        onLocationUpdated(lat, lon, name)
                                    },
                                    onStatusToast = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                locationLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1.2f).testTag("live_autodetect_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Detect", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val parsedLat = inputLat.toDoubleOrNull() ?: 40.7128
                            val parsedLon = inputLon.toDoubleOrNull() ?: -74.0060
                            onLocationUpdated(parsedLat, parsedLon, inputLocName)
                            Toast.makeText(context, "Location coordinates applied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CelestialGold, contentColor = Color.Black)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CelestialGold, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}
