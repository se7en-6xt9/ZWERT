package com.example.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.navigation.NavController
import com.example.ui.util.SoundFeedbackHelper
import com.example.viewmodel.MainViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun getDeviceGoogleAccounts(context: Context): List<String> {
    return try {
        val am = AccountManager.get(context)
        am.getAccountsByType("com.google").mapNotNull { it.name }.filter { it.contains("@") }
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Auth State
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("student") } // "student" or "teacher"
    var authTab by remember { mutableStateOf(0) } // 0: Sign In, 1: Create Account

    // Form inputs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Registration inputs
    var fullName by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Student-specific fields
    var rollNumber by remember { mutableStateOf("") }
    var studentDepartment by remember { mutableStateOf("Computer Science & Engineering") }
    var semesterSection by remember { mutableStateOf("4th Semester • Section A") }

    // Faculty-specific fields
    var facultyId by remember { mutableStateOf("") }
    var facultyDepartment by remember { mutableStateOf("Computer Science & Engineering") }
    var facultyDesignation by remember { mutableStateOf("Assistant Professor") }
    var cabinRoomNo by remember { mutableStateOf("Block 3 • Room 402") }

    // Google Auth & Account Modal state
    var showGoogleRoleFallbackModal by remember { mutableStateOf(false) }
    var showGoogleAccountPickerModal by remember { mutableStateOf(false) }
    var googlePickerAction by remember { mutableStateOf("signin") } // "signin" or "signup"
    var googleAccountEmailInput by remember { mutableStateOf("") }
    var showEmailPasswordFields by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Google Account Picker Launcher for device accounts
    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSubmitting = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                googleAccountEmailInput = selectedEmail
                if (googlePickerAction == "signin") {
                    isSubmitting = true
                    viewModel.signInWithGoogleAccountEmail(
                        googleEmail = selectedEmail,
                        onSuccess = { hasProfile, role ->
                            isSubmitting = false
                            if (role.isNullOrBlank()) {
                                showGoogleRoleFallbackModal = true
                            } else {
                                val dest = if (role == "teacher") "faculty_dashboard" else "student_dashboard"
                                navController.navigate(dest) {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        },
                        onError = { msg ->
                            isSubmitting = false
                            showGoogleAccountPickerModal = true
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Google Sign-In: $msg")
                            }
                        }
                    )
                } else {
                    showGoogleAccountPickerModal = true
                }
            }
        }
    }

    val launchSystemAccountPicker: (String) -> Unit = { action ->
        googlePickerAction = action
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                false,
                null,
                null,
                null,
                null
            )
            googleAccountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("LoginScreen", "Cannot launch system account chooser: ${e.message}")
            showGoogleAccountPickerModal = true
        }
    }

    val primaryIndigo = Color(0xFF4F46E5)
    val accentMint = Color(0xFF10B981)
    val bgSoft = Color(0xFFF8FAFC)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF1F5F9),
                        Color(0xFFEEF2FF),
                        Color(0xFFF8FAFC)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. APP LOGO & HEADER
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = primaryIndigo.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Attentis Logo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Attentis",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E1B4B),
                letterSpacing = (-0.5).sp
            )

            Text(
                text = "Academic Management Suite",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // 2. ROLE SELECTOR SWITCH / SEGMENTED PILL
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Student Pill
                    val isStudent = selectedRole == "student"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (isStudent) Brush.horizontalGradient(
                                    listOf(accentMint, Color(0xFF059669))
                                ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedRole = "student"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎓", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Student",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isStudent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isStudent) Color.White else Color(0xFF64748B)
                            )
                        }
                    }

                    // Faculty Pill
                    val isFaculty = selectedRole == "teacher"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (isFaculty) Brush.horizontalGradient(
                                    listOf(primaryIndigo, Color(0xFF4338CA))
                                ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedRole = "teacher"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👨‍🏫", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Faculty / Teacher",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isFaculty) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFaculty) Color.White else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. SIGN IN WITH GOOGLE (OAuth)
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    val clientId = try {
                        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                        if (resId != 0) context.getString(resId) else ""
                    } catch (e: Exception) {
                        ""
                    }

                    val activity = context.findActivity()
                    if (activity != null && clientId.isNotBlank()) {
                        isSubmitting = true
                        coroutineScope.launch {
                            try {
                                val credentialManager = CredentialManager.create(activity)
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(clientId)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()
                                val result = credentialManager.getCredential(activity, request)
                                val credential = result.credential
                                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    val tokenCred = GoogleIdTokenCredential.createFrom(credential.data)
                                    viewModel.signInWithGoogleToken(
                                        tokenCred.idToken,
                                        onSuccess = { hasProfile, role ->
                                            isSubmitting = false
                                            if (role.isNullOrBlank()) {
                                                showGoogleRoleFallbackModal = true
                                            } else {
                                                val dest = if (role == "teacher") "faculty_dashboard" else "student_dashboard"
                                                navController.navigate(dest) {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            }
                                        },
                                        onError = {
                                            isSubmitting = false
                                            launchSystemAccountPicker("signin")
                                        }
                                    )
                                    return@launch
                                }
                                isSubmitting = false
                                launchSystemAccountPicker("signin")
                            } catch (e: Exception) {
                                isSubmitting = false
                                Log.d("LoginScreen", "CredentialManager fallback to account picker: ${e.message}")
                                launchSystemAccountPicker("signin")
                            }
                        }
                    } else {
                        launchSystemAccountPicker("signin")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(4.dp, RoundedCornerShape(27.dp), spotColor = Color(0xFF4F46E5).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1E293B)
                ),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                enabled = !isSubmitting
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("G", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF4285F4))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // DIVIDER: "or continue with email"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCBD5E1))
                Text(
                    text = "or continue with email",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCBD5E1))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. TABBED FORM CONTAINER [ Sign In ] | [ Create Account ]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Segmented Tabs: Sign In / Create Account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (authTab == 0) Color.White else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    authTab = 0
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (authTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (authTab == 0) Color(0xFF1E1B4B) else Color(0xFF64748B)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (authTab == 1) Color.White else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    authTab = 1
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Account",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (authTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (authTab == 1) Color(0xFF1E1B4B) else Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    AnimatedContent(
                        targetState = authTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(180))
                        },
                        label = "authFormTabs"
                    ) { tabIndex ->
                        if (tabIndex == 0) {
                            // SIGN IN FORM
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Institutional Email") },
                                    placeholder = { Text("e.g., student@university.edu") },
                                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = primaryIndigo) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryIndigo,
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = primaryIndigo) },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password",
                                                tint = Color(0xFF94A3B8)
                                            )
                                        }
                                    },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryIndigo,
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        val cleanEmail = email.trim().replace("\\s+".toRegex(), "")
                                        val cleanPass = password.trim()
                                        if (cleanEmail.isBlank() || cleanPass.isBlank()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Please enter your email and password.")
                                            }
                                            return@Button
                                        }
                                        val normalizedEmail = if (!cleanEmail.contains("@")) {
                                            "$cleanEmail@campus.edu"
                                        } else {
                                            cleanEmail
                                        }
                                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Please enter a valid email address (e.g. name@university.edu)")
                                            }
                                            return@Button
                                        }
                                        isSubmitting = true
                                        viewModel.signInWithEmailAndPassword(
                                            email = normalizedEmail,
                                            pass = cleanPass,
                                            onSuccess = { hasProfile, role ->
                                                isSubmitting = false
                                                viewModel.setUserRole(selectedRole)
                                                val dest = if (selectedRole == "teacher") "faculty_dashboard" else "student_dashboard"
                                                navController.navigate(dest) {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            },
                                            onError = { err ->
                                                isSubmitting = false
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Sign in error: $err")
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = primaryIndigo.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedRole == "student") accentMint else primaryIndigo,
                                        contentColor = Color.White
                                    ),
                                    enabled = !isSubmitting
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                    } else {
                                        Text(
                                            text = if (selectedRole == "student") "Sign In as Student" else "Sign In as Faculty",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // CREATE ACCOUNT FORM (ROLE-SPECIFIC FIELDS)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Full Name
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Full Name") },
                                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = primaryIndigo) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = FocusDirection.Down.let { ImeAction.Next }),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Email
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Institutional Email") },
                                    placeholder = { Text(if (selectedRole == "student") "e.g., student@university.edu" else "e.g., prof.name@university.edu") },
                                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = primaryIndigo) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                if (selectedRole == "student") {
                                    // Student Specific: Roll Number / Student ID
                                    OutlinedTextField(
                                        value = rollNumber,
                                        onValueChange = { rollNumber = it },
                                        label = { Text("Roll Number / Student ID") },
                                        placeholder = { Text("e.g., 24BCS025") },
                                        leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null, tint = accentMint) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Student Specific: Department / Branch
                                    OutlinedTextField(
                                        value = studentDepartment,
                                        onValueChange = { studentDepartment = it },
                                        label = { Text("Department / Branch") },
                                        placeholder = { Text("e.g., Computer Science") },
                                        leadingIcon = { Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = accentMint) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Student Specific: Semester & Section
                                    OutlinedTextField(
                                        value = semesterSection,
                                        onValueChange = { semesterSection = it },
                                        label = { Text("Semester & Section") },
                                        placeholder = { Text("e.g., Sem 4 - Sec A") },
                                        leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null, tint = accentMint) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                } else {
                                    // Faculty Specific: Faculty ID
                                    OutlinedTextField(
                                        value = facultyId,
                                        onValueChange = { facultyId = it },
                                        label = { Text("Faculty / Employee ID") },
                                        placeholder = { Text("e.g., FAC-CSE-104") },
                                        leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null, tint = primaryIndigo) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Faculty Specific: Department
                                    OutlinedTextField(
                                        value = facultyDepartment,
                                        onValueChange = { facultyDepartment = it },
                                        label = { Text("Department") },
                                        placeholder = { Text("e.g., Computer Science & Eng.") },
                                        leadingIcon = { Icon(Icons.Outlined.Domain, contentDescription = null, tint = primaryIndigo) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Faculty Specific: Designation
                                    OutlinedTextField(
                                        value = facultyDesignation,
                                        onValueChange = { facultyDesignation = it },
                                        label = { Text("Academic Designation") },
                                        placeholder = { Text("e.g., Assistant Professor") },
                                        leadingIcon = { Icon(Icons.Outlined.WorkOutline, contentDescription = null, tint = primaryIndigo) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Faculty Specific: Cabin / Room No.
                                    OutlinedTextField(
                                        value = cabinRoomNo,
                                        onValueChange = { cabinRoomNo = it },
                                        label = { Text("Cabin / Room Number") },
                                        placeholder = { Text("e.g., Academic Block 3, Room 402") },
                                        leadingIcon = { Icon(Icons.Outlined.MeetingRoom, contentDescription = null, tint = primaryIndigo) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Google Account Binding Info Card
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selectedRole == "student") Color(0xFFF0FDF4) else Color(0xFFEEF2FF),
                                    border = BorderStroke(1.dp, if (selectedRole == "student") accentMint.copy(alpha = 0.3f) else primaryIndigo.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🔒", fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Google Account Binding",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedRole == "student") Color(0xFF065F46) else Color(0xFF312E81)
                                            )
                                            Text(
                                                text = "Your academic profile will be securely bound to your Google account. Next time, log in instantly with 1-tap Google Sign-In.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (selectedRole == "student") Color(0xFF047857) else Color(0xFF4338CA)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // PRIMARY ACTION: Sign Up with Google
                                Button(
                                    onClick = {
                                        if (isSubmitting) return@Button
                                        val cleanName = fullName.trim()
                                        val idVal = if (selectedRole == "student") rollNumber.trim() else facultyId.trim()
                                        val deptVal = if (selectedRole == "student") studentDepartment.trim() else facultyDepartment.trim()
                                        val ext1 = if (selectedRole == "student") semesterSection.trim() else facultyDesignation.trim()
                                        val ext2 = if (selectedRole == "student") rollNumber.trim() else cabinRoomNo.trim()

                                        if (cleanName.isBlank()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Please enter your full name.")
                                            }
                                            return@Button
                                        }
                                        if (idVal.isBlank()) {
                                            val idLabel = if (selectedRole == "student") "Roll Number" else "Faculty ID"
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Please enter your $idLabel.")
                                            }
                                            return@Button
                                        }

                                        val clientId = try {
                                            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                                            if (resId != 0) context.getString(resId) else ""
                                        } catch (e: Exception) {
                                            ""
                                        }

                                        val activity = context.findActivity()
                                        if (activity != null && clientId.isNotBlank()) {
                                            isSubmitting = true
                                            coroutineScope.launch {
                                                try {
                                                    val credentialManager = CredentialManager.create(activity)
                                                    val googleIdOption = GetGoogleIdOption.Builder()
                                                        .setFilterByAuthorizedAccounts(false)
                                                        .setServerClientId(clientId)
                                                        .setAutoSelectEnabled(false)
                                                        .build()
                                                    val request = GetCredentialRequest.Builder()
                                                        .addCredentialOption(googleIdOption)
                                                        .build()
                                                    val result = credentialManager.getCredential(activity, request)
                                                    val credential = result.credential
                                                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                                        val tokenCred = GoogleIdTokenCredential.createFrom(credential.data)
                                                        viewModel.signUpAndBindGoogleAccount(
                                                            idToken = tokenCred.idToken,
                                                            googleEmailFallback = null,
                                                            role = selectedRole,
                                                            name = cleanName,
                                                            rollOrEmpId = idVal,
                                                            department = deptVal,
                                                            extra1 = ext1,
                                                            extra2 = ext2,
                                                            onSuccess = { role ->
                                                                isSubmitting = false
                                                                val dest = if (role == "teacher") "faculty_dashboard" else "student_dashboard"
                                                                navController.navigate(dest) {
                                                                    popUpTo("login") { inclusive = true }
                                                                }
                                                            },
                                                            onError = { err ->
                                                                isSubmitting = false
                                                                launchSystemAccountPicker("signup")
                                                            }
                                                        )
                                                        return@launch
                                                    }
                                                    isSubmitting = false
                                                    launchSystemAccountPicker("signup")
                                                } catch (e: Exception) {
                                                    isSubmitting = false
                                                    Log.e("LoginScreen", "Google signup exception: ${e.message}", e)
                                                    launchSystemAccountPicker("signup")
                                                }
                                            }
                                        } else {
                                            launchSystemAccountPicker("signup")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = if (selectedRole == "student") accentMint.copy(alpha = 0.4f) else primaryIndigo.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedRole == "student") accentMint else primaryIndigo,
                                        contentColor = Color.White
                                    ),
                                    enabled = !isSubmitting
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(26.dp),
                                                shape = CircleShape,
                                                color = Color.White
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("G", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF4285F4))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = if (selectedRole == "student") "Sign Up with Google (Student)" else "Sign Up with Google (Faculty)",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Toggle for manual email/password
                                TextButton(
                                    onClick = { showEmailPasswordFields = !showEmailPasswordFields },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (showEmailPasswordFields) "Hide Email & Password options" else "Or create account with Email & Password",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                    Icon(
                                        imageVector = if (showEmailPasswordFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                AnimatedVisibility(visible = showEmailPasswordFields) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(modifier = Modifier.height(6.dp))

                                        OutlinedTextField(
                                            value = email,
                                            onValueChange = { email = it },
                                            label = { Text("Institutional Email") },
                                            placeholder = { Text("e.g., student@university.edu") },
                                            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = primaryIndigo) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            label = { Text("Password (min 6 characters)") },
                                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = primaryIndigo) },
                                            trailingIcon = {
                                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                    Icon(
                                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle password",
                                                        tint = Color(0xFF94A3B8)
                                                    )
                                                }
                                            },
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        OutlinedTextField(
                                            value = confirmPassword,
                                            onValueChange = { confirmPassword = it },
                                            label = { Text("Confirm Password") },
                                            leadingIcon = { Icon(Icons.Outlined.LockReset, contentDescription = null, tint = primaryIndigo) },
                                            trailingIcon = {
                                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                                    Icon(
                                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle confirm password",
                                                        tint = Color(0xFF94A3B8)
                                                    )
                                                }
                                            },
                                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        OutlinedButton(
                                            onClick = {
                                                val cleanEmail = email.trim().replace("\\s+".toRegex(), "")
                                                val cleanPass = password.trim()
                                                val cleanConfirm = confirmPassword.trim()
                                                val cleanName = fullName.trim()

                                                if (cleanName.isBlank() || cleanEmail.isBlank() || cleanPass.isBlank()) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Please fill in all required fields.")
                                                    }
                                                    return@OutlinedButton
                                                }

                                                val normalizedEmail = if (!cleanEmail.contains("@")) {
                                                    "$cleanEmail@campus.edu"
                                                } else {
                                                    cleanEmail
                                                }

                                                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Please enter a valid email address.")
                                                    }
                                                    return@OutlinedButton
                                                }

                                                if (cleanPass.length < 6) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Password must be at least 6 characters.")
                                                    }
                                                    return@OutlinedButton
                                                }

                                                if (cleanPass != cleanConfirm) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Passwords do not match.")
                                                    }
                                                    return@OutlinedButton
                                                }

                                                isSubmitting = true
                                                val idVal = if (selectedRole == "student") rollNumber.trim().ifBlank { "24BCS025" } else facultyId.trim().ifBlank { "FAC-001" }
                                                val deptVal = if (selectedRole == "student") studentDepartment.trim() else facultyDepartment.trim()
                                                val ext1 = if (selectedRole == "student") semesterSection.trim() else facultyDesignation.trim()
                                                val ext2 = if (selectedRole == "student") rollNumber.trim() else cabinRoomNo.trim()

                                                viewModel.signUpWithEmailAndPassword(
                                                    email = normalizedEmail,
                                                    pass = cleanPass,
                                                    name = cleanName,
                                                    role = selectedRole,
                                                    rollOrEmpId = idVal,
                                                    department = deptVal,
                                                    extra1 = ext1,
                                                    extra2 = ext2,
                                                    onSuccess = {
                                                        isSubmitting = false
                                                        val dest = if (selectedRole == "teacher") "faculty_dashboard" else "student_dashboard"
                                                        navController.navigate(dest) {
                                                            popUpTo("login") { inclusive = true }
                                                        }
                                                    },
                                                    onError = { err ->
                                                        isSubmitting = false
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Registration error: $err")
                                                        }
                                                    }
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, if (selectedRole == "student") accentMint else primaryIndigo),
                                            enabled = !isSubmitting
                                        ) {
                                            Text(
                                                text = "Register with Password",
                                                color = if (selectedRole == "student") accentMint else primaryIndigo,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // GOOGLE AUTH FALLBACK MODAL: COMPLETE YOUR PROFILE
        if (showGoogleRoleFallbackModal) {
            ModalBottomSheet(
                onDismissRequest = { showGoogleRoleFallbackModal = false },
                sheetState = sheetState,
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = primaryIndigo,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Complete Your Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E1B4B)
                    )

                    Text(
                        text = "Select your academic role to tailor your courses, schedules, and attendance portal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Card 1: Student Role Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showGoogleRoleFallbackModal = false
                                viewModel.setUserRole("student") {
                                    navController.navigate("student_dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.5.dp, accentMint.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(accentMint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎓", fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "I am a Student",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                                Text(
                                    text = "View lectures, mark self-attendance, and check aggregate percentages.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF047857)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accentMint)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Card 2: Faculty Role Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showGoogleRoleFallbackModal = false
                                viewModel.setUserRole("teacher") {
                                    navController.navigate("faculty_dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.5.dp, primaryIndigo.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(primaryIndigo.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👨‍🏫", fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "I am a Teacher",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF312E81)
                                )
                                Text(
                                    text = "Manage batches, import schedules, register attendance & export reports.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4338CA)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = primaryIndigo)
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // GOOGLE ACCOUNT PICKER & BINDING MODAL
        if (showGoogleAccountPickerModal) {
            ModalBottomSheet(
                onDismissRequest = { showGoogleAccountPickerModal = false },
                sheetState = pickerSheetState,
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("G", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color(0xFF4285F4))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (googlePickerAction == "signin") "Sign in with Google" else "Link Google Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E1B4B)
                    )

                    Text(
                        text = if (googlePickerAction == "signin")
                            "Sign in with your Google account. Attentis backend will automatically detect your role and load your isolated data."
                        else
                            "Bind your academic profile to your Google account. Your data will be isolated and accessible via 1-tap Google login.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (googlePickerAction == "signup") {
                        // Profile summary preview
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedRole == "student") Color(0xFFF0FDF4) else Color(0xFFEEF2FF),
                            border = BorderStroke(1.dp, if (selectedRole == "student") accentMint.copy(alpha = 0.3f) else primaryIndigo.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Profile To Bind",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selectedRole == "student") Color(0xFF065F46) else Color(0xFF312E81)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selectedRole == "student") accentMint else primaryIndigo
                                    ) {
                                        Text(
                                            text = if (selectedRole == "student") "🎓 Student" else "👨‍🏫 Faculty",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Name: ${fullName.trim().ifBlank { "User" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                                val idLabel = if (selectedRole == "student") "Roll No: ${rollNumber.trim().ifBlank { "24BCS025" }}" else "Faculty ID: ${facultyId.trim().ifBlank { "FAC-001" }}"
                                Text(
                                    text = idLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569)
                                )
                                val deptLabel = if (selectedRole == "student") studentDepartment else facultyDepartment
                                Text(
                                    text = "Department: $deptLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    val detectedAccounts = remember { getDeviceGoogleAccounts(context) }

                    // Button to launch native Android system account chooser
                    OutlinedButton(
                        onClick = {
                            launchSystemAccountPicker(googlePickerAction)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF8FAFC)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("G", fontWeight = FontWeight.Black, fontSize = 19.sp, color = Color(0xFF4285F4))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Select Account from Device",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }

                    if (detectedAccounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Detected Accounts on Device:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        detectedAccounts.forEach { accEmail ->
                            val isChosen = googleAccountEmailInput.equals(accEmail, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        googleAccountEmailInput = accEmail
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChosen) Color(0xFFEEF2FF) else Color(0xFFF1F5F9),
                                border = BorderStroke(
                                    1.dp,
                                    if (isChosen) primaryIndigo else Color(0xFFE2E8F0)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = if (isChosen) primaryIndigo else Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = accEmail,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isChosen) primaryIndigo else Color(0xFF1E293B),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isChosen) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = primaryIndigo,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Google Account Input / Selection Field
                    OutlinedTextField(
                        value = googleAccountEmailInput,
                        onValueChange = { googleAccountEmailInput = it },
                        label = { Text("Google Account Email") },
                        leadingIcon = {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = primaryIndigo)
                        },
                        trailingIcon = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE0E7FF),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "Google",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryIndigo,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Button
                    Button(
                        onClick = {
                            val cleanGoogleEmail = googleAccountEmailInput.trim()
                            if (cleanGoogleEmail.isBlank() || !cleanGoogleEmail.contains("@")) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Please enter a valid Google Account email.")
                                }
                                return@Button
                            }

                            if (googlePickerAction == "signin") {
                                isSubmitting = true
                                showGoogleAccountPickerModal = false
                                viewModel.signInWithGoogleAccountEmail(
                                    googleEmail = cleanGoogleEmail,
                                    onSuccess = { hasProfile, role ->
                                        isSubmitting = false
                                        if (role.isNullOrBlank()) {
                                            showGoogleRoleFallbackModal = true
                                        } else {
                                            val dest = if (role == "teacher") "faculty_dashboard" else "student_dashboard"
                                            navController.navigate(dest) {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    },
                                    onError = { msg ->
                                        isSubmitting = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Google Sign-In: $msg")
                                        }
                                    }
                                )
                            } else {
                                // SIGN UP FLOW: Bind profile to Google Account
                                val cleanName = fullName.trim().ifBlank { "User" }
                                val idVal = if (selectedRole == "student") rollNumber.trim().ifBlank { "24BCS025" } else facultyId.trim().ifBlank { "FAC-001" }
                                val deptVal = if (selectedRole == "student") studentDepartment.trim() else facultyDepartment.trim()
                                val ext1 = if (selectedRole == "student") semesterSection.trim() else facultyDesignation.trim()
                                val ext2 = if (selectedRole == "student") rollNumber.trim() else cabinRoomNo.trim()

                                isSubmitting = true
                                showGoogleAccountPickerModal = false
                                viewModel.signUpAndBindGoogleAccount(
                                    idToken = null,
                                    googleEmailFallback = cleanGoogleEmail,
                                    role = selectedRole,
                                    name = cleanName,
                                    rollOrEmpId = idVal,
                                    department = deptVal,
                                    extra1 = ext1,
                                    extra2 = ext2,
                                    onSuccess = { role ->
                                        isSubmitting = false
                                        val dest = if (role == "teacher") "faculty_dashboard" else "student_dashboard"
                                        navController.navigate(dest) {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    },
                                    onError = { err ->
                                        isSubmitting = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Google registration error: $err")
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = primaryIndigo.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == "student" && googlePickerAction == "signup") accentMint else primaryIndigo,
                            contentColor = Color.White
                        ),
                        enabled = !isSubmitting
                    ) {
                        Text(
                            text = if (googlePickerAction == "signin") "Sign in with Google Account" else "Confirm & Bind to Google Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
