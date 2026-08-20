package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CelestialGold

@Composable
fun ExportCsvDialog(
    darkTheme: Boolean,
    logsCount: Int,
    currentDateStr: String,
    onDismiss: () -> Unit,
    onExportLogs: (saveLocally: Boolean) -> Unit,
    onExportCycles: (saveLocally: Boolean) -> Unit,
    onExportComplete: (saveLocally: Boolean) -> Unit
) {
    var selectedType by remember { mutableStateOf(ExportUtils.ExportType.SHIFT_LOGS_ONLY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = CelestialGold
                )
                Text(
                    text = "Export to Local CSV",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select data to export to a standard comma-separated CSV file for spreadsheets and analysis:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (darkTheme) Color.LightGray else Color.DarkGray
                )

                // Option cards using unified ExportType enum
                ExportUtils.ExportType.values().forEach { exportType ->
                    val subtitle = when (exportType) {
                        ExportUtils.ExportType.SHIFT_LOGS_ONLY -> "$logsCount recorded transitions with planet, tattva & location"
                        ExportUtils.ExportType.TRACKED_CYCLES_ONLY -> "All 24h planetary hours, tattwic tides & alignments for $currentDateStr"
                        ExportUtils.ExportType.COMPLETE_ALL -> "Comprehensive full dataset with header metadata and user logs"
                    }
                    ExportOptionCard(
                        title = exportType.title,
                        subtitle = subtitle,
                        isSelected = selectedType == exportType,
                        darkTheme = darkTheme,
                        onClick = { selectedType = exportType }
                    )
                }

                Text(
                    text = "Files are saved to your device's Downloads/MagickalTime folder.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (darkTheme) Color.Gray else Color.DarkGray,
                    fontStyle = FontStyle.Italic
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedType) {
                        ExportUtils.ExportType.SHIFT_LOGS_ONLY -> onExportLogs(true)
                        ExportUtils.ExportType.TRACKED_CYCLES_ONLY -> onExportCycles(true)
                        ExportUtils.ExportType.COMPLETE_ALL -> onExportComplete(true)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CelestialGold,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("save_local_csv_confirm_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save to Local CSV", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        when (selectedType) {
                            ExportUtils.ExportType.SHIFT_LOGS_ONLY -> onExportLogs(false)
                            ExportUtils.ExportType.TRACKED_CYCLES_ONLY -> onExportCycles(false)
                            ExportUtils.ExportType.COMPLETE_ALL -> onExportComplete(false)
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
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
