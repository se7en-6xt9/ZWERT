package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.CourseEntity
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Curated palette of modern vibrant accent colors for distinct course recognition
private val CoursePalette = listOf(
    Color(0xFF6366F1), // Indigo
    Color(0xFF10B981), // Emerald
    Color(0xFF06B6D4), // Cyan
    Color(0xFFF59E0B), // Amber
    Color(0xFFEC4899), // Pink
    Color(0xFF8B5CF6), // Violet
    Color(0xFF3B82F6), // Blue
    Color(0xFFF97316), // Orange
    Color(0xFF14B8A6), // Teal
    Color(0xFFEF4444)  // Rose
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageClassesScreen(navController: NavController, viewModel: MainViewModel) {
    val courses by viewModel.getAllCourses().collectAsState(initial = emptyList())
    val isFaculty by viewModel.isFaculty.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val isTeacher = isFaculty && userRole != "student"
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Track which course is currently showing inline delete confirmation
    var courseConfirmingDeleteId by remember { mutableStateOf<String?>(null) }
    // Courses currently animating out before deletion
    val deletingCourseIds = remember { mutableStateListOf<String>() }
    // Deleting state in flight for network/db operation
    var isDeletingInFlight by remember { mutableStateOf(false) }

    // Bulk CSV Import Modal Dialog state
    var isCsvImportOpen by remember { mutableStateOf(false) }
    var csvTargetCourseId by remember { mutableStateOf<String?>(null) }

    // Entrance animation trigger
    var isScreenEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        isScreenEntered = true
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isScreenEntered,
                enter = fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) +
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                            initialOffsetY = { -it / 2 }
                        )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isTeacher) "Manage Classes" else "My Classes",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        val backInteraction = remember { MutableInteractionSource() }
                        val isBackPressed by backInteraction.collectIsPressedAsState()
                        val backScale by animateFloatAsState(
                            targetValue = if (isBackPressed) 0.90f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "backScale"
                        )
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.popBackStack()
                            },
                            interactionSource = backInteraction,
                            modifier = Modifier.graphicsLayer {
                                scaleX = backScale
                                scaleY = backScale
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isTeacher) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    csvTargetCourseId = null
                                    isCsvImportOpen = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Import Students CSV",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        floatingActionButton = {
            ManageClassesFab(
                isEmptyList = courses.isEmpty(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.navigate("add_edit_batch")
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (courses.isEmpty()) {
                // Friendly illustrated empty state
                AnimatedVisibility(
                    visible = isScreenEntered,
                    enter = fadeIn(animationSpec = spring(dampingRatio = 0.8f)) +
                            scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                initialScale = 0.92f
                            ),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    ManageClassesEmptyState(
                        onAddClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("add_edit_batch")
                        },
                        onCsvImportClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            csvTargetCourseId = null
                            isCsvImportOpen = true
                        }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(courses, key = { _, course -> course.id }) { index, course ->
                        val isVisibleInAnimation = !deletingCourseIds.contains(course.id)

                        // Staggered entrance animation for each card
                        val itemDelay = remember(course.id) { (index * 55).coerceAtMost(500) }
                        var itemEntered by remember { mutableStateOf(false) }
                        LaunchedEffect(isScreenEntered) {
                            if (isScreenEntered) {
                                delay(itemDelay.toLong())
                                itemEntered = true
                            }
                        }

                        val entranceAlpha by animateFloatAsState(
                            targetValue = if (itemEntered) 1f else 0f,
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                            label = "cardEntranceAlpha"
                        )
                        val entranceTranslationY by animateFloatAsState(
                            targetValue = if (itemEntered) 0f else 36f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "cardEntranceY"
                        )

                        // Smooth collapse & fade when deleted
                        AnimatedVisibility(
                            visible = isVisibleInAnimation,
                            exit = shrinkVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                            ),
                            modifier = Modifier.animateItem()
                        ) {
                            val accentColor = remember(course.id) {
                                CoursePalette[abs(course.id.hashCode()) % CoursePalette.size]
                            }

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = entranceAlpha
                                        translationY = entranceTranslationY
                                    }
                            ) {
                                ClassManageCard(
                                    course = course,
                                    accentColor = accentColor,
                                    showCsvImport = isTeacher,
                                    isConfirmingDelete = courseConfirmingDeleteId == course.id,
                                    isDeleting = isDeletingInFlight && courseConfirmingDeleteId == course.id,
                                    onCardClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isTeacher) {
                                            navController.navigate("add_edit_batch?batchId=${course.id}")
                                        } else {
                                            navController.navigate("student_report")
                                        }
                                    },
                                    onEditClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        navController.navigate("add_edit_batch?batchId=${course.id}")
                                    },
                                    onImportCsvClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        csvTargetCourseId = course.id
                                        isCsvImportOpen = true
                                    },
                                    onDeleteClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        courseConfirmingDeleteId = course.id
                                    },
                                    onConfirmDelete = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isDeletingInFlight = true
                                        coroutineScope.launch {
                                            // 1. Mark for exit animation
                                            deletingCourseIds.add(course.id)
                                            // 2. Allow smooth shrink animation to complete before database call
                                            delay(260)
                                            viewModel.deleteCourse(
                                                courseId = course.id,
                                                onComplete = {
                                                    isDeletingInFlight = false
                                                    courseConfirmingDeleteId = null
                                                    deletingCourseIds.remove(course.id)
                                                    Toast.makeText(context, "Class deleted successfully", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { err ->
                                                    isDeletingInFlight = false
                                                    courseConfirmingDeleteId = null
                                                    deletingCourseIds.remove(course.id)
                                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    },
                                    onCancelDelete = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        courseConfirmingDeleteId = null
                                    }
                                )
                            }
                        }
                    }
                }

                // Subtle top fade-out gradient to hint at content beneath the header
                val showTopGradient by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 }
                }
                AnimatedVisibility(
                    visible = showTopGradient,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    }

    if (isCsvImportOpen) {
        BulkCsvImportDialog(
            viewModel = viewModel,
            initialCourseId = csvTargetCourseId,
            targetMode = if (csvTargetCourseId != null) CsvImportTargetMode.EXISTING_CLASS else CsvImportTargetMode.ONBOARD_NEW_CLASS,
            onDismiss = { isCsvImportOpen = false }
        )
    }
}

/**
 * Modern Class Card with generous rounding, left-edge accent bar, layered glow/shadow,
 * pill-shaped course code badge, discrete tinted action buttons, and inline delete confirmation.
 */
@Composable
private fun ClassManageCard(
    course: CourseEntity,
    accentColor: Color,
    showCsvImport: Boolean = true,
    isConfirmingDelete: Boolean,
    isDeleting: Boolean,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onImportCsvClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()

    // Tactile 0.97x press feedback with spring bounce-back
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed && !isConfirmingDelete) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    val cardCornerRadius = 18.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .shadow(
                elevation = if (isConfirmingDelete) 6.dp else 4.dp,
                shape = cardShape,
                spotColor = if (isConfirmingDelete) Color(0xFFEF4444).copy(alpha = 0.35f) else accentColor.copy(alpha = 0.28f),
                ambientColor = Color.Black.copy(alpha = 0.45f)
            )
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                enabled = !isConfirmingDelete,
                onClick = onCardClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isConfirmingDelete) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isConfirmingDelete) Color(0xFFEF4444).copy(alpha = 0.45f)
            else accentColor.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Subtle colored left-edge accent bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(if (isConfirmingDelete) Color(0xFFEF4444) else accentColor)
            )

            // Animated content transition between standard card view & inline delete confirmation
            AnimatedContent(
                targetState = isConfirmingDelete,
                transitionSpec = {
                    fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                            ) togetherWith
                            fadeOut(animationSpec = tween(150)) +
                            scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(150)
                            )
                },
                label = "cardModeTransition",
                modifier = Modifier.fillMaxWidth()
            ) { confirming ->
                if (!confirming) {
                    // Standard Card Content
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = course.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Course code badge: rounded pill with subtle tinted background matching accent
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                            ) {
                                Text(
                                    text = course.code,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Distinct action buttons with tinted circular backgrounds
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showCsvImport) {
                                // Import Students CSV Button (soft emerald green tinted circle)
                                TintedActionButton(
                                    icon = Icons.Default.UploadFile,
                                    contentDescription = "Import Students CSV",
                                    tintColor = Color(0xFF10B981),
                                    backgroundColor = Color(0xFF10B981).copy(alpha = 0.14f),
                                    borderColor = Color(0xFF10B981).copy(alpha = 0.28f),
                                    onClick = onImportCsvClick
                                )
                            }

                            // Edit Button (soft blue tinted circle)
                            TintedActionButton(
                                icon = Icons.Default.Edit,
                                contentDescription = "Edit Class",
                                tintColor = Color(0xFF60A5FA),
                                backgroundColor = Color(0xFF3B82F6).copy(alpha = 0.14f),
                                borderColor = Color(0xFF3B82F6).copy(alpha = 0.28f),
                                onClick = onEditClick
                            )

                            // Delete Button (soft red tinted circle)
                            TintedActionButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Delete Class",
                                tintColor = Color(0xFFF87171),
                                backgroundColor = Color(0xFFEF4444).copy(alpha = 0.14f),
                                borderColor = Color(0xFFEF4444).copy(alpha = 0.28f),
                                onClick = onDeleteClick
                            )
                        }
                    }
                } else {
                    // Inline Delete Confirmation State
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delete \"${course.name}\"?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Removes class & students permanently",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel Button
                            OutlinedButton(
                                onClick = onCancelDelete,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelMedium)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Confirm Delete Button with spring bounce
                            val confirmInteraction = remember { MutableInteractionSource() }
                            val isConfirmPressed by confirmInteraction.collectIsPressedAsState()
                            val confirmScale by animateFloatAsState(
                                targetValue = if (isConfirmPressed) 0.94f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "confirmScale"
                            )

                            Button(
                                onClick = onConfirmDelete,
                                enabled = !isDeleting,
                                interactionSource = confirmInteraction,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .graphicsLayer {
                                        scaleX = confirmScale
                                        scaleY = confirmScale
                                    }
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Delete",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
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

/**
 * Discrete Tinted Action Button (Edit / Delete) with circular background and tactile spring bounce.
 */
@Composable
private fun TintedActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tintColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tintedBtnScale"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Floating Action Button with subtle gradient fill, soft glowing shadow, press bounce,
 * and an ambient pulsing halo when the class list is empty.
 */
@Composable
private fun ManageClassesFab(
    isEmptyList: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile bounce on press
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fabPressScale"
    )

    // Soft attention-grabbing pulse when list is empty
    val infiniteTransition = rememberInfiniteTransition(label = "fabEmptyPulse")
    val idleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isEmptyList) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabIdleScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = if (isEmptyList) 0.45f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fabHaloAlpha"
    )
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isEmptyList) 1.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fabHaloScale"
    )

    val combinedScale = pressScale * idleScale
    val fabGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF8B5CF6)  // Violet
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(72.dp)
    ) {
        // Continuous soft glowing halo when empty
        if (isEmptyList) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = haloAlpha
                    }
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1))
            )
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = combinedScale
                    scaleY = combinedScale
                }
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    spotColor = Color(0xFF6366F1).copy(alpha = 0.65f),
                    ambientColor = Color(0xFF8B5CF6).copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(fabGradient)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Class",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Friendly illustrated empty state when zero classes exist.
 */
@Composable
private fun ManageClassesEmptyState(
    onAddClick: () -> Unit,
    onCsvImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Layered circular illustration badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(96.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.22f),
                                Color(0xFF8B5CF6).copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.25f)),
                shadowElevation = 4.dp,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No classes yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap + to add your first class, or import from a timetable on the dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onAddClick,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF818CF8)
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Class", fontWeight = FontWeight.SemiBold)
            }

            FilledTonalButton(
                onClick = onCsvImportClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                    contentColor = Color(0xFF059669)
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import CSV", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

