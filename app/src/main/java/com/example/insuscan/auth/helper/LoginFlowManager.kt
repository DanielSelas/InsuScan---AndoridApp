package com.example.insuscan.auth.helper

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.network.repository.UserRepository
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.launch

/**
 * Manages the navigation and profile synchronization after a successful Firebase Auth login.
 * Separates backend syncing and SharedPreferences logic from the UI.
 */
class LoginFlowManager(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val userRepository: UserRepository,
    private val onNavigateToHome: () -> Unit,
    private val onNavigateToRegistration: () -> Unit,
    private val onLoading: (Boolean) -> Unit
) {

    fun checkAutoLogin(): Boolean {
        val existingEmail = UserProfileManager.getUserEmail(context)
        if (!existingEmail.isNullOrBlank() && AuthManager.isLoggedIn()) {
            if (UserProfileManager.isRegistrationComplete(context)) {
                val storedName = UserProfileManager.getUserName(context) ?: "User"
                val storedPhoto = UserProfileManager.getProfilePhotoUrl(context)
                handleLoginSuccess(existingEmail, storedName, storedPhoto)
                return true
            } else {
                onNavigateToRegistration()
                return true
            }
        }
        return false
    }

    fun handleLoginSuccess(email: String, displayName: String, photoUrl: String?) {
        Log.e("LoginFlow", "Login Success! PhotoURL: $photoUrl")
        UserProfileManager.saveUserEmail(context, email)
        UserProfileManager.saveUserName(context, displayName)
        if (photoUrl != null) {
            UserProfileManager.saveProfilePhotoUrl(context, photoUrl)
        } else {
            Log.e("LoginFlow", "PhotoURL is NULL from Auth!")
        }

        checkRegistrationAndNavigate(email)
    }

    private fun checkRegistrationAndNavigate(email: String) {
        if (UserProfileManager.isRegistrationComplete(context)) {
            onNavigateToHome()
            return
        }

        onLoading(true)
        scope.launch {
            try {
                val result = userRepository.getUser(email)
                onLoading(false)

                if (result.isSuccess && result.getOrNull() != null) {
                    val userDto = result.getOrNull()!!
                    UserProfileManager.syncFromServer(context, userDto)
                    UserProfileManager.setRegistrationComplete(context, true)
                    onNavigateToHome()
                } else {
                    onNavigateToRegistration()
                }
            } catch (e: Exception) {
                onLoading(false)
                Log.e("LoginFlow", "Error checking registration", e)
                onNavigateToRegistration()
            }
        }
    }
}
