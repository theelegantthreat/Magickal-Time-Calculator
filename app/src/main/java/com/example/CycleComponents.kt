package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CelestialMuted
import java.util.Locale

@Composable
fun FeaturedCycleCard(
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emptyMessage, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
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
fun SideStatusCard(
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
fun CycleListItem(
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
