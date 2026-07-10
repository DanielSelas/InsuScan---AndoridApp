package com.example.insuscan.auth

import android.content.Context
import android.content.Intent
import com.example.insuscan.auth.exception.AuthException
import com.example.insuscan.auth.util.AuthErrorHandler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Central wrapper for all Firebase Auth and Google Sign-In operations.
 */
object AuthManager {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var googleSignInClient: GoogleSignInClient? = null

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun getUserEmail(): String? = auth.currentUser?.email

    // Setup Google Sign-In (call once from Activity)
    fun setupGoogleSignIn(context: Context, webClientId: String) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    fun getGoogleSignInIntent(): Intent? = googleSignInClient?.signInIntent

    fun signInWithGoogle(idToken: String, onComplete: (Boolean, AuthException?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, AuthErrorHandler.toAuthException(task.exception?.message))
                }
            }
    }

    fun signInWithEmail(email: String, password: String, onComplete: (Boolean, AuthException?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, AuthErrorHandler.toAuthException(task.exception?.message))
                }
            }
    }

    fun registerWithEmail(email: String, password: String, onComplete: (Boolean, AuthException?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, AuthErrorHandler.toAuthException(task.exception?.message))
                }
            }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient?.signOut()
    }
}