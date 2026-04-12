package com.torpedoes.smartsales.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.torpedoes.smartsales.ui.theme.BrandOrange
import com.torpedoes.smartsales.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateToLogin     : () -> Unit,
    onNavigateToDashboard : () -> Unit      // ← new: skip login if already signed in
) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                scale.animateTo(
                    targetValue   = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessLow
                    )
                )
            }
            alpha.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 600)
            )
        }
        delay(2000)

        // Route based on whether Firebase session is still active
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            onNavigateToDashboard()
        } else {
            onNavigateToLogin()
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Text(
                text       = "SmartSales",
                fontSize   = 42.sp,
                fontWeight = FontWeight.Bold,
                color      = BrandOrange
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text       = "The Business Brain\nEvery Small Shop Needs",
                fontSize   = 14.sp,
                color      = OnSurfaceMuted,
                textAlign  = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}