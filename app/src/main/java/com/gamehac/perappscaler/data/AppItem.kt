package com.gamehac.perappscaler.data

import android.graphics.drawable.Drawable

/**
 * Represents an installed application or game and its current downscaling configuration.
 */
data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isGame: Boolean,
    var scalePercent: Int = 100, // 100 means native resolution (1.0x)
    var fps: Int = 0 // 0 means default / uncapped
) {
    val isScaled: Boolean
        get() = scalePercent in 30..95 || fps > 0

    fun getScaleFactor(): Float = scalePercent / 100.0f
}
