import re

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "r") as f:
    content = f.read()

dialog_code = """
        if (isGridFullScreenOpen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isGridFullScreenOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                val activity = LocalContext.current as? android.app.Activity
                DisposableEffect(Unit) {
                    val original = activity?.requestedOrientation
                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    onDispose {
                        original?.let { activity.requestedOrientation = it }
                    }
                }
                
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()
                val density = androidx.compose.ui.platform.LocalDensity.current
                
                LaunchedEffect(Unit) {
                    // Scroll 8 hours down (8 * 52.dp)
                    val offset = with(density) { (8 * 52).dp.toPx() }.toInt()
                    verticalScrollState.scrollTo(offset)
                }
                
                Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isGridFullScreenOpen = false }, modifier = Modifier.privateBounceClick(haptic) { isGridFullScreenOpen = false }) {
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
                        
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // Left Sticky Column (Times)
                            Column(modifier = Modifier.width(140.dp)) {
                                Box(modifier = Modifier.height(44.dp)) // Header spacing
                                Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                                    gridHours.forEach { hour ->
                                        Box(
                                            modifier = Modifier.height(52.dp).fillMaxWidth().padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(hour, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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
                                Row(modifier = Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                                    gridDays.forEach { day ->
                                        Box(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                                .fillMaxHeight()
                                                .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(day, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = accentColor)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(40.dp))
                                }
                                
                                Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                // Grid Cells
                                Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                                    gridHours.forEach { hour ->
                                        Row(modifier = Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                                            gridDays.forEachIndexed { colIndex, day ->
                                                val isSelected = selectedGridCells.contains(Pair(day, hour))
                                                val isBooked = allBookedGridCells.contains(Pair(day, hour))
                                                
                                                val cellScale by animateFloatAsState(if (isSelected) 1f else 0.95f, spring(dampingRatio = 0.5f), label = "cellScale")
                                                
                                                val cellColor = if (isSelected) accentColor else if (isBooked) Color(0xFFD32F2F) else Color(0xFF388E3C)
                                                val colBg = if (colIndex % 2 == 1) colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.Transparent
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .width(100.dp)
                                                        .fillMaxHeight()
                                                        .background(colBg)
                                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .privateBounceClick(haptic) {
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
                                                            Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                                                        } else if (isBooked) {
                                                            Icon(Icons.Default.Close, "Booked", tint = Color.White, modifier = Modifier.size(20.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(40.dp))
                                        }
                                        Divider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
"""
content = content[:content.rindex("}")]
content = content[:content.rindex("}")]
content = content + dialog_code

with open("app/src/main/java/com/example/ui/screens/AddEditBatchScreen.kt", "w") as f:
    f.write(content)
