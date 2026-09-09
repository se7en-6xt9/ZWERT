sed -i '113,126d' app/src/main/java/com/example/ui/screens/LoginScreen.kt
sed -i '112a\
                                    onSuccess = { hasProfile ->\
                                        isSigningIn = false\
                                        if (hasProfile) {\
                                            navController.navigate("faculty_dashboard") {\
                                                popUpTo("login") { inclusive = true }\
                                            }\
                                        } else {\
                                            navController.navigate("profile_setup") {\
                                                popUpTo("login") { inclusive = true }\
                                            }\
                                        }\
                                    },' app/src/main/java/com/example/ui/screens/LoginScreen.kt
