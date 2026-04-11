package com.torpedoes.smartsales.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading       : Boolean = false,
    val isSuccess       : Boolean = false,
    val errorMessage    : String? = null,
    val resetEmailSent  : Boolean = false,
    val emailAlreadyExists: Boolean = false   // ← new
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db  : FirebaseFirestore       // ← new
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // One-shot event — fires once after sign-up succeeds
    private val _signUpSuccess = MutableSharedFlow<Unit>(replay = 0)
    val signUpSuccess: SharedFlow<Unit> = _signUpSuccess.asSharedFlow()

    // ── Sign Up ────────────────────────────────────────────────────────────

    fun signUp(fullName: String, email: String, password: String) {
        when {
            fullName.isBlank() -> { _uiState.value = _uiState.value.copy(errorMessage = "Please enter your full name.");          return }
            email.isBlank()    -> { _uiState.value = _uiState.value.copy(errorMessage = "Please enter your email.");              return }
            password.isBlank() -> { _uiState.value = _uiState.value.copy(errorMessage = "Please enter a password.");             return }
            password.length < 6 -> { _uiState.value = _uiState.value.copy(errorMessage = "Password must be at least 6 characters."); return }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, emailAlreadyExists = false)
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val uid    = result.user!!.uid

                result.user!!.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName.trim())
                        .build()
                ).await()

                db.collection("users").document(uid).set(
                    mapOf(
                        "uid"       to uid,
                        "name"      to fullName.trim(),
                        "nameLower" to fullName.trim().lowercase(),
                        "email"     to email.trim()
                    )
                ).await()

                _uiState.value = _uiState.value.copy(isLoading = false)
                _signUpSuccess.emit(Unit)

            } catch (e: FirebaseAuthWeakPasswordException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Password must be at least 6 characters.")
            } catch (e: FirebaseAuthUserCollisionException) {
                _uiState.value = _uiState.value.copy(isLoading = false, emailAlreadyExists = true)
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Please enter a valid email address.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Sign up failed. Please try again.")
            }
        }
    }

    // ── Log In ─────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email and password cannot be empty")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Login failed. Please try again."
                )
            }
        }
    }

    // ── Password Reset ─────────────────────────────────────────────────────

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your email address")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                _uiState.value = _uiState.value.copy(isLoading = false, resetEmailSent = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to send reset email."
                )
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    fun clearError()       { _uiState.value = _uiState.value.copy(errorMessage = null, emailAlreadyExists = false) }
    fun clearSignUpError() { _uiState.value = _uiState.value.copy(errorMessage = null, emailAlreadyExists = false) }
}