sed -i 's/Text("YT", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 48.sp)/Text(userProfile?.name?.takeIf { it.isNotBlank() }?.let { name -> name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("") } ?: "YT", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 48.sp)/' app/src/main/java/com/example/ui/screens/ProfileScreen.kt

sed -i 's/Text(\n                text = "Prof. Yash Thakur",/Text(\n                text = userProfile?.name?.takeIf { it.isNotBlank() } ?: "Prof. Yash Thakur",/' app/src/main/java/com/example/ui/screens/ProfileScreen.kt

sed -i 's/Text(\n                text = "Computer Science & Engineering",/Text(\n                text = userProfile?.subject?.takeIf { it.isNotBlank() } ?: "Computer Science \& Engineering",/' app/src/main/java/com/example/ui/screens/ProfileScreen.kt

sed -i '25a\
    var userProfile by remember { mutableStateOf<com.example.models.UserProfile?>(null) }\
    LaunchedEffect(Unit) {\
        viewModel.loadUserProfile { profile ->\
            userProfile = profile\
        }\
    }\
' app/src/main/java/com/example/ui/screens/ProfileScreen.kt
