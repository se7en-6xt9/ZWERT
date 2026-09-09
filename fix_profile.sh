sed -i '25,30d' app/src/main/java/com/example/ui/screens/ProfileScreen.kt
sed -i '/val context =/a \
    var userProfile by remember { mutableStateOf<com.example.models.UserProfile?>(null) }\
    LaunchedEffect(Unit) {\
        viewModel.loadUserProfile { profile ->\
            userProfile = profile\
        }\
    }' app/src/main/java/com/example/ui/screens/ProfileScreen.kt
