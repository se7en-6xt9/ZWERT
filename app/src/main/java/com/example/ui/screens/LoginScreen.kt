package com.example.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.navigation.NavController
import com.example.viewmodel.MainViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSigningIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Attentis",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Academic Management Suite",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 64.dp)
        )

        Button(
            onClick = {
                val clientId = try {
                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    if (resId != 0) context.getString(resId) else ""
                } catch (e: Exception) {
                    ""
                }
                if (clientId.isBlank()) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Production Google Sign-In requires Client ID configuration. Use the Demo accounts below to test.")
                    }
                    return@Button
                }
                
                isSigningIn = true
                coroutineScope.launch {
                    try {
                        val credentialManager = CredentialManager.create(context)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(clientId)
                            .setAutoSelectEnabled(false)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()
                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential
                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                viewModel.signInWithGoogleToken(
                                    googleIdTokenCredential.idToken,
                                    onSuccess = { hasProfile ->
                                        isSigningIn = false
                                        if (hasProfile) {
                                            navController.navigate("faculty_dashboard") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate("profile_setup") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    },
                                    onError = { msg ->
                                        isSigningIn = false
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Sign in failed: $msg") }
                                    }
                                )
                            } catch (e: GoogleIdTokenParsingException) {
                                isSigningIn = false
                                Log.e("LoginScreen", "Received an invalid google id token response", e)
                                snackbarHostState.showSnackbar("Invalid token response")
                            }
                        } else {
                            isSigningIn = false
                            Log.e("LoginScreen", "Unexpected type of credential")
                            snackbarHostState.showSnackbar("Unexpected credential type")
                        }
                    } catch (e: GetCredentialException) {
                        isSigningIn = false
                        Log.e("LoginScreen", "GetCredentialException", e)
                        snackbarHostState.showSnackbar("Google Sign-In failed or was cancelled.")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = !isSigningIn
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Sign in with Google", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "OR TRY DEMO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(modifier = Modifier.height(32.dp))

        ElevatedButton(
            onClick = {
                if (isSigningIn) return@ElevatedButton
                isSigningIn = true
                viewModel.loginAsDemoFaculty {
                    isSigningIn = false
                    navController.navigate("faculty_dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(56.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color.White,
                contentColor = Color.DarkGray
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            ),
            shape = RoundedCornerShape(28.dp),
            enabled = !isSigningIn
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Text("Demo Faculty Account", fontWeight = FontWeight.SemiBold)
        }

        ElevatedButton(
            onClick = {
                if (isSigningIn) return@ElevatedButton
                isSigningIn = true
                viewModel.loginAsDemoStudent {
                    isSigningIn = false
                    navController.navigate("student_dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(56.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color.White,
                contentColor = Color.DarkGray
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            ),
            shape = RoundedCornerShape(28.dp),
            enabled = !isSigningIn
        ) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Text("Demo Student Account", fontWeight = FontWeight.SemiBold)
        }
    }
}
