package com.example

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import com.example.ui.theme.CelestialGold
import com.example.ui.theme.CelestialMuted
import com.example.ui.theme.StarrySlateBorders

@Composable
fun RecordedShiftLogsPanel(
    darkTheme: Boolean,
    logsList: List<LoggedShift>,
    onDeleteLog: (Long) -> Unit,
    onClearLogs: () -> Unit,
    onAddLog: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var noteInputText by remember { mutableStateOf("") }

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
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = CelestialGold
                    )
                    Text(
                        text = "Recorded Shift Logs (${logsList.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (darkTheme) Color.White else Color.Black
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Shift Logs")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // New Note Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = { noteInputText = it },
                        placeholder = { Text("Add ritual note...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_note_input_field"),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (noteInputText.isNotBlank()) {
                                onAddLog(noteInputText)
                                noteInputText = ""
                                Toast.makeText(context, "Shift logged!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CelestialGold, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("dialog_submit_note_btn")
                    ) {
                        Text("Log", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shift History",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CelestialGold
                    )
                    if (logsList.isNotEmpty()) {
                        TextButton(
                            onClick = onClearLogs,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Clear All", color = Color(0xFFFF6B6B), fontSize = 11.sp)
                        }
                    }
                }

                if (logsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No shifts logged yet.\nEnter notes above to record offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logsList, key = { it.id }) { log ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (darkTheme) Color(0xFF1E1C24) else Color(0xFFF5F2EA),
                                border = BorderStroke(1.dp, if (darkTheme) StarrySlateBorders else Color(0xFFD4CBBB))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${log.planetName} + ${log.tattvaName}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (darkTheme) Color.White else Color.Black
                                        )
                                        Text(
                                            text = "${log.dateString} • ${log.locationName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CelestialMuted
                                        )
                                        if (log.notes.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = log.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (darkTheme) Color.LightGray else Color.DarkGray
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteLog(log.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete log entry",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CelestialGold, contentColor = Color.Black)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}
