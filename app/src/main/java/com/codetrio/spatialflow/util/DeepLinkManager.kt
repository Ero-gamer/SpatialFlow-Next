package com.codetrio.spatialflow.util

import android.content.Intent
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles extraction, validation, and deduplication of SpatialFlow song share deep links.
 * Supports App Links (https://spatialflowshare.vercel.app/s/{shareId}),
 * custom fallback scheme (spatialflow://song/{shareId}), and system share-sheet text intents.
 */
object DeepLinkManager {

    private const val EXTRA_SHARE_LINK_PROCESSED = "spatialflow.extra.SHARE_LINK_PROCESSED"
    private const val SHARE_HOST = "spatialflowshare.vercel.app"
    private const val DEBOUNCE_WINDOW_MS = 4000L

    // Precompiled regex patterns avoiding repeated allocations
    private val APP_LINK_REGEX = Regex("""(?:https?://)?spatialflowshare\.vercel\.app/s/([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val CUSTOM_SCHEME_REGEX = Regex("""spatialflow://(?:song|share)/([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)

    // Deduplication cache: shareId -> lastProcessedTimestamp
    private val processedShareIds = ConcurrentHashMap<String, Long>()

    /**
     * Extracts and consumes the shareId from an incoming Intent.
     * Guarantees each link is processed only once, even across configuration changes
     * or multiple onNewIntent dispatches.
     */
    fun consumeShareId(intent: Intent?): String? {
        if (intent == null || intent.getBooleanExtra(EXTRA_SHARE_LINK_PROCESSED, false)) {
            return null
        }

        val shareId = extractShareId(intent) ?: return null

        val now = System.currentTimeMillis()
        val lastProcessed = processedShareIds[shareId] ?: 0L
        if (now - lastProcessed < DEBOUNCE_WINDOW_MS) {
            intent.putExtra(EXTRA_SHARE_LINK_PROCESSED, true)
            return null
        }

        processedShareIds[shareId] = now
        intent.putExtra(EXTRA_SHARE_LINK_PROCESSED, true)

        if (processedShareIds.size > 20) {
            processedShareIds.entries.removeIf { now - it.value > DEBOUNCE_WINDOW_MS * 2 }
        }

        return shareId
    }

    /**
     * Extracts shareId from intent data or intent text extras without consuming it.
     */
    fun extractShareId(intent: Intent?): String? {
        if (intent == null) return null

        // 1. Check direct Uri data (ACTION_VIEW, etc.)
        intent.data?.let { dataUri ->
            extractFromUri(dataUri)?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // 2. Check Intent.ACTION_SEND text
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                extractFromText(text)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        return null
    }

    /**
     * Extracts shareId from Uri.
     * Examples:
     * - https://spatialflowshare.vercel.app/s/eW7yspwd8m
     * - http://spatialflowshare.vercel.app/s/eW7yspwd8m
     * - spatialflow://song/eW7yspwd8m
     * - spatialflow://song?shareId=eW7yspwd8m
     */
    fun extractFromUri(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null

        if ((scheme == "https" || scheme == "http") && (host == SHARE_HOST || host == "www.$SHARE_HOST")) {
            val pathSegments = uri.pathSegments
            if (pathSegments.size >= 2 && pathSegments[0] == "s") {
                return sanitizeShareId(pathSegments[1])
            }
            val queryId = uri.getQueryParameter("shareId") ?: uri.getQueryParameter("id")
            if (!queryId.isNullOrBlank()) {
                return sanitizeShareId(queryId)
            }
        }

        if (scheme == "spatialflow") {
            if (host == "song" || host == "share") {
                val pathSegments = uri.pathSegments
                val idFromPath = pathSegments.firstOrNull()
                if (!idFromPath.isNullOrBlank()) {
                    return sanitizeShareId(idFromPath)
                }
                val queryId = uri.getQueryParameter("shareId") ?: uri.getQueryParameter("id") ?: uri.getQueryParameter("v")
                if (!queryId.isNullOrBlank()) {
                    return sanitizeShareId(queryId)
                }
            }
        }

        return null
    }

    /**
     * Extracts shareId from arbitrary shared text containing an App Link or custom scheme link.
     */
    fun extractFromText(text: String): String? {
        // Fast-path guard: if the text does not even contain our keywords, skip regex matching entirely
        if (!text.contains("spatialflow", ignoreCase = true)) return null

        // Match https://spatialflowshare.vercel.app/s/{shareId}
        APP_LINK_REGEX.find(text)?.let { match ->
            return sanitizeShareId(match.groupValues[1])
        }

        // Match spatialflow://song/{shareId}
        CUSTOM_SCHEME_REGEX.find(text)?.let { match ->
            return sanitizeShareId(match.groupValues[1])
        }

        return null
    }

    /**
     * Zero-allocation validator for shareId strings.
     */
    fun sanitizeShareId(raw: String): String? {
        val cleaned = raw.trim().trim('/')
        if (cleaned.length !in 3..64) return null
        for (i in 0 until cleaned.length) {
            val c = cleaned[i]
            if (c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9' && c != '_' && c != '-') {
                return null
            }
        }
        return cleaned
    }
}
