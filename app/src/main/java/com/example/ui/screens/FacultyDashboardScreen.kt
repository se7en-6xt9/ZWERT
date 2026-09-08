package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.ScheduleSlotEntity
import com.example.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacultyDashboardScreen(navController: NavController, viewModel: MainViewModel) {
    var currentTime by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = java.time.LocalTime.now()
            kotlinx.coroutines.delay(10000)
        }
    }

    val todayStr = remember { LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
    val schedule by viewModel.getScheduleForDay(todayStr).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    DashboardInfoBlock(viewModel = viewModel, navController = navController, dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM dd")))
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                SmallFloatingActionButton(
                    onClick = { navController.navigate("import_timetable") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Default.Upload, "Import Timetable")
                }
                SmallFloatingActionButton(
                    onClick = { navController.navigate("manage_classes") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Edit, "Edit")
                }
            }
        }
    ) { innerPadding ->
        if (schedule.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No classes scheduled for today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(schedule, key = { _, slot -> slot.id }) { _, slot ->
                    GlassLectureCard(
                        slot = slot,
                        currentTime = currentTime,
                        isToday = true,
                        onClick = { navController.navigate("lecture_view/${slot.id}") }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardInfoBlock(viewModel: MainViewModel, navController: NavController, dateStr: String) {
    val userProfile by viewModel.userProfile.collectAsState()
    
    val firstName = userProfile?.firstName?.takeIf { it.isNotBlank() } ?: "Faculty"
    val lastName = userProfile?.lastName?.takeIf { it.isNotBlank() } ?: ""
    val name = if (lastName.isNotBlank()) "$firstName $lastName" else firstName
    val initials = if (firstName.isNotBlank()) firstName.take(1).uppercase() else "F"
    val dept = userProfile?.department?.takeIf { it.isNotBlank() } ?: "Demo"
    val college = userProfile?.college?.takeIf { it.isNotBlank() } ?: "Demo College"
    
    val deptText = if (dept.isNotBlank()) " • $dept" else ""

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { navController.navigate("profile") }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(deptText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$college • $dateStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GlassLectureCard(
    slot: ScheduleSlotEntity,
    currentTime: java.time.LocalTime = java.time.LocalTime.now(),
    isToday: Boolean = false,
    onClick: () -> Unit
) {
    val startTime = remember(slot.startTime) {
        try { java.time.LocalTime.parse(slot.startTime, DateTimeFormatter.ofPattern("HH:mm")) } catch (e: Exception) { null }
    }
    val endTime = remember(slot.endTime) {
        try { java.time.LocalTime.parse(slot.endTime, DateTimeFormatter.ofPattern("HH:mm")) } catch (e: Exception) { null }
    }

    val isLive = isToday && startTime != null && endTime != null && !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)
    
    val progress = if (isLive && startTime != null && endTime != null) {
        val totalMins = java.time.Duration.between(startTime, endTime).toMinutes().toFloat()
        val elapsedMins = java.time.Duration.between(startTime, currentTime).toMinutes().toFloat()
        if (totalMins > 0) (elapsedMins / totalMins).coerceIn(0f, 1f) else 0f
    } else 0f

    val subjectColors = listOf(Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFFBA68C8))
    val barColor = subjectColors[abs(slot.courseId.hashCode()) % subjectColors.size]
    
    val targetContainerColor = if (isLive) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor, 
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "containerColor"
    )
    
    val targetBorderColor = if (isLive) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "borderColor"
    )

    val targetBarColor = if (isLive) Color(0xFF4CAF50) else barColor
    val animatedBarColor by animateColorAsState(
        targetValue = targetBarColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "barColor"
    )
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, animatedBorderColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(animatedBarColor)
            )
            
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = slot.courseId,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isLive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                        ) {
                            Text(
                                text = "${slot.startTime} - ${slot.endTime}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                
                if (isLive) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(slot.startTime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text(slot.endTime, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, "Location", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slot.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PeopleAlt, "Students", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sec ${slot.section}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
