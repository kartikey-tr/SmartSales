package com.torpedoes.smartsales.ui.screens.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torpedoes.smartsales.ui.theme.*
import com.torpedoes.smartsales.ui.viewmodel.AuthViewModel

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel     : AuthViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    var email    by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    val focusMgr = LocalFocusManager.current

    fun validate(): Boolean {
        return when {
            email.isBlank() -> { emailError = "Please enter your email address."; false }
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> { emailError = "Please enter a valid email address."; false }
            else -> { emailError = null; true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        IconButton(
            onClick  = onNavigateBack,
            modifier = Modifier
                .padding(top = 48.dp, start = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = OnSurfaceLight
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.resetEmailSent) {
                Text(text = "✉️", fontSize = 56.sp)
                Spacer(Modifier.height(16.dp))
                Text("Check your inbox", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Password reset link sent to\n$email",
                    fontSize  = 14.sp,
                    color     = OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick  = onNavigateBack,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                ) {
                    Text("Back to Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                }
            } else {
                Text("Forgot Password?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter your email and we'll send\na reset link right away.",
                    fontSize  = 14.sp,
                    color     = OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(32.dp))

                Card(
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = SurfaceMid),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value         = email,
                            onValueChange = { email = it; emailError = null; viewModel.clearError() },
                            label         = { Text("Email address") },
                            singleLine    = true,
                            isError       = emailError != null,
                            supportingText = emailError?.let { { Text(it, color = ErrorRed, fontSize = 12.sp) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction    = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusMgr.clearFocus()
                                    if (validate()) viewModel.sendPasswordReset(email)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            colors   = authFieldColors()
                        )

                        uiState.errorMessage?.let { msg ->
                            Text(text = msg, color = ErrorRed, fontSize = 12.sp)
                        }

                        Button(
                            onClick  = {
                                focusMgr.clearFocus()
                                if (validate()) viewModel.sendPasswordReset(email)
                            },
                            enabled  = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = BrandOrange)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(color = OnSurfaceLight, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Send Reset Link", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceLight)
                            }
                        }
                    }
                }
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
    errorBorderColor     = ErrorRed,
    errorLabelColor      = ErrorRed
)