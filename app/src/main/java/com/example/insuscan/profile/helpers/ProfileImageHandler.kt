package com.example.insuscan.profile.helpers

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.profile.exception.StorageException
import com.example.insuscan.utils.ToastHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileImageHandler(private val fragment: Fragment) {
    private var photoUri: Uri? = null
    private var uiManager: ProfileUiManager? = null
    private var onPhotoUpdated: (() -> Unit)? = null

    private val pickImageLauncher = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSavePhoto(it) }
    }

    private val takePictureLauncher = fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoUri?.let { uploadAndSavePhoto(it) }
    }

    private val cameraPermissionLauncher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else ToastHelper.showShort(fragment.requireContext(), "Camera permission denied")
    }

    private var onUploadError: ((StorageException) -> Unit)? = null

    fun bind(ui: ProfileUiManager, updateCallback: () -> Unit, onError: ((StorageException) -> Unit)? = null) {
        this.uiManager = ui
        this.onPhotoUpdated = updateCallback
        this.onUploadError = onError
    }

    fun showPhotoOptionsDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> pickImageLauncher.launch("image/*")
                    2 -> removeProfilePhoto()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        val ctx = fragment.requireContext()
        photoUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", photoFile)
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val storageDir = fragment.requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PROFILE_${timestamp}_", ".jpg", storageDir)
    }

    private fun uploadAndSavePhoto(uri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("profile_photos/$userId.jpg")
        val ctx = fragment.requireContext()
        ToastHelper.showShort(ctx, "Uploading photo...")

        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                val url = downloadUrl.toString()
                UserProfileManager.saveProfilePhotoUrl(ctx, url)
                loadProfilePhoto(url)
                onPhotoUpdated?.invoke()
                ToastHelper.showShort(ctx, "Photo updated and saved!")
            }.addOnFailureListener { e ->
                Log.e("ProfileImageHandler", "Failed to get download URL: ${e.message}")
                val error = StorageException.DownloadUrlFailed
                onUploadError?.invoke(error) ?: ToastHelper.showShort(ctx, error.message ?: "Upload failed")
            }
        }.addOnFailureListener { e ->
            Log.e("ProfileImageHandler", "Upload failed: ${e.message}")
            val error = StorageException.UploadFailed(e)
            onUploadError?.invoke(error) ?: ToastHelper.showShort(ctx, error.message ?: "Upload failed")
        }
    }

    fun loadProfilePhoto(url: String?) {
        val ui = uiManager ?: return
        if (url.isNullOrBlank()) {
            ui.profilePhoto.setImageResource(R.drawable.ic_person)
            ui.profilePhoto.setPadding(32, 32, 32, 32)
            return
        }
        Glide.with(fragment).load(url).circleCrop().placeholder(R.drawable.ic_person).into(ui.profilePhoto)
        ui.profilePhoto.setPadding(0, 0, 0, 0)
    }

    private fun removeProfilePhoto() {
        val ctx = fragment.requireContext()
        UserProfileManager.saveProfilePhotoUrl(ctx, "")
        uiManager?.profilePhoto?.setImageResource(R.drawable.ic_person)
        uiManager?.profilePhoto?.setPadding(32, 32, 32, 32)
        ToastHelper.showShort(ctx, "Photo removed")
    }
}
