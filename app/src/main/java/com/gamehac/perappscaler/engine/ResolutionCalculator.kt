package com.gamehac.perappscaler.engine

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import kotlin.math.roundToInt

object ResolutionCalculator {

    data class DisplaySize(val width: Int, val height: Int)

    fun getNativeDisplaySize(context: Context): DisplaySize {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            DisplaySize(bounds.width(), bounds.height())
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            DisplaySize(point.x, point.y)
        }
    }

    /**
     * Calculates the scaled resolution in pixels, ensuring dimensions are even numbers.
     */
    fun calculateScaledResolution(nativeWidth: Int, nativeHeight: Int, scaleFactor: Float): DisplaySize {
        if (scaleFactor >= 0.99f) {
            return DisplaySize(nativeWidth, nativeHeight)
        }
        var w = (nativeWidth * scaleFactor).roundToInt()
        var h = (nativeHeight * scaleFactor).roundToInt()

        // Round to even numbers for hardware buffer alignment
        if (w % 2 != 0) w += 1
        if (h % 2 != 0) h += 1

        return DisplaySize(w, h)
    }

    /**
     * Formats estimated resolution and pixel savings percentage.
     */
    fun formatEstimatedResolution(nativeWidth: Int, nativeHeight: Int, scaleFactor: Float): String {
        if (scaleFactor >= 0.99f) {
            return "$nativeWidth × $nativeHeight (100%)"
        }
        val scaled = calculateScaledResolution(nativeWidth, nativeHeight, scaleFactor)
        val nativePixels = nativeWidth.toLong() * nativeHeight.toLong()
        val scaledPixels = scaled.width.toLong() * scaled.height.toLong()
        val savings = ((1.0 - (scaledPixels.toDouble() / nativePixels.toDouble())) * 100).roundToInt()

        return "${scaled.width} × ${scaled.height} (-$savings% px)"
    }
}
