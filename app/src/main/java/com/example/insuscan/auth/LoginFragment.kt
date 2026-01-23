package com.example.insuscan.auth


import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.network.repository.UserRepository
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    // UI elements
    private lateinit var tvScreenTitle: TextView
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvPasswordHint: TextView
    private lateinit var btnAction: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvTogglePrompt: TextView
    private lateinit var tvToggleAction: TextView
    private lateinit var btnGoogle: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: View

    private val userRepository = UserRepositoryImpl()

    // true = Sign In mode, false = Sign Up mode
    private var isLoginMode = true

    // Google Sign-In launcher
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
                        val displayName = AuthManager.currentUser()?.displayName
                            ?: email.substringBefore("@")
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

        findViews(view)
        setupGoogleSignIn()
        setupListeners()

        // Check if already logged in (Auto-Login)
        val existingEmail = UserProfileManager.getUserEmail(requireContext())
        if (!existingEmail.isNullOrBlank() && AuthManager.isLoggedIn()) {
            // Use the stored name or a default
            val storedName = UserProfileManager.getUserName(requireContext()) ?: "User"
            // Call onLoginSuccess to perform Sync + Reset before navigating
            onLoginSuccess(existingEmail, storedName)
            return
        }

        updateUI()
    }

    private fun findViews(view: View) {
        tvScreenTitle = view.findViewById(R.id.tv_screen_title)
        tilEmail = view.findViewById(R.id.til_email)
        etEmail = view.findViewById(R.id.et_email)
        tilPassword = view.findViewById(R.id.til_password)
        etPassword = view.findViewById(R.id.et_password)
        tilConfirmPassword = view.findViewById(R.id.til_confirm_password)
        etConfirmPassword = view.findViewById(R.id.et_confirm_password)
        tvPasswordHint = view.findViewById(R.id.tv_password_hint)
        btnAction = view.findViewById(R.id.btn_action)
        tvForgotPassword = view.findViewById(R.id.tv_forgot_password)
        tvTogglePrompt = view.findViewById(R.id.tv_toggle_prompt)
        tvToggleAction = view.findViewById(R.id.tv_toggle_action)
        btnGoogle = view.findViewById(R.id.btn_google)
        progressBar = view.findViewById(R.id.progress_bar)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
    }

    private fun setupGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        AuthManager.setupGoogleSignIn(requireContext(), webClientId)
    }

    private fun setupListeners() {
        // Main action button
        btnAction.setOnClickListener {
            clearErrors()
            if (isLoginMode) {
                performLogin()
            } else {
                performSignUp()
            }
        }

        // Toggle between login and sign up
        tvToggleAction.setOnClickListener {
            isLoginMode = !isLoginMode
            clearErrors()
            clearFields()
            updateUI()
        }

        // Forgot password
        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                tilEmail.error = "Enter your email first"
                return@setOnClickListener
            }
            if (!isValidEmail(email)) {
                tilEmail.error = "Enter a valid email"
                return@setOnClickListener
            }
            sendPasswordReset(email)
        }

        // Google sign in
        btnGoogle.setOnClickListener {
            AuthManager.getGoogleSignInIntent()?.let { intent ->
                googleSignInLauncher.launch(intent)
            }
        }
    }

    private fun updateUI() {
        if (isLoginMode) {
            tvScreenTitle.text = "Sign In"
            btnAction.text = "Sign In"
            tvTogglePrompt.text = "Don't have an account? "
            tvToggleAction.text = "Sign Up"
            tvForgotPassword.isVisible = true
            tilConfirmPassword.isVisible = false
            tvPasswordHint.isVisible = false
        } else {
            tvScreenTitle.text = "Create Account"
            btnAction.text = "Create Account"
            tvTogglePrompt.text = "Already have an account? "
            tvToggleAction.text = "Sign In"
            tvForgotPassword.isVisible = false
            tilConfirmPassword.isVisible = true
            tvPasswordHint.isVisible = true
        }
    }

    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // Validate
        if (!validateEmail(email)) return
        if (!validatePasswordNotEmpty(password)) return

        showLoading(true)
        AuthManager.signInWithEmail(email, password) { success, error ->
            showLoading(false)
            if (success) {
                val displayName = AuthManager.currentUser()?.displayName
                    ?: email.substringBefore("@")
                onLoginSuccess(email, displayName)
            } else {
                showError(mapFirebaseError(error))
            }
        }
    }

    private fun performSignUp() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        // Validate all fields
        if (!validateEmail(email)) return
        if (!validatePassword(password)) return
        if (!validatePasswordsMatch(password, confirmPassword)) return

        showLoading(true)
        AuthManager.registerWithEmail(email, password) { success, error ->
            if (success) {
                val displayName = email.substringBefore("@")
                onLoginSuccess(email, displayName)
            } else {
                showLoading(false)
                showError(mapFirebaseError(error))
            }
        }
    }

    // --- Validation functions ---

    private fun validateEmail(email: String): Boolean {
        return when {
            email.isEmpty() -> {
                tilEmail.error = "Email is required"
                false
            }
            !isValidEmail(email) -> {
                tilEmail.error = "Enter a valid email address"
                false
            }
            else -> true
        }
    }

    private fun validatePasswordNotEmpty(password: String): Boolean {
        return when {
            password.isEmpty() -> {
                tilPassword.error = "Password is required"
                false
            }
            else -> true
        }
    }

    private fun validatePassword(password: String): Boolean {
        return when {
            password.isEmpty() -> {
                tilPassword.error = "Password is required"
                false
            }
            password.length < 8 -> {
                tilPassword.error = "At least 8 characters"
                false
            }
            !password.any { it.isUpperCase() } -> {
                tilPassword.error = "Must contain an uppercase letter"
                false
            }
            !password.any { it.isLowerCase() } -> {
                tilPassword.error = "Must contain a lowercase letter"
                false
            }
            !password.any { it.isDigit() } -> {
                tilPassword.error = "Must contain a number"
                false
            }
            !password.any { it in "!@#\$%^&*()_+-=[]{}|;':\",./<>?" } -> {
                tilPassword.error = "Must contain a special character"
                false
            }
            else -> true
        }
    }

    private fun validatePasswordsMatch(password: String, confirmPassword: String): Boolean {
        return when {
            confirmPassword.isEmpty() -> {
                tilConfirmPassword.error = "Please confirm your password"
                false
            }
            password != confirmPassword -> {
                tilConfirmPassword.error = "Passwords don't match"
                false
            }
            else -> true
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun clearErrors() {
        tilEmail.error = null
        tilPassword.error = null
        tilConfirmPassword.error = null
    }

    private fun clearFields() {
        etEmail.text?.clear()
        etPassword.text?.clear()
        etConfirmPassword.text?.clear()
    }

    // --- Firebase error mapping ---

    private fun mapFirebaseError(error: String?): String {
        return when {
            error == null -> "An unknown error occurred"
            error.contains("no user record", ignoreCase = true) ->
                "No account found with this email"
            error.contains("password is invalid", ignoreCase = true) ->
                "Incorrect password"
            error.contains("email address is already in use", ignoreCase = true) ->
                "An account with this email already exists"
            error.contains("badly formatted", ignoreCase = true) ->
                "Invalid email format"
            error.contains("network error", ignoreCase = true) ->
                "Network error. Check your connection"
            error.contains("too many requests", ignoreCase = true) ->
                "Too many attempts. Try again later"
            else -> error
        }
    }

    // --- Password reset ---

    private fun sendPasswordReset(email: String) {
        showLoading(true)
        com.google.firebase.auth.FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Password reset email sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showError(mapFirebaseError(task.exception?.message))
                }
            }
    }

    // --- Success handling ---

    private fun onLoginSuccess(email: String, displayName: String) {
        // Save basic info locally
        UserProfileManager.saveUserEmail(requireContext(), email)
        UserProfileManager.saveUserName(requireContext(), displayName)

        showLoading(true)

        lifecycleScope.launch {
            try {
                // Try to fetch full profile from server
                val result = userRepository.getUser(email)

                if (result.isSuccess) {
                    val userDto = result.getOrNull()
                    if (userDto != null) {
                        UserProfileManager.syncFromServer(requireContext(), userDto)
                        Toast.makeText(requireContext(), "Profile synced successfully", Toast.LENGTH_SHORT).show()
                    }
                    showLoading(false)
                    navigateToHome()
                } else {
                    // User not found on server, proceed to create
                    createUserInServerDB(email, displayName)
                }
            } catch (e: Exception) {
                // Network error or offline - proceed to home with local data
                showLoading(false)
                navigateToHome()
            }
        }
    }

    private fun createUserInServerDB(email: String, name: String) {
        lifecycleScope.launch {
            try {
                // Check if user exists, if not create
                val result = userRepository.getUser(email)
                if (result.isFailure) {
                    userRepository.register(email, name)
                }
            } catch (e: Exception) {
                // Ignore - user can still use app
            } finally {
                showLoading(false)
                navigateToHome()
            }
        }
    }

    private fun navigateToHome() {
        findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
    }

    // --- UI helpers ---

    private fun showLoading(show: Boolean) {
        progressBar.isVisible = show
        loadingOverlay.isVisible = show
        btnAction.isEnabled = !show
        btnGoogle.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}