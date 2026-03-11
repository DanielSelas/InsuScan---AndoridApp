package com.example.insuscan.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R

class SplashAnimationFragment : Fragment(R.layout.fragment_splash_animation) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val duckWalking = view.findViewById<ImageView>(R.id.img_duck_walking)
        val duckCamera = view.findViewById<ImageView>(R.id.img_duck_camera)
        val flashOverlay = view.findViewById<View>(R.id.flash_overlay)

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        duckWalking.translationX = -screenWidth
        duckCamera.scaleX = SCALE_FROM
        duckCamera.scaleY = SCALE_FROM

        buildAnimation(duckWalking, duckCamera, flashOverlay, screenWidth).start()
    }

    private fun buildAnimation(
        duckWalking: ImageView,
        duckCamera: ImageView,
        flashOverlay: View,
        screenWidth: Float
    ): AnimatorSet {
        return AnimatorSet().apply {
            playTogether(
                createSlideAcross(duckWalking, screenWidth),
                createFadeOut(duckWalking, MIDPOINT_DELAY, CROSSFADE_DURATION),
                createFadeIn(duckCamera, MIDPOINT_DELAY, CROSSFADE_DURATION),
                createScaleUp(duckCamera, View.SCALE_X, SCALE_START_DELAY),
                createScaleUp(duckCamera, View.SCALE_Y, SCALE_START_DELAY),
                createFlashIn(flashOverlay),
                createFlashOut(flashOverlay),
                createFadeOut(duckCamera, FADE_OUT_DELAY, FINAL_FADE_DURATION)
            )
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    navigateToHome()
                }
            })
        }
    }

    private fun createSlideAcross(target: View, screenWidth: Float): ObjectAnimator {
        return ObjectAnimator.ofFloat(
            target, View.TRANSLATION_X, -screenWidth, screenWidth
        ).apply {
            duration = SLIDE_DURATION
            interpolator = LinearInterpolator()
        }
    }

    private fun createFadeIn(target: View, delay: Long, dur: Long): ObjectAnimator {
        return ObjectAnimator.ofFloat(target, View.ALPHA, 0f, 1f).apply {
            startDelay = delay
            duration = dur
            interpolator = DecelerateInterpolator()
        }
    }

    private fun createFadeOut(target: View, delay: Long, dur: Long): ObjectAnimator {
        return ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0f).apply {
            startDelay = delay
            duration = dur
            interpolator = AccelerateInterpolator()
        }
    }

    private fun createScaleUp(target: View, property: android.util.Property<View, Float>, delay: Long): ObjectAnimator {
        return ObjectAnimator.ofFloat(target, property, SCALE_FROM, SCALE_TO).apply {
            startDelay = delay
            duration = SCALE_DURATION
            interpolator = DecelerateInterpolator()
        }
    }

    private fun createFlashIn(flashOverlay: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(flashOverlay, View.ALPHA, 0f, 1f).apply {
            startDelay = FLASH_START_DELAY
            duration = FLASH_IN_DURATION
        }
    }

    private fun createFlashOut(flashOverlay: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(flashOverlay, View.ALPHA, 1f, 0f).apply {
            startDelay = FLASH_START_DELAY + FLASH_IN_DURATION
            duration = FLASH_OUT_DURATION
            interpolator = DecelerateInterpolator()
        }
    }

    private fun navigateToHome() {
        if (isAdded) {
            findNavController().navigate(R.id.action_splashAnimation_to_homeFragment)
        }
    }

    companion object {
        private const val SLIDE_DURATION = 1500L
        private const val MIDPOINT_DELAY = 750L
        private const val CROSSFADE_DURATION = 300L
        private const val SCALE_START_DELAY = MIDPOINT_DELAY + CROSSFADE_DURATION
        private const val SCALE_DURATION = 1000L
        private const val SCALE_FROM = 0.6f
        private const val SCALE_TO = 1.4f
        private const val FLASH_START_DELAY = 2600L
        private const val FLASH_IN_DURATION = 250L
        private const val FLASH_OUT_DURATION = 700L
        private const val FINAL_FADE_DURATION = 400L
        private val FADE_OUT_DELAY = FLASH_START_DELAY + FLASH_IN_DURATION + FLASH_OUT_DURATION + 200L
    }

}
