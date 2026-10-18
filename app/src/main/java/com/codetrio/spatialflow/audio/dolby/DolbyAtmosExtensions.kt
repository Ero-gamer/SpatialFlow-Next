package com.codetrio.spatialflow.audio.dolby

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.codetrio.spatialflow.model.SongItem

/**
 * Extension helpers for Media3 formats and SongItem metadata related to Dolby Atmos.
 */
object DolbyAtmosExtensions {

    /**
     * Checks if a Media3 [Format] represents a genuine Dolby Atmos stream.
     */
    fun Format?.isDolbyAtmos(): Boolean {
        if (this == null) return false
        val mime = sampleMimeType ?: containerMimeType ?: ""
        return isEac3Joc(mime) || isAc4(mime) || (isEac3(mime) && (channelCount > 2 || codecs?.contains("joc", ignoreCase = true) == true))
    }

    /**
     * Checks if a MIME string represents E-AC-3 JOC (Dolby Digital Plus with Joint Object Coding).
     */
    fun isEac3Joc(mime: String?): Boolean {
        if (mime == null) return false
        return mime.equals(DolbyAtmosDetector.MIME_EAC3_JOC, ignoreCase = true)
    }

    /**
     * Checks if a MIME string represents Dolby AC-4.
     */
    fun isAc4(mime: String?): Boolean {
        if (mime == null) return false
        return mime.equals(DolbyAtmosDetector.MIME_AC4, ignoreCase = true) ||
                mime.equals(MimeTypes.AUDIO_AC4, ignoreCase = true)
    }

    /**
     * Checks if a MIME string represents E-AC-3.
     */
    fun isEac3(mime: String?): Boolean {
        if (mime == null) return false
        return mime.equals(DolbyAtmosDetector.MIME_EAC3, ignoreCase = true) ||
                mime.equals(MimeTypes.AUDIO_E_AC3, ignoreCase = true)
    }

    /**
     * Resolves the stream URI based on whether Atmos playback is currently supported and requested.
     */
    fun SongItem.resolveStreamUri(shouldUseAtmos: Boolean): String {
        return if (shouldUseAtmos && !atmosStreamUri.isNullOrBlank()) {
            atmosStreamUri!!
        } else {
            path ?: "content://media/external/audio/media/$id"
        }
    }
}
