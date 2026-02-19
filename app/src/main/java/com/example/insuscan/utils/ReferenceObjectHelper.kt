package com.example.insuscan.utils

import android.app.AlertDialog
import android.content.Context
import com.example.insuscan.R

/**
 * Shared utility for reference object selection.
 * Used by both ScanFragment and ChatFragment to avoid code duplication.
 */
object ReferenceObjectHelper {

    enum class ReferenceObjectType(
        val displayNameResId: Int,
        val lengthCm: Float,
        val serverValue: String
    ) {
        INSULIN_SYRINGE(R.string.ref_option_insulin_syringe, 13f, "INSULIN_SYRINGE"),
        SYRINGE_KNIFE(R.string.ref_option_syringe_knife, 21f, "SYRINGE_KNIFE"),
        CARD(R.string.ref_option_card, 8.56f, "CARD"),
        NONE(R.string.ref_option_none, 0f, "NONE");
    }

    /**
     * Shows a dialog letting the user pick one of the 4 reference object options.
     * Calls [onSelected] with the chosen type, or does nothing if dismissed.
     */
    fun showSelectionDialog(
        context: Context,
        onSelected: (ReferenceObjectType) -> Unit
    ) {
        val types = ReferenceObjectType.entries
        val labels = types.map { context.getString(it.displayNameResId) }.toTypedArray()

        var selected = 0 // default to first option

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.ref_dialog_title))
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setPositiveButton(context.getString(R.string.action_continue)) { dialog, _ ->
                dialog.dismiss()
                onSelected(types[selected])
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Maps a server string back to a [ReferenceObjectType], or null if unrecognized.
     */
    fun fromServerValue(value: String?): ReferenceObjectType? {
        if (value == null) return null
        return ReferenceObjectType.entries.find {
            it.serverValue.equals(value, ignoreCase = true)
        }
    }

    /**
     * Returns a user-facing display label for a server value string.
     */
    fun displayLabel(context: Context, serverValue: String?): String {
        val type = fromServerValue(serverValue)
        return if (type != null) context.getString(type.displayNameResId)
        else serverValue ?: context.getString(R.string.ref_option_none)
    }
}
