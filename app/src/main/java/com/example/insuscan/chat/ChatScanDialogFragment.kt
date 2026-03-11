package com.example.insuscan.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import com.example.insuscan.meal.Meal
import com.example.insuscan.scan.CameraScanFragment
import com.example.insuscan.scan.CapturedScanData
import com.example.insuscan.scan.ScanResultCallback

class ChatScanDialogFragment : DialogFragment(), ScanResultCallback {

    var onResult: ((Meal) -> Unit)? = null
    var onImageCaptured: ((CapturedScanData) -> Unit)? = null

    companion object {
        fun newInstance(openGalleryDirectly: Boolean): ChatScanDialogFragment {
            val args = Bundle().apply {
                putBoolean("open_gallery_directly", openGalleryDirectly)
            }
            return ChatScanDialogFragment().apply { arguments = args }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(requireContext()).apply {
            id = View.generateViewId()
            tag = "scan_container"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            val openGallery = arguments?.getBoolean("open_gallery_directly") ?: false

            val cameraFragment = CameraScanFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("open_gallery_directly", openGallery)
                    putBoolean("capture_only_mode", true)
                }
            }

            childFragmentManager.beginTransaction()
                .replace(view.id, cameraFragment)
                .commit()
        }
    }

    override fun onImageCapturedForBackground(data: CapturedScanData) {
        onImageCaptured?.invoke(data)
        dismiss()
    }

    override fun onScanSuccess(meal: Meal) {
        onResult?.invoke(meal)
        dismiss()
    }

    override fun onScanCancelled() {
        dismiss()
    }
}