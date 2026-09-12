import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

# Extract from "if (isGridFullScreenOpen) {" to the end of the file.
start_str = "        if (isGridFullScreenOpen) {"

start_idx = content.find(start_str)
if start_idx == -1:
    print("Could not find dialog start.")
    exit(1)

pre_content = content[:start_idx]

new_dialog_code = """        if (isGridFullScreenOpen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isGridFullScreenOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                val activity = LocalContext.current as? android.app.Activity
                DisposableEffect(Unit) {
                    val original = activity?.requestedOrientation
                    // Allow full sensor rotation for this dialog
                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    onDispose {
                        original?.let { activity.requestedOrientation = it }
                    }
                }
                
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()
                val density = androidx.compose.ui.platform.LocalDensity.current
                
                val rowHeight by animateDpAsState(if (isLandscape) 44.dp else 52.dp, spring(dampingRatio = 0.8f))
                val timeColumnWidth = 110.dp
                val screenWidthDp = configuration.screenWidthDp.dp
                val availableGridWidth = screenWidthDp - timeColumnWidth
                val minDayWidth = 80.dp
                val dayWidth = if (isLandscape) maxOf(minDayWidth, availableGridWidth / gridDays.size) else 100.dp
                
                LaunchedEffect(Unit) {
                    // Scroll 8 hours down
                    val offset = with(density) { (8 * 52).dp.toPx() }.toInt()
                    verticalScrollState.scrollTo(offset)
                }
                
                Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            
                            // Top Header (Portrait)
                            AnimatedVisibility(
                                visible = !isLandscape,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { isGridFullScreenOpen = false }, modifier = Modifier.bounceClick(haptic) { isGridFullScreenOpen = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("Select Slots", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        
                                        // Legend
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF388E3C)))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Available", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFD32F2F)))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Booked", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(accentColor))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Selected", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                            
                            // Mini Legend (Landscape)
                            AnimatedVisibility(
                                visible = isLandscape,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(vertical = 4.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF388E3C)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Avail", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFD32F2F)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Book", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accentColor))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sel", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                                    }
                                }
                            }
                            
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                // Left Sticky Column (Times)
                                Column(modifier = Modifier.width(timeColumnWidth)) {
                                    Box(modifier = Modifier.height(rowHeight)) // Header spacing
                                    Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                                        gridHours.forEach { hour ->
                                            Box(
                                                modifier = Modifier.height(rowHeight).fillMaxWidth().padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(hour, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                            }
                                            Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                
                                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                // Scrollable Days and Cells
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .overlayFadeEdges(colorScheme.background, right = true)
                                        .horizontalScroll(horizontalScrollState)
                                ) {
                                    // Header (Days)
                                    Row(modifier = Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                                        gridDays.forEach { day ->
                                            Box(
                                                modifier = Modifier
                                                    .width(dayWidth)
                                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                                                    .fillMaxHeight()
                                                    .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(day, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = accentColor)
                                            }
                                        }
                                        if (!isLandscape) Spacer(modifier = Modifier.width(40.dp))
                                    }
                                    
                                    Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    
                                    // Grid Cells
                                    Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                                        gridHours.forEach { hour ->
                                            Row(modifier = Modifier.height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                                                gridDays.forEachIndexed { colIndex, day ->
                                                    val isSelected = selectedGridCells.contains(Pair(day, hour))
                                                    val isBooked = allBookedGridCells.contains(Pair(day, hour))
                                                    
                                                    val cellScale by animateFloatAsState(if (isSelected) 1f else 0.95f, spring(dampingRatio = 0.5f), label = "cellScale")
                                                    
                                                    val cellColor = if (isSelected) accentColor else if (isBooked) Color(0xFFD32F2F) else Color(0xFF388E3C)
                                                    val colBg = if (colIndex % 2 == 1) colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.Transparent
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .width(dayWidth)
                                                            .fillMaxHeight()
                                                            .background(colBg)
                                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .bounceClick(haptic) {
                                                                    if (isBooked && !isSelected) {
                                                                        Toast.makeText(context, "Warning: Slot is already booked by another class.", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                    val newSet = selectedGridCells.toMutableSet()
                                                                    val pair = Pair(day, hour)
                                                                    if(isSelected) newSet.remove(pair) else newSet.add(pair)
                                                                    selectedGridCells = newSet
                                                                }
                                                                .graphicsLayer {
                                                                    scaleX = cellScale
                                                                    scaleY = cellScale
                                                                }
                                                                .background(cellColor.copy(alpha = if (isSelected || isBooked) 1f else 0.15f), RoundedCornerShape(8.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isSelected) {
                                                                Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp))
                                                            } else if (isBooked) {
                                                                Icon(Icons.Default.Close, "Booked", tint = Color.White, modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                if (!isLandscape) Spacer(modifier = Modifier.width(40.dp))
                                            }
                                            Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Floating Close Button (Landscape only)
                        AnimatedVisibility(
                            visible = isLandscape,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { isGridFullScreenOpen = false },
                                containerColor = colorScheme.surface,
                                contentColor = colorScheme.onSurface,
                                modifier = Modifier.bounceClick(haptic) { isGridFullScreenOpen = false }.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Grid")
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(pre_content + new_dialog_code)
