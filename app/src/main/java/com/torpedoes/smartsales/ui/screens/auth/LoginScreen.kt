package com.torpedoes.smartsales.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onNavigateToDashboard     : () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToSignUp        : () -> Unit,
    prefillEmail              : String = "",
    viewModel                 : AuthViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    var email    by remember { mutableStateOf(prefillEmail) }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focusMgr = LocalFocusManager.current

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onNavigateToDashboard()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo area
            Text(
                text       = "SmartSales",
                fontSize   = 36.sp,
                fontWeight = FontWeight.Bold,
                color      = BrandOrange
            )
            Text(
                text     = "Sign in to your account",
                fontSize = 14.sp,
                color    = OnSurfaceMuted
            )

            Spacer(Modifier.height(8.dp))

            // Card
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = SurfaceMid),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Email field
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it; viewModel.clearError() },
                        label         = { Text("Email") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusMgr.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors   = authFieldColors()
                    )

                    // Password field
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; viewModel.clearError() },
                        label         = { Text("Password") },
                        singleLine    = true,
                        visualTransformation = if (showPass)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusMgr.clearFocus()
                                viewModel.login(email, password)
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    imageVector = if (showPass)
                                        Icons.Default.VisibilityOff
                                    else
                                        Icons.Default.Visibility,
                                    contentDescription = if (showPass) "Hide" else "Show",
                                    tint = OnSurfaceMuted
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = authFieldColors()
                    )

                    // Error message
                    uiState.errorMessage?.let { msg ->
                        Text(text = msg, color = ErrorRed, fontSize = 12.sp)
                    }

                    // Forgot password
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(onClick = onNavigateToForgotPassword) {
                            Text(
                                text     = "Forgot Password?",
                                color    = BrandOrange,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Login button
                    Button(
                        onClick  = { focusMgr.clearFocus(); viewModel.login(email, password) },
                        enabled  = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape  = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color       = OnSurfaceLight,
                                modifier    = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text       = "Sign In",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = OnSurfaceLight
                            )
                        }
                    }
                }
            }

            // Sign up prompt
            TextButton(onClick = onNavigateToSignUp) {
                Text(
                    buildAnnotatedString {
                        append("Don't have an account? ")
                        withStyle(SpanStyle(color = BrandOrange, fontWeight = FontWeight.SemiBold)) {
                            append("Sign Up")
                        }
                    },
                    textAlign = TextAlign.Center,
                    fontSize  = 13.sp,
                    color     = OnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = BrandOrange,
    unfocusedBorderColor = OnSurfaceMuted,
    focusedLabelColor    = BrandOrange,
    unfocusedLabelColor  = OnSurfaceMuted,
    cursorColor          = BrandOrange,
    focusedTextColor     = OnSurfaceLight,
    unfocusedTextColor   = OnSurfaceLight,
)