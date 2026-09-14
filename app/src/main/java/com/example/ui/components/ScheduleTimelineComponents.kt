package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScheduleSlotEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Represents items in the timeline schedule list (either a lecture slot or a break).
 */
sealed class ScheduleTimelineItem {
    data class SlotItem(val slot: ScheduleSlotEntity) : ScheduleTimelineItem()
    data class BreakItem(
        val id: String,
        val startTime: String,
        val endTime: String,
        val durationMinutes: Long,
        val title: String,
        val isLunch: Boolean
    ) : ScheduleTimelineItem()
}

/**
 * Universal time parser that reliably parses "09:00", "9:00 AM", "13:30", "1:30 PM", "01:30 PM", etc.
 */
fun parseSlotTime(timeStr: String): LocalTime? {
    val clean = timeStr.trim().uppercase(Locale.ENGLISH)
    return try {
        if (clean.contains("AM") || clean.contains("PM")) {
            val formatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("[hh:mm a][h:mm a][hh:mma][h:mma]")
                .toFormatter(Locale.ENGLISH)
            LocalTime.parse(clean, formatter)
        } else {
            val parts = clean.split(":")
            val h = parts[0].toInt()
            val m = parts[1].take(2).toInt()
            LocalTime.of(h, m)
        }
    } catch (e: Exception) {
        try {
            val fallback = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("[HH:mm][H:mm][HH.mm][H.mm]")
                .toFormatter(Locale.ENGLISH)
            LocalTime.parse(clean, fallback)
        } catch (e2: Exception) {
            null
        }
    }
}

/**
 * Converts a raw list of ScheduleSlotEntity into a chronologically sorted timeline
 * with lunch breaks and recess periods automatically inserted between classes.
 */
fun buildChronologicalTimeline(slots: List<ScheduleSlotEntity>): List<ScheduleTimelineItem> {
    if (slots.isEmpty()) return emptyList()

    // 1. Sort slots in strictly increasing order of start time
    val sortedSlots = slots.sortedWith(Comparator { s1, s2 ->
        val t1 = parseSlotTime(s1.startTime)
        val t2 = parseSlotTime(s2.startTime)
        when {
            t1 != null && t2 != null -> t1.compareTo(t2)
            t1 != null -> -1
            t2 != null -> 1
            else -> s1.startTime.compareTo(s2.startTime)
        }
    })

    val result = mutableListOf<ScheduleTimelineItem>()

    for (i in sortedSlots.indices) {
        val currentSlot = sortedSlots[i]
        result.add(ScheduleTimelineItem.SlotItem(currentSlot))

        // Check gap with next slot
        if (i < sortedSlots.size - 1) {
            val nextSlot = sortedSlots[i + 1]
            val currentEnd = parseSlotTime(currentSlot.endTime)
            val nextStart = parseSlotTime(nextSlot.startTime)

            if (currentEnd != null && nextStart != null && nextStart.isAfter(currentEnd)) {
                val gapMinutes = java.time.Duration.between(currentEnd, nextStart).toMinutes()
                
                // If gap is 15 minutes or more, insert a Break Card
                if (gapMinutes >= 15) {
                    val isLunchTime = (currentEnd.hour in 11..14) || (nextStart.hour in 12..15) || gapMinutes >= 45
                    val breakTitle = if (isLunchTime) "Lunch & Refreshment Break" else "Short Recess / Break"
                    
                    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                    val startStr = currentEnd.format(timeFormatter)
                    val endStr = nextStart.format(timeFormatter)

                    result.add(
                        ScheduleTimelineItem.BreakItem(
                            id = "break_${currentSlot.id}_${nextSlot.id}",
                            startTime = startStr,
                            endTime = endStr,
                            durationMinutes = gapMinutes,
                            title = breakTitle,
                            isLunch = isLunchTime
                        )
                    )
                }
            }
        }
    }

    return result
}

/**
 * Modern, polished mini-card to display Lunch / Recess breaks seamlessly integrated into the schedule.
 */
@Composable
fun ScheduleBreakCard(
    breakItem: ScheduleTimelineItem.BreakItem,
    modifier: Modifier = Modifier
) {
    val isLunch = breakItem.isLunch
    val warmAmber = Color(0xFFF59E0B)
    val lunchOrange = Color(0xFFFF7043)
    val teaTeal = Color(0xFF0D9488)

    val primaryColor = if (isLunch) lunchOrange else teaTeal
    val bgGradient = if (isLunch) {
        listOf(lunchOrange.copy(alpha = 0.12f), warmAmber.copy(alpha = 0.06f))
    } else {
        listOf(teaTeal.copy(alpha = 0.10f), teaTeal.copy(alpha = 0.04f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(bgGradient))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Icon + Title + Duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLunch) Icons.Default.Restaurant else Icons.Default.Coffee,
                            contentDescription = breakItem.title,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = breakItem.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = primaryColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${breakItem.durationMinutes} mins free time",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor
                            )
                        }
                    }
                }

                // Right Time Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = primaryColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${breakItem.startTime} - ${breakItem.endTime}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
