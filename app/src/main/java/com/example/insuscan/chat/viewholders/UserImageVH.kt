package com.example.insuscan.chat.viewholders

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.insuscan.R
import java.io.File

class UserImageVH(view: View) : RecyclerView.ViewHolder(view) {
    private val iv: ImageView = view.findViewById(R.id.iv_user_image)
    fun bind(imagePath: String) {
        Glide.with(iv.context).load(File(imagePath)).centerCrop().into(iv)
    }
}
