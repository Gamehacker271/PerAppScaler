package com.gamehac.perappscaler.data

/**
 * Game Mode intervention settings for scaling and FPS cap.
 */
data class InterventionConfig(
    val scaleFactor: Float = 1.0f,
    val fpsOverride: Int = 0
) {
    val scalePercent: Int
        get() = (scaleFactor * 100).toInt()
}
