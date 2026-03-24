package com.example.insuscan.chat.viewholders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.chat.ChatMessage

class BotDoseResultVH(view: View) : RecyclerView.ViewHolder(view) {
    private val carbDose: TextView = view.findViewById(R.id.tv_dose_carb)
    private val correctionLayout: View = view.findViewById(R.id.layout_dose_correction)
    private val correctionDose: TextView = view.findViewById(R.id.tv_dose_correction)
    private val sickLayout: View = view.findViewById(R.id.layout_dose_sick)
    private val sickDose: TextView = view.findViewById(R.id.tv_dose_sick)
    private val stressLayout: View = view.findViewById(R.id.layout_dose_stress)
    private val stressDose: TextView = view.findViewById(R.id.tv_dose_stress)
    private val exerciseLayout: View = view.findViewById(R.id.layout_dose_exercise)
    private val exerciseDose: TextView = view.findViewById(R.id.tv_dose_exercise)
    private val iobLayout: View = view.findViewById(R.id.layout_dose_iob)
    private val iobDose: TextView = view.findViewById(R.id.tv_dose_iob)
    private val finalDose: TextView = view.findViewById(R.id.tv_dose_final)

    fun bind(msg: ChatMessage.BotDoseResult) {
        val r = msg.doseResult

        // Reset all optional rows
        correctionLayout.visibility = View.GONE
        sickLayout.visibility = View.GONE
        stressLayout.visibility = View.GONE
        exerciseLayout.visibility = View.GONE
        iobLayout.visibility = View.GONE

        carbDose.text = String.format("+%.2f u", r.carbDose)

        if (r.correctionDose != 0f) {
            correctionLayout.visibility = View.VISIBLE
            correctionDose.text = String.format("%+.2f u", r.correctionDose)
        }
        if (r.sickAdj != 0f) {
            sickLayout.visibility = View.VISIBLE
            sickDose.text = String.format("+%.2f u", r.sickAdj)
        }
        if (r.stressAdj != 0f) {
            stressLayout.visibility = View.VISIBLE
            stressDose.text = String.format("+%.2f u", r.stressAdj)
        }
        if (r.exerciseAdj != 0f) {
            exerciseLayout.visibility = View.VISIBLE
            exerciseDose.text = String.format("-%.2f u", r.exerciseAdj)
        }
        if (r.iob != 0f) {
            iobLayout.visibility = View.VISIBLE
            iobDose.text = String.format("-%.2f u", r.iob)
        }

        finalDose.text = String.format("%.2f u", r.roundedDose)
    }
}
