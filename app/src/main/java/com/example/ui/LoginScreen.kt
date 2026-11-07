package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var isSignUpMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Form inputs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Error states
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    isLoading = true
                    viewModel.signInWithGoogleToken(idToken) { success, error ->
                        isLoading = false
                        if (!success && error != null) {
                            emailError = error
                        }
                    }
                } else {
                    emailError = "Google Authentication: Token is null"
                }
            } catch (e: Exception) {
                emailError = "Google Sign-In failed: ${e.localizedMessage}"
            }
        } else {
            emailError = "Google Sign-In cancelled or failed"
        }
    }

    val gso = remember {
        val clientIdResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val clientId = if (clientIdResId != 0) context.getString(clientIdResId) else "your_client_id"
        com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember {
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }

    // Forgot password flow
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetEmailError by remember { mutableStateOf<String?>(null) }
    var resetSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Validate email format
    fun isValidEmail(target: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    // Clear all validation errors
    fun clearErrors() {
        emailError = null
        passwordError = null
        nameError = null
        confirmPasswordError = null
    }

    fun handleLogin() {
        clearErrors()
        var hasError = false

        if (email.isBlank()) {
            emailError = "Email cannot be empty"
            hasError = true
        } else if (!isValidEmail(email)) {
            emailError = "Invalid email format"
            hasError = true
        }

        if (password.isBlank()) {
            passwordError = "Password cannot be empty"
            hasError = true
        } else if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            hasError = true
        }

        if (!hasError) {
            isLoading = true
            viewModel.signInWithEmailAndPassword(email, password) { success, error ->
                isLoading = false
                if (!success && error != null) {
                    passwordError = error
                }
            }
        }
    }

    fun handleSignUp() {
        clearErrors()
        var hasError = false

        if (name.isBlank()) {
            nameError = "Name cannot be empty"
            hasError = true
        }

        if (email.isBlank()) {
            emailError = "Email cannot be empty"
            hasError = true
        } else if (!isValidEmail(email)) {
            emailError = "Invalid email format"
            hasError = true
        }

        if (password.isBlank()) {
            passwordError = "Password cannot be empty"
            hasError = true
        } else if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            hasError = true
        }

        if (confirmPassword != password) {
            confirmPasswordError = "Passwords do not match"
            hasError = true
        }

        if (!hasError) {
            isLoading = true
            viewModel.signUpWithEmailAndPassword(email, password, name) { success, error ->
                isLoading = false
                if (!success && error != null) {
                    confirmPasswordError = error
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .testTag("login_form_container"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Modern Animated Logo Badge
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(HeroGradient)
                    .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = size.minDimension / 1.4f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = size.minDimension / 2.0f,
                        center = center
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "ARLingua Logo",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ARLingua",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Augmented Reality Multilingual Tutor",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Master 12+ languages with real-time AR object scanning, AI conversations, and instant speech feedback.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Glass Container Card
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // Tab Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { 
                                    isSignUpMode = false 
                                    clearErrors()
                                }
                                .padding(vertical = 12.dp)
                                .testTag("tab_login"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Sign In",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (!isSignUpMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { 
                                    isSignUpMode = true 
                                    clearErrors()
                                }
                                .padding(vertical = 12.dp)
                                .testTag("tab_signup"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Sign Up",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSignUpMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Form Fields
                    AnimatedVisibility(
                        visible = isSignUpMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Your Full Name",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = name,
                                onValueChange = { 
                                    name = it
                                    if (nameError != null) nameError = null
                                },
                                placeholder = { Text("e.g. Jean-Luc Picard") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                isError = nameError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_name"),
                                colors = AppTextFieldDefaults.outlinedColors()
                            )
                            if (nameError != null) {
                                Text(nameError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Email Address",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { 
                                email = it
                                if (emailError != null) emailError = null
                            },
                            placeholder = { Text("you@example.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            isError = emailError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_email"),
                            colors = AppTextFieldDefaults.outlinedColors()
                        )
                        if (emailError != null) {
                            Text(emailError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Password",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { 
                                password = it
                                if (passwordError != null) passwordError = null
                            },
                            placeholder = { Text("••••••••") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (isSignUpMode) ImeAction.Next else ImeAction.Done),
                            isError = passwordError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_password"),
                            colors = AppTextFieldDefaults.outlinedColors()
                        )
                        if (passwordError != null) {
                            Text(passwordError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    AnimatedVisibility(
                        visible = isSignUpMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Confirm Password",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { 
                                    confirmPassword = it
                                    if (confirmPasswordError != null) confirmPasswordError = null
                                },
                                placeholder = { Text("••••••••") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                isError = confirmPasswordError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_confirm_password"),
                                colors = AppTextFieldDefaults.outlinedColors()
                            )
                            if (confirmPasswordError != null) {
                                Text(confirmPasswordError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }

                    // Forgot Password option (Only show in login mode)
                    if (!isSignUpMode) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "Forgot Password?",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { showForgotPasswordDialog = true }
                                    .testTag("forgot_password_button")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ACTION BUTTON (Gradient)
                    GradientButton(
                        text = if (isSignUpMode) "Create Account" else "Sign In",
                        onClick = {
                            if (isSignUpMode) handleSignUp() else handleLogin()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_button"),
                        gradient = EmeraldGradient,
                        isLoading = isLoading
                    )
                }
            }

            // --- SOCIAL SIGN IN ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "or continue with",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real Google login
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            try {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            } catch (e: Exception) {
                                emailError = "Google Sign-In failed: ${e.localizedMessage}"
                            }
                        }
                        .testTag("social_google_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlternateEmail,
                            contentDescription = "Google",
                            tint = Color(0xFFEA4335),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Mock GitHub login
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            scope.launch {
                                isLoading = true
                                delay(1200)
                                isLoading = false
                                viewModel.loginUser("github.coder@github.com", "GitHub Developer")
                            }
                        }
                        .testTag("social_github_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "GitHub",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GitHub", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- FORGOT PASSWORD DIALOG ---
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showForgotPasswordDialog = false
                resetEmail = ""
                resetEmailError = null
                resetSuccessMessage = null
            },
            title = {
                Text("Reset Password", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Enter your registered email address to receive password reset instructions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (resetSuccessMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = resetSuccessMessage!!,
                                color = EmeraldDark,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { 
                                resetEmail = it
                                if (resetEmailError != null) resetEmailError = null
                            },
                            placeholder = { Text("you@example.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            singleLine = true,
                            isError = resetEmailError != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_password_email_input"),
                            colors = AppTextFieldDefaults.outlinedColors()
                        )
                        if (resetEmailError != null) {
                            Text(resetEmailError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (resetSuccessMessage == null) {
                    Button(
                        onClick = {
                            if (resetEmail.isBlank() || !isValidEmail(resetEmail)) {
                                resetEmailError = "Enter a valid email address"
                            } else {
                                isLoading = true
                                viewModel.sendPasswordReset(resetEmail) { success, error ->
                                    isLoading = false
                                    if (success) {
                                        resetSuccessMessage = "Success! Password reset instructions sent to $resetEmail"
                                    } else {
                                        resetEmailError = error ?: "Failed to send reset email"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("forgot_password_submit_btn")
                    ) {
                        Text("Send Link", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { 
                            showForgotPasswordDialog = false
                            resetEmail = ""
                            resetEmailError = null
                            resetSuccessMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (resetSuccessMessage == null) {
                    TextButton(
                        onClick = { 
                            showForgotPasswordDialog = false
                            resetEmail = ""
                            resetEmailError = null
                        }
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

