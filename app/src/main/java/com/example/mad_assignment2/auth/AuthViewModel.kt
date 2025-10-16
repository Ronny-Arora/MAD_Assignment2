package com.example.mad_assignment2.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val loading: Boolean = false,
    val userEmail: String? = null,
    val error: String? = null,
    val isAnon: Boolean = false
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = Firebase.auth

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui

    init {
        // Ensure we always have a user (anon on first run)
        viewModelScope.launch {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            emitState()
        }
    }

    private fun emitState(err: String? = null, loading: Boolean = false) {
        val u = auth.currentUser
        _ui.value = AuthUiState(
            loading = loading,
            userEmail = u?.email,
            error = err,
            isAnon = (u != null && u.isAnonymous)
        )
    }

    /** Existing account login (returns a permanent user; UID changes if not linked before). */
    fun signIn(email: String, password: String) = viewModelScope.launch {
        try {
            _ui.value = _ui.value.copy(loading = true, error = null)
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            emitState()
        } catch (t: Throwable) {
            emitState(err = t.localizedMessage ?: "Sign in failed")
        }
    }

    /** Upgrade current anonymous user to a real account (keeps the same UID). */
    fun linkAnonToEmail(email: String, password: String) = viewModelScope.launch {
        try {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val cred = EmailAuthProvider.getCredential(email.trim(), password)
            val user = auth.currentUser
            if (user == null) {
                emitState(err = "No user session")
                return@launch
            }
            // If already permanent, nothing to do
            if (!user.isAnonymous) {
                emitState()
                return@launch
            }
            user.linkWithCredential(cred).await() // keeps same UID
            emitState()
        } catch (t: Throwable) {
            emitState(err = t.localizedMessage ?: "Account upgrade failed")
        }
    }

    fun signOut() = viewModelScope.launch {
        Firebase.auth.signOut()
        // Create new anon session after sign out
        Firebase.auth.signInAnonymously().await()
        emitState()
    }
}
