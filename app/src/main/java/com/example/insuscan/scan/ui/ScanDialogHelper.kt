package com.example.insuscan.scan.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.insuscan.network.exception.ScanException
import com.example.insuscan.utils.ToastHelper

class ScanDialogHelper(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onTryAgainClicked()
        fun onCancelClicked()
        fun onTakeSidePhotoClicked()
        fun onSkipSidePhotoClicked()
        fun onRetakeSidePhotoOnlyClicked()
        fun onRetakeBothPhotosClicked()
    }

    fun showNoFoodDetectedDialog() {
        AlertDialog.Builder(context)
            .setTitle("No Food Detected")
            .setMessage("We couldn't identify any food items.\n\nTry a clearer photo with good lighting.")
            .setPositiveButton("Try Again") { d, _ -> 
                d.dismiss()
                listener.onTryAgainClicked() 
            }
            .setNegativeButton("Cancel") { d, _ -> 
                d.dismiss()
                listener.onCancelClicked() 
            }
            .setCancelable(false)
            .show()
    }

    fun showScanFailedDialog(message: String) {
        AlertDialog.Builder(context)
            .setTitle("Scan Failed")
            .setMessage(message)
            .setPositiveButton("Try Again") { d, _ -> 
                d.dismiss()
                listener.onTryAgainClicked() 
            }
            .setNegativeButton("Cancel") { d, _ -> 
                d.dismiss()
                listener.onCancelClicked() 
            }
            .setCancelable(false)
            .show()
    }

    fun handleScanError(error: Throwable) {
        when (error) {
            is ScanException.NoFoodDetected -> showNoFoodDetectedDialog()
            is ScanException.NetworkError -> showScanFailedDialog("No internet connection. Please check your network and try again.")
            is ScanException.ServerError -> showScanFailedDialog("Server error. Please try again later.")
            is ScanException.Unauthorized -> ToastHelper.showLong(context, "Session expired. Please log in again.")
            else -> showScanFailedDialog("Something went wrong. Please try again.")
        }
    }

    fun showSidePhotoDialog() {
        AlertDialog.Builder(context)
            .setTitle("Side Photo Needed")
            .setMessage("For better accuracy, take a photo from the side to measure depth.\n\nHold the phone at table level, showing the side of the plate/bowl.")
            .setPositiveButton("Take Side Photo") { d, _ ->
                d.dismiss()
                listener.onTakeSidePhotoClicked()
            }
            .setNegativeButton("Skip") { d, _ ->
                d.dismiss()
                listener.onSkipSidePhotoClicked()
            }
            .setCancelable(false)
            .show()
    }

    fun showRetakeOptionsDialog() {
        AlertDialog.Builder(context)
            .setTitle("Retake Photo")
            .setMessage("Which photo would you like to retake?")
            .setPositiveButton("Side photo only") { d, _ ->
                d.dismiss()
                listener.onRetakeSidePhotoOnlyClicked()
            }
            .setNegativeButton("Both photos") { d, _ ->
                d.dismiss()
                listener.onRetakeBothPhotosClicked()
            }
            .setCancelable(true)
            .show()
    }
}
