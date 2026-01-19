package com.example.insuscan.auth


import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import com.example.insuscan.network.repository.UserRepository
import kotlinx.coroutines.launch
class LoginFragment : Fragment(R.layout.fragment_login) {

    private lateinit var etEmail: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnGoogle: Button
    private lateinit var progressBar: ProgressBar
    private val userRepository = UserRepository()

    // Google Sign-In result handler
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                showLoading(true)
                AuthManager.signInWithGoogle(token) { success, error ->
                    showLoading(false)
                    if (success) {
                        val email = AuthManager.getUserEmail()
                        if (email.isNullOrBlank()) {
                            showError("Google sign-in succeeded but email is missing")
                            return@signInWithGoogle
                        }
                        val displayName = AuthManager.currentUser()?.displayName ?: email.substringBefore("@")
                        onLoginSuccess(email, displayName)
                    } else {
                        showError("Google sign-in failed: $error")
                    }
                }
            }
        } catch (e: ApiException) {
            showError("Google sign-in failed: ${e.message}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Skip login if already have a local session (server uses email-only users)
        val existingEmail = UserProfileManager.getUserEmail(requireContext())
        if (!existingEmail.isNullOrBlank()) {
            navigateToHome()
            return
        }

        findViews(view)
        setupGoogleSignIn()
        setupListeners()
    }

    private fun findViews(view: View) {
        etEmail = view.findViewById(R.id.et_email)
        btnLogin = view.findViewById(R.id.btn_login)
        btnRegister = view.findViewById(R.id.btn_register)
        btnGoogle = view.findViewById(R.id.btn_google)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    private fun setupGoogleSignIn() {
        // Web client ID from google-services.json (Firebase Console)
        val webClientId = getString(R.string.default_web_client_id)
        AuthManager.setupGoogleSignIn(requireContext(), webClientId)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (validateEmail(email)) {
                performServerLogin(email)
            }
        }

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (validateEmail(email)) {
                performServerRegister(email)
            }
        }

        btnGoogle.setOnClickListener {
            AuthManager.getGoogleSignInIntent()?.let { intent ->
                googleSignInLauncher.launch(intent)
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) {
            showError("Please enter your email")
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email")
            return false
        }
        return true
    }

    private fun performServerLogin(email: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = userRepository.login(email)
                result.onSuccess { user ->
                    val displayName = user.username ?: email.substringBefore("@")
                    onLoginSuccess(email, displayName)
                }.onFailure {
                    // If user doesn't exist yet, offer registration via the button
                    showError("User not found on server. Tap 'Create user' to register.")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun performServerRegister(email: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val name = email.substringBefore("@")
                val result = userRepository.register(email, name)
                result.onSuccess { user ->
                    val displayName = user.username ?: name
                    onLoginSuccess(email, displayName)
                }.onFailure { e ->
                    showError("Registration failed: ${e.message}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun onLoginSuccess(email: String, displayName: String) {
        // Save email locally
        UserProfileManager.saveUserEmail(requireContext(), email)
        UserProfileManager.saveUserName(requireContext(), displayName)

        // Ensure user exists in server DB (if doesn't exist)
        createUserInDatabase(email, displayName)
    }

    private fun createUserInDatabase(email: String, name: String) {
        lifecycleScope.launch {
            try {
                // Try to get user first
                val result = userRepository.getUser(email)

                if (result.isFailure) {
                    // User doesn't exist, create new one
                    userRepository.register(email, name)
                }
            } catch (e: Exception) {
                // Ignore errors - user can still use app
            } finally {
                // Navigate to home regardless of server result
                navigateToHome()
            }
        }
    }

    private fun navigateToHome() {
        findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !show
        btnRegister.isEnabled = !show
        btnGoogle.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}