package com.codetrio.spatialflow.ui.player

enum class LyricsShareAspectRatio(val displayName: String, val width: Int, val height: Int, val ratioAspect: Float) {
    SQUARE_1_1("1:1 Square", 1080, 1080, 1.0f),
    PORTRAIT_4_5("4:5 Portrait", 1080, 1350, 0.8f),
    STORY_9_16("9:16 Story", 1080, 1920, 0.5625f)
}

enum class LyricsShareTheme(val displayName: String) {
    FLUID("Fluid Theme"),
    STATIC("Static Theme")
}

enum class LyricsShareTextAlign(val displayName: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

data class LyricsShareImageOptions(
    val aspectRatio: LyricsShareAspectRatio = LyricsShareAspectRatio.SQUARE_1_1,
    val theme: LyricsShareTheme = LyricsShareTheme.FLUID,
    val textAlign: LyricsShareTextAlign = LyricsShareTextAlign.LEFT,
    val blurRadius: Float = 24f,
    val backgroundDim: Float = 1.0f,
    val showArtwork: Boolean = true,
    val showWatermark: Boolean = true
)
