package com.example.insuscan.analysis.estimation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.insuscan.analysis.model.FoodRegion
import com.example.insuscan.meal.FoodItem
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Takes GPT bounding boxes + captured image + pixelToCmRatio,
 * runs GrabCut per food item, and returns refined area in cm².
 */
object FoodRegionAnalyzer {

    private const val TAG = "FoodRegionAnalyzer"
    private const val GRABCUT_ITERATIONS = 3

    /**
     * For each food item that has a bbox, run GrabCut to get precise
     * pixel mask, then convert to cm² using the given scale.
     *
     * @param bitmap         original captured image
     * @param foodItems      items from server (with bbox fields)
     * @param pixelToCmRatio scale from reference object detection
     * @param plateBounds    plate rect in pixels (optional, for masking)
     * @return list of measured regions, or empty if nothing to refine
     */
    fun analyze(
        bitmap: Bitmap,
        foodItems: List<FoodItem>,
        pixelToCmRatio: Float,
        plateBounds: Rect? = null
    ): List<FoodRegion> {
        val itemsWithBbox = foodItems.filter { hasBbox(it) }
        if (itemsWithBbox.isEmpty() || pixelToCmRatio <= 0f) {
            Log.d(TAG, "Skipping: ${itemsWithBbox.size} items with bbox, ratio=$pixelToCmRatio")
            return emptyList()
        }

        val imgW = bitmap.width
        val imgH = bitmap.height
        val cm2PerPixel = pixelToCmRatio * pixelToCmRatio

        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        val bgrMat = Mat()
        Imgproc.cvtColor(srcMat, bgrMat, Imgproc.COLOR_RGBA2BGR)

        val results = mutableListOf<FoodRegion>()

        for (item in itemsWithBbox) {
            try {
                val region = analyzeItem(bgrMat, item, imgW, imgH, cm2PerPixel, plateBounds)
                if (region != null) results.add(region)
            } catch (e: Exception) {
                Log.w(TAG, "GrabCut failed for ${item.name}: ${e.message}")
            }
        }

        srcMat.release()
        bgrMat.release()

        Log.d(TAG, "Analyzed ${results.size}/${itemsWithBbox.size} food regions")
        return results
    }

    private fun analyzeItem(
        bgrMat: Mat,
        item: FoodItem,
        imgW: Int, imgH: Int,
        cm2PerPixel: Float,
        plateBounds: Rect?
    ): FoodRegion? {
        val x = (item.bboxXPct!! / 100f * imgW).toInt().coerceIn(0, imgW - 1)
        val y = (item.bboxYPct!! / 100f * imgH).toInt().coerceIn(0, imgH - 1)
        val w = (item.bboxWPct!! / 100f * imgW).toInt().coerceIn(1, imgW - x)
        val h = (item.bboxHPct!! / 100f * imgH).toInt().coerceIn(1, imgH - y)

        if (w < 10 || h < 10) {
            Log.w(TAG, "${item.name}: bbox too small (${w}x${h})")
            return null
        }

        val rect = org.opencv.core.Rect(x, y, w, h)
        val mask = Mat.zeros(bgrMat.size(), CvType.CV_8UC1)
        val bgModel = Mat()
        val fgModel = Mat()

        Imgproc.grabCut(bgrMat, mask, rect, bgModel, fgModel,
            GRABCUT_ITERATIONS, Imgproc.GC_INIT_WITH_RECT)

        // foreground = GC_FGD (1) or GC_PR_FGD (3)
        val fgMask = Mat()
        Core.inRange(mask, Scalar(1.0), Scalar(1.0), fgMask)
        val prFgMask = Mat()
        Core.inRange(mask, Scalar(3.0), Scalar(3.0), prFgMask)
        Core.bitwise_or(fgMask, prFgMask, fgMask)

        // Mask out anything outside the plate
        if (plateBounds != null) {
            val plateMask = Mat.zeros(bgrMat.size(), CvType.CV_8UC1)
            Imgproc.rectangle(plateMask,
                Point(plateBounds.left.toDouble(), plateBounds.top.toDouble()),
                Point(plateBounds.right.toDouble(), plateBounds.bottom.toDouble()),
                Scalar(255.0), -1)
            Core.bitwise_and(fgMask, plateMask, fgMask)
            plateMask.release()
        }

        val fgPixels = Core.countNonZero(fgMask).toFloat()
        val areaCm2 = fgPixels * cm2PerPixel

        // Cleanup
        mask.release()
        bgModel.release()
        fgModel.release()
        fgMask.release()
        prFgMask.release()

        Log.d(TAG, "${item.name}: ${fgPixels.toInt()}px → ${"%.1f".format(areaCm2)}cm²")

        return FoodRegion(
            foodName = item.name,
            areaCm2 = areaCm2,
            heightCm = heightCategoryToCm(item)
        )
    }

    /**
     * Map height from GPT (same logic as server).
     * Gets refined when server processes foodRegionsJson.
     */
    private fun heightCategoryToCm(item: FoodItem): Float {
        return 1.5f
    }

    private fun hasBbox(item: FoodItem): Boolean {
        return item.bboxXPct != null && item.bboxYPct != null
                && item.bboxWPct != null && item.bboxHPct != null
                && item.bboxWPct > 0f && item.bboxHPct > 0f
    }
}
