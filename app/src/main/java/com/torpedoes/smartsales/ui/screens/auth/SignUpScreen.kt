package com.torpedoes.smartsales.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun SignUpScreen(
    onSignUpSuccess   : () -> Unit,
    onNavigateToLogin : (prefillEmail: String) -> Unit,
    viewModel         : AuthViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    var name     by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focusMgr = LocalFocusManager.current

    // One-shot navigation — fires exactly once after Firebase confirms success
    LaunchedEffect(Unit) {
        viewModel.signUpSuccess.collect { onSignUpSuccess() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Text("SmartSales", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = BrandOrange)
            Text("Create your account", fontSize = 14.sp, color = OnSurfaceMuted)

            Spacer(Modifier.height(8.dp))

            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = SurfaceMid),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Full Name
                    OutlinedTextField(
                        value         = name,
                        onValueChange = { name = it; viewModel.clearSignUpError() },
                        label         = { Text("Full Name") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusMgr.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                        colors   = authFieldColors()
                    )

                    // Email
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it; viewModel.clearSignUpError() },
                        label         = { Text("Email") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusMgr.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                        colors   = authFieldColors()
                    )

                    // Password
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; viewModel.clearSignUpError() },
                        label         = { Text("Password") },
                        singleLine    = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusMgr.clearFocus()
                            viewModel.signUp(name, email, password)
                        }),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    imageVector = if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPass) "Hide" else "Show",
                                    tint = OnSurfaceMuted
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = authFieldColors()
                    )

                    // Email already exists prompt
                    if (uiState.emailAlreadyExists) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BrandOrange.copy(alpha = 0.08f))
                                .border(1.dp, BrandOrange.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text("This email is already registered.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                            Spacer(Modifier.height(4.dp))
                            Text("Want to log in with this email instead?", fontSize = 11.sp, color = OnSurfaceMuted)
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { onNavigateToLogin(email) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Log In instead →", fontSize = 12.sp, color = BrandOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Generic error
                    uiState.errorMessage?.let { msg ->
                        Text(text = msg, color = ErrorRed, fontSize = 12.sp)
                    }

                    // Sign Up button
                    Button(
                        onClick  = { focusMgr.clearFocus(); viewModel.signUp(name, email, password) },
                        enabled  = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = OnSurfaceLight, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                        }
                    }
                }
            }

            // Already have an account?
            TextButton(onClick = { onNavigateToLogin("") }) {
                Text(
                    buildAnnotatedString {
                        append("Already have an account? ")
                        withStyle(SpanStyle(color = BrandOrange, fontWeight = FontWeight.SemiBold)) { append("Sign In") }
                    },
                    textAlign = TextAlign.Center,
                    fontSize  = 12.sp,
                    color     = OnSurfaceMuted
                )
            }

            Spacer(Modifier.height(24.dp))
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