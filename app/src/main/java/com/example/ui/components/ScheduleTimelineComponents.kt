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
import com.example.data.OfficialClassEntity
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
    data class OfficialSlotItem(val officialClass: OfficialClassEntity) : ScheduleTimelineItem()
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
 * Converts a raw list of OfficialClassEntity into a chronologically sorted timeline
 * with breaks automatically inserted between classes, identical to personal timetable.
 */
fun buildOfficialChronologicalTimeline(classes: List<OfficialClassEntity>): List<ScheduleTimelineItem> {
    if (classes.isEmpty()) return emptyList()

    // 1. Sort classes in strictly increasing order of start time
    val sortedClasses = classes.sortedWith(Comparator { c1, c2 ->
        val t1 = parseSlotTime(c1.startTime)
        val t2 = parseSlotTime(c2.startTime)
        when {
            t1 != null && t2 != null -> t1.compareTo(t2)
            t1 != null -> -1
            t2 != null -> 1
            else -> c1.startTime.compareTo(c2.startTime)
        }
    })

    val result = mutableListOf<ScheduleTimelineItem>()

    for (i in sortedClasses.indices) {
        val current = sortedClasses[i]
        result.add(ScheduleTimelineItem.OfficialSlotItem(current))

        // Check gap with next class
        if (i < sortedClasses.size - 1) {
            val next = sortedClasses[i + 1]
            val currentEnd = parseSlotTime(current.endTime)
            val nextStart = parseSlotTime(next.startTime)

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
                            id = "break_official_${current.slotId}_${next.slotId}",
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
 * Compact, thin mini-card for Break intervals.
 * Displays only: Break label, Duration (e.g. 60m), and Time Period (e.g. 1:30 PM - 2:30 PM).
 * Engineered to take minimal vertical space on compact screens.
 */
@Composable
fun ScheduleBreakCard(
    breakItem: ScheduleTimelineItem.BreakItem,
    modifier: Modifier = Modifier
) {
    val isLunch = breakItem.isLunch
    val primaryColor = if (isLunch) Color(0xFFFF7043) else Color(0xFF0D9488)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = primaryColor.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Icon + "Break" / "Lunch Break" + Duration Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = if (isLunch) Icons.Default.Restaurant else Icons.Default.Coffee,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLunch) "Lunch Break" else "Break",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = primaryColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${breakItem.durationMinutes}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Time Period
            Text(
                text = "${breakItem.startTime} - ${breakItem.endTime}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
