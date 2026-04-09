package com.example.insuscan.auth

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.auth.exception.AuthException
import com.example.insuscan.auth.helper.GoogleSignInHelper
import com.example.insuscan.auth.helper.LoginFlowManager
import com.example.insuscan.auth.util.AuthErrorHandler
import com.example.insuscan.auth.validation.AuthValidator
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Handles the visual presentation layer of Login and Sign Up.
 * Delegates string validation, error formatting, registration syncing,
 * and Google Sign-in to their respective helper classes.
 */
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

    private var isLoginMode = true

    private lateinit var flowManager: LoginFlowManager
    private lateinit var googleSignInHelper: GoogleSignInHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        googleSignInHelper = GoogleSignInHelper(
            fragment = this,
            webClientId = getString(R.string.default_web_client_id),
            onLoading = { showLoading(it) },
            onSuccess = { email, name, photo -> flowManager.handleLoginSuccess(email, name, photo) },
            onError = { showError(it.message ?: "Google sign-in failed") }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findViews(view)

        flowManager = LoginFlowManager(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            userRepository = UserRepositoryImpl(),
            onNavigateToHome = { findNavController().navigate(R.id.action_loginFragment_to_splashAnimation) },
            onNavigateToRegistration = { findNavController().navigate(R.id.action_loginFragment_to_registrationStep1) },
            onLoading = { showLoading(it) }
        )

        googleSignInHelper.setup()
        setupListeners()

        // Check if already logged in (Auto-Login)
        if (flowManager.checkAutoLogin()) return

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

    private fun setupListeners() {
        btnAction.setOnClickListener {
            clearErrors()
            if (isLoginMode) performLogin() else performSignUp()
        }

        tvToggleAction.setOnClickListener {
            isLoginMode = !isLoginMode
            clearErrors()
            clearFields()
            updateUI()
        }

        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val error = AuthValidator.validateEmail(email)
            if (error != null) {
                tilEmail.error = error
                return@setOnClickListener
            }
            sendPasswordReset(email)
        }

        btnGoogle.setOnClickListener {
            googleSignInHelper.launch()
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

        val emailError = AuthValidator.validateEmail(email)
        if (emailError != null) { tilEmail.error = emailError; return }

        val passError = AuthValidator.validatePasswordNotEmpty(password)
        if (passError != null) { tilPassword.error = passError; return }

        showLoading(true)
        AuthManager.signInWithEmail(email, password) { success, exception ->
            showLoading(false)
            if (success) {
                val displayName = AuthManager.currentUser()?.displayName ?: email.substringBefore("@")
                flowManager.handleLoginSuccess(email, displayName, null)
            } else {
                showError(exception?.message ?: "Sign in failed")
            }
        }
    }

    private fun performSignUp() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        val emailError = AuthValidator.validateEmail(email)
        if (emailError != null) { tilEmail.error = emailError; return }

        val passError = AuthValidator.validatePassword(password)
        if (passError != null) { tilPassword.error = passError; return }

        val matchError = AuthValidator.validatePasswordsMatch(password, confirmPassword)
        if (matchError != null) { tilConfirmPassword.error = matchError; return }

        showLoading(true)
        AuthManager.registerWithEmail(email, password) { success, exception ->
            if (success) {
                val displayName = email.substringBefore("@")
                flowManager.handleLoginSuccess(email, displayName, null)
            } else {
                showLoading(false)
                showError(exception?.message ?: "Registration failed")
            }
        }
    }

    private fun sendPasswordReset(email: String) {
        showLoading(true)
        com.google.firebase.auth.FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                } else {
                    showError(AuthErrorHandler.toAuthException(task.exception?.message).message ?: "Failed to send reset email")
                }
            }
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