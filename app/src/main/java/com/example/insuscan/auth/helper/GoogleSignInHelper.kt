package com.example.insuscan.auth.helper

import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.insuscan.auth.AuthManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

/**
 * Handles Google Sign-In intent launching and result processing.
 * Separates heavy authentication lifecycle logic from the LoginFragment.
 */
class GoogleSignInHelper(
    private val fragment: Fragment,
    private val webClientId: String,
    private val onLoading: (Boolean) -> Unit,
    private val onSuccess: (email: String, name: String, photo: String?) -> Unit,
    private val onError: (String) -> Unit
) {

    private val launcher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                onLoading(true)
                AuthManager.signInWithGoogle(token) { success, error ->
                    onLoading(false)
                    if (success) {
                        val email = AuthManager.getUserEmail()
                        if (email.isNullOrBlank()) {
                            onError("Google sign-in succeeded but email is missing")
                            return@signInWithGoogle
                        }
                        val displayName = AuthManager.currentUser()?.displayName ?: email.substringBefore("@")
                        val photoUrl = AuthManager.currentUser()?.photoUrl?.toString()
                        onSuccess(email, displayName, photoUrl)
                    } else {
                        onError("Google sign-in failed: $error")
                    }
                }
            }
        } catch (e: ApiException) {
            android.util.Log.e("GoogleSignIn", "Failed code=${e.statusCode}", e)
            onError("Google sign-in failed. Error code: ${e.statusCode}")
        }
    }

    fun setup() {
        AuthManager.setupGoogleSignIn(fragment.requireContext(), webClientId)
    }

    fun launch() {
        AuthManager.getGoogleSignInIntent()?.let { intent ->
            launcher.launch(intent)
        }
    }
}
