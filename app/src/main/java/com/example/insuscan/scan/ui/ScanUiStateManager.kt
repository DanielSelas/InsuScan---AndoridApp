package com.example.insuscan.scan.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.scan.coach.CameraCoachState
import com.example.insuscan.scan.coach.CoachSeverity
import com.example.insuscan.scan.coach.MeasurementStrategy
import java.io.File
import com.bumptech.glide.Glide

class ScanUiStateManager(
    private val view: View,
    private val listener: Listener
) {
    interface Listener {
        fun onSidePhotoReadyClicked()
        fun onSidePhotoSkipClicked()
        fun onArIndicatorClicked()
    }

    val cameraPreview: PreviewView = view.findViewById(R.id.camera_preview)
    val capturedImageView: ImageView = view.findViewById(R.id.iv_captured_image)
    val captureButton: Button = view.findViewById(R.id.btn_capture)
    val galleryButton: Button = view.findViewById(R.id.btn_gallery)
    val btnRefToggle: TextView = view.findViewById(R.id.btn_ref_toggle)
    val viewTargetZone: View = view.findViewById(R.id.view_target_zone)
    val hiddenArSurfaceView: android.opengl.GLSurfaceView = view.findViewById(R.id.hidden_ar_surface_view)
    val chipGroupRefObject: LinearLayout = view.findViewById(R.id.chip_group_ref_object)
    
    // Internal views for state management
    private val qualityStatusText: TextView = view.findViewById(R.id.tv_quality_status)
    private val loadingOverlay: FrameLayout = view.findViewById(R.id.loading_overlay)
    private val loadingMessage: TextView = view.findViewById(R.id.tv_loading_message)
    private val subtitleText: TextView = view.findViewById(R.id.tv_scan_subtitle)
    private val tvCoachPill: TextView = view.findViewById(R.id.tv_coach_pill)
    private val arIndicatorDot: View = view.findViewById(R.id.view_ar_indicator)
    private val layoutArIndicator: View = view.findViewById(R.id.layout_ar_indicator)
    private val tvArExplanation: TextView = view.findViewById(R.id.tv_ar_explanation)
    private val viewPlateTargetZone: View = view.findViewById(R.id.view_plate_target_zone)
    private val layoutStepIndicator: LinearLayout = view.findViewById(R.id.layout_step_indicator)
    private val stepDot1: View = view.findViewById(R.id.view_step_dot_1)
    private val stepDot2: View = view.findViewById(R.id.view_step_dot_2)
    private val cardSidePhotoPrompt: androidx.cardview.widget.CardView = view.findViewById(R.id.card_side_photo_prompt)
    private val btnSidePhotoReady: Button = view.findViewById(R.id.btn_side_photo_ready)
    private val tvSideCardSkip: TextView = view.findViewById(R.id.tv_side_card_skip)
    
    init {
        layoutArIndicator.setOnClickListener {
            tvArExplanation.visibility = if (tvArExplanation.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            listener.onArIndicatorClicked()
        }
        btnSidePhotoReady.setOnClickListener { listener.onSidePhotoReadyClicked() }
        tvSideCardSkip.setOnClickListener { listener.onSidePhotoSkipClicked() }
    }

    fun showLoading(show: Boolean, message: String = "Processing image...") {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        loadingMessage.text = message
        captureButton.isEnabled = !show
        galleryButton.isEnabled = !show
    }

    fun switchToCapturedImageMode(imageFile: File, fragment: androidx.fragment.app.Fragment, isSidePhotoMode: Boolean) {
        Glide.with(fragment).load(imageFile).into(capturedImageView)
        cameraPreview.visibility = View.GONE
        capturedImageView.visibility = View.VISIBLE
        captureButton.text = if (isSidePhotoMode) "Retake Side" else "Retake"
        captureButton.setBackgroundResource(R.drawable.button_primary)
        captureButton.isEnabled = true
        captureButton.alpha = 1f
        captureButton.clearAnimation()
        subtitleText.text = "Analyzing your meal..."
        qualityStatusText.visibility = View.GONE
        tvCoachPill.visibility = View.GONE

        chipGroupRefObject.visibility = View.GONE
        btnRefToggle.visibility = View.GONE
        layoutArIndicator.visibility = View.GONE
        viewPlateTargetZone.visibility = View.GONE
        cardSidePhotoPrompt.visibility = View.GONE
    }

    fun switchToCameraMode() {
        capturedImageView.visibility = View.GONE
        cameraPreview.visibility = View.VISIBLE
        captureButton.text = "Capture"
        captureButton.setBackgroundResource(R.drawable.button_primary)
        qualityStatusText.visibility = View.GONE
        subtitleText.text = "Place your plate, then tap Capture"

        btnRefToggle.visibility = View.VISIBLE
        cardSidePhotoPrompt.visibility = View.GONE
        layoutStepIndicator.visibility = View.GONE
        layoutArIndicator.visibility = View.VISIBLE
        viewPlateTargetZone.visibility = View.VISIBLE
        viewTargetZone.visibility = View.VISIBLE
    }

    fun switchToSideCameraMode() {
        switchToCameraMode()
        updateStepIndicator(2, true)
        viewPlateTargetZone.visibility = View.GONE
        viewTargetZone.visibility = View.GONE
        subtitleText.text = "Hold phone at table level — capture the side of the plate"
    }

    fun showSidePhotoCard() {
        updateStepIndicator(1, true)
        cardSidePhotoPrompt.visibility = View.VISIBLE
        cardSidePhotoPrompt.translationY = cardSidePhotoPrompt.height.toFloat().coerceAtLeast(300f)
        cardSidePhotoPrompt.animate()
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    fun hideSidePhotoCard(onComplete: () -> Unit) {
        cardSidePhotoPrompt.animate()
            .translationY(cardSidePhotoPrompt.height.toFloat().coerceAtLeast(300f))
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                cardSidePhotoPrompt.visibility = View.GONE
                onComplete()
            }
            .start()
    }

    fun updateStepIndicator(activeStep: Int, isTwoPhotoMode: Boolean) {
        if (!isTwoPhotoMode) {
            layoutStepIndicator.visibility = View.GONE
            return
        }
        layoutStepIndicator.visibility = View.VISIBLE
        val activeColor = ContextCompat.getColor(view.context, R.color.primary)
        val inactiveColor = ContextCompat.getColor(view.context, R.color.text_disabled)
        stepDot1.backgroundTintList = ColorStateList.valueOf(if (activeStep == 1) activeColor else inactiveColor)
        stepDot2.backgroundTintList = ColorStateList.valueOf(if (activeStep == 2) activeColor else inactiveColor)
    }

    fun updateTwoPhotoHint(requiresSidePhoto: Boolean, isShowingCapturedImage: Boolean, isSidePhotoMode: Boolean) {
        if (requiresSidePhoto && !isShowingCapturedImage && !isSidePhotoMode) {
            subtitleText.text = "\uD83D\uDCF8 2-photo scan: top view + side view"
            updateStepIndicator(1, true)
        } else if (!isShowingCapturedImage && !isSidePhotoMode) {
            subtitleText.text = "Place your plate, then tap Capture"
            updateStepIndicator(1, false)
        }
    }

    fun applyCoachState(state: CameraCoachState) {
        val context = view.context
        val coachColor = when (state.severity) {
            CoachSeverity.BLOCKING -> R.color.status_critical
            CoachSeverity.WARNING -> R.color.status_critical
            CoachSeverity.TIP -> R.color.status_warning
            CoachSeverity.ACCEPTABLE -> R.color.status_warning
            CoachSeverity.GOOD -> R.color.status_normal
        }

        tvCoachPill.apply {
            text = state.message
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(context, R.drawable.bg_status_pill)?.mutate()
            val color = ContextCompat.getColor(context, coachColor)
            background?.setTint(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0x99))
            setTextColor(ContextCompat.getColor(context, R.color.text_on_primary))
        }

        captureButton.isEnabled = state.canCapture
        captureButton.alpha = if (state.canCapture) 1.0f else 0.5f
    }

    fun updateArIndicator(arReady: Boolean, arSupported: Boolean) {
        val context = view.context
        val color = when {
            arReady -> R.color.status_normal
            arSupported -> R.color.primary
            else -> R.color.text_disabled
        }

        val message = when {
            arReady -> "High accuracy"
            arSupported -> "Calibrating..."
            else -> "Basic mode"
        }
        tvArExplanation.text = message

        arIndicatorDot.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, color))
        arIndicatorDot.visibility = View.VISIBLE
        arIndicatorDot.contentDescription = when {
            arReady -> "AR: high accuracy"
            arSupported -> "AR: calibrating"
            else -> "AR: basic mode"
        }
    }

    fun showConfidenceBanner(strategy: MeasurementStrategy) {
        val context = view.context
        val bannerColor = when (strategy.accuracy) {
            MeasurementStrategy.Accuracy.HIGH -> R.color.status_normal
            MeasurementStrategy.Accuracy.GOOD -> R.color.status_warning
            MeasurementStrategy.Accuracy.MODERATE -> R.color.status_critical
        }
        val icon = when (strategy.accuracy) {
            MeasurementStrategy.Accuracy.HIGH -> "\uD83D\uDFE2"
            MeasurementStrategy.Accuracy.GOOD -> "\uD83D\uDFE1"
            MeasurementStrategy.Accuracy.MODERATE -> "\uD83D\uDFE0"
        }

        qualityStatusText.apply {
            text = "$icon ${strategy.label}"
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(context, R.drawable.bg_status_pill)?.mutate()
            val color = ContextCompat.getColor(context, bannerColor)
            background?.setTint(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xCC))
            setTextColor(ContextCompat.getColor(context, R.color.text_on_primary))
        }
    }

    fun showSkipAccuracyBanner() {
        val context = view.context
        qualityStatusText.apply {
            text = "\uD83D\uDFE0 Lower accuracy — no side photo"
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(context, R.drawable.bg_status_pill)?.mutate()
            val color = ContextCompat.getColor(context, R.color.status_warning)
            background?.setTint(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xCC))
            setTextColor(ContextCompat.getColor(context, R.color.text_on_primary))
        }
    }
}
