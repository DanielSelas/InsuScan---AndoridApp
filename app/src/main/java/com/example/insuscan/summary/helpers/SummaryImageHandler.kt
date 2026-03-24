package com.example.insuscan.summary.helpers

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.insuscan.R
import com.example.insuscan.meal.MealSessionManager
import java.io.File

class SummaryImageHandler(private val context: Context, private val ui: SummaryUiManager) {

    fun displayMealImage() {
        val meal = MealSessionManager.currentMeal
        val imagePath = meal?.imagePath

        if (imagePath.isNullOrEmpty()) {
            ui.imageCard.visibility = View.GONE
            return
        }

        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            ui.imageCard.visibility = View.GONE
            return
        }

        Glide.with(context)
            .load(imageFile)
            .into(ui.mealImageView)

        ui.imageCard.visibility = View.VISIBLE

        ui.mealImageView.setOnClickListener {
            showFullscreenImage(imagePath)
        }
    }

    private fun showFullscreenImage(imagePath: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_fullscreen)

        val imageView = dialog.findViewById<ImageView>(R.id.iv_fullscreen_image)
        val closeBtn = dialog.findViewById<ImageButton>(R.id.btn_close)

        Glide.with(context)
            .load(File(imagePath))
            .into(imageView)

        closeBtn.setOnClickListener { dialog.dismiss() }
        imageView.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
