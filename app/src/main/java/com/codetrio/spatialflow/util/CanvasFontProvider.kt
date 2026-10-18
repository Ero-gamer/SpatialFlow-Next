package com.codetrio.spatialflow.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.codetrio.spatialflow.R

/**
 * Centralized variable font provider for all Canvas-based image generators
 * (Recap posters, Apple Music Lyrics cards, and Song Story images).
 *
 * Configures the bundled Google Sans Flex variable font (R.font.google_sans_flex)
 * with the exact variable font axes ('wght', 'wdth', 'ROND', 'opsz') defined in
 * the app's Compose typography system (Type.kt).
 */
object CanvasFontProvider {

    var cachedBaseTypeface: Typeface? = null
        private set

    /**
     * Resolves and caches the base Google Sans Flex typeface.
     */
    fun getBaseTypeface(context: Context): Typeface {
        cachedBaseTypeface?.let { return it }
        val tf = try {
            ResourcesCompat.getFont(context, R.font.google_sans_flex)
        } catch (_: Exception) {
            null
        } ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        cachedBaseTypeface = tf
        return tf
    }

    /**
     * Applies exact variable font axes to a [Paint] or [android.text.TextPaint].
     * Uses [Paint.setFontVariationSettings] on Android O+ (API 26+) for precision glyph rendering.
     */
    fun Paint.applyVariation(
        weight: Number,
        width: Number = 100f,
        round: Number = 0f,
        opticalSize: Number = 18f
    ) {
        val tf = cachedBaseTypeface
        if (tf != null && typeface != tf) {
            typeface = tf
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fontVariationSettings = "'wght' ${weight.toInt()}, 'wdth' ${width.toInt()}, 'ROND' ${round.toInt()}, 'opsz' ${opticalSize.toInt()}"
        }
    }

    /**
     * Preset: GoogleSansFlexRecapWide (wght 860, wdth 138, ROND 35, opsz 64)
     * Matches Compose GoogleSansFlexRecapWide: monumental, wide, heavy headlines,
     * total listening minutes, rank badges, and track titles.
     */
    fun Paint.applyRecapWide(
        weight: Number = 860f,
        width: Number = 138f,
        round: Number = 35f,
        opticalSize: Number = 64f,
        context: Context? = null
    ) {
        val tf = context?.let { getBaseTypeface(it) } ?: cachedBaseTypeface
        if (tf != null) typeface = tf
        applyVariation(weight, width, round, opticalSize)
    }

    /**
     * Preset: GoogleSansFlexRecapTitle (wght 720, wdth 124, ROND 30, opsz 32)
     * Matches Compose GoogleSansFlexRecapTitle: section headers, pills, badges, artist subtitles.
     */
    fun Paint.applyRecapTitle(
        weight: Number = 720f,
        width: Number = 124f,
        round: Number = 30f,
        opticalSize: Number = 32f,
        context: Context? = null
    ) {
        val tf = context?.let { getBaseTypeface(it) } ?: cachedBaseTypeface
        if (tf != null) typeface = tf
        applyVariation(weight, width, round, opticalSize)
    }

    /**
     * Preset: GoogleSansFlexDisplay (wght 760, wdth 122, ROND 42, opsz 57)
     * Matches Compose GoogleSansFlexDisplay: hero song share titles.
     */
    fun Paint.applyDisplay(
        weight: Number = 760f,
        width: Number = 122f,
        round: Number = 42f,
        opticalSize: Number = 57f,
        context: Context? = null
    ) {
        val tf = context?.let { getBaseTypeface(it) } ?: cachedBaseTypeface
        if (tf != null) typeface = tf
        applyVariation(weight, width, round, opticalSize)
    }

    /**
     * Preset: GoogleSansFlexTitle (wght 640, wdth 110, ROND 30, opsz 22)
     * Matches Compose GoogleSansFlexTitle.
     */
    fun Paint.applyTitle(
        weight: Number = 640f,
        width: Number = 110f,
        round: Number = 30f,
        opticalSize: Number = 22f,
        context: Context? = null
    ) {
        val tf = context?.let { getBaseTypeface(it) } ?: cachedBaseTypeface
        if (tf != null) typeface = tf
        applyVariation(weight, width, round, opticalSize)
    }

    /**
     * Preset: GoogleSansFlexBody (wght 440, wdth 102, ROND 18, opsz 16)
     * Matches Compose GoogleSansFlexBody: readable body text and artist subtitles.
     */
    fun Paint.applyBody(
        weight: Number = 440f,
        width: Number = 102f,
        round: Number = 18f,
        opticalSize: Number = 16f,
        context: Context? = null
    ) {
        val tf = context?.let { getBaseTypeface(it) } ?: cachedBaseTypeface
        if (tf != null) typeface = tf
        applyVariation(weight, width, round, opticalSize)
    }

    /**
     * Preset: GoogleSansFlexLabel (wght 680, wdth 108, ROND 46, opsz 14)
     * Matches Compose GoogleSansFlexLabel: watermark badges and compact chips.
     */
    fun Paint.applyLabel(
        weight: Number = 680f,
        width: Number = 108f,
        round: Number = 46f,
        opticalSize: Number = 14f,
        context: Context? = null
    ) {
        val tf = context?.let { getBaseTypeface(it) } ?: cachedBaseTypeface
        if (tf != null) typeface = tf
        applyVariation(weight, width, round, opticalSize)
    }

    /**
     * Preset: GoogleSansFlexLyrics (wght 700, wdth 100, ROND 0, opsz 36)
     * Matches bold lyrics typography on Apple Music share cards.
     */
    fun Paint.applyLyrics(
        weight: Number = 700f,
        width: Number = 100f,
        round: Number = 0f,
        opticalSize: Number = 36f,
        context: Context? = null
    ) {
        val tf = context?.let { getBaseTypeface(it) } ?: cachedBaseTypeface
        if (tf != null) typeface = tf
        applyVariation(weight, width, round, opticalSize)
    }
}
