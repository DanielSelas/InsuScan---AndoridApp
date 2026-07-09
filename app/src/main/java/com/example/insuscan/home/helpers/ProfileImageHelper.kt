package com.example.insuscan.home.helpers

import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import com.google.firebase.auth.FirebaseAuth

/**
 * Loads the home-screen avatar: local photo first, then the Google account photo,
 * falling back to a placeholder. Renders the image circular via Glide.
 */
class ProfileImageHelper(
    private val fragment: Fragment,
    private val profileImage: ImageView
) {

    fun loadImage() {
        val ctx = fragment.context ?: return
        val localPhotoUrl = UserProfileManager.getProfilePhotoUrl(ctx)
        val googlePhotoUrl = FirebaseAuth.getInstance().currentUser?.photoUrl

        when {
            !localPhotoUrl.isNullOrEmpty() -> loadWithGlide(localPhotoUrl)
            googlePhotoUrl != null -> loadWithGlide(googlePhotoUrl)
            else -> loadWithGlide(PLACEHOLDER_RES)
        }
    }

    private fun loadWithGlide(source: Any) {
        Glide.with(fragment)
            .load(source)
            .circleCrop()
            .placeholder(PLACEHOLDER_RES)
            .error(PLACEHOLDER_RES)
            .into(profileImage)
    }

    companion object {
        private val PLACEHOLDER_RES = R.drawable.duck
    }
}
