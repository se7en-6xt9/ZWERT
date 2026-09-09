sed -i '/Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {/i \
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {\
                        Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)\
                        Spacer(modifier = Modifier.width(16.dp))\
                        Column {\
                            Text("Institute", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)\
                            Text(userProfile?.institute?.takeIf { it.isNotBlank() } ?: "Not Set", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)\
                        }\
                    }\
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))' app/src/main/java/com/example/ui/screens/ProfileScreen.kt
