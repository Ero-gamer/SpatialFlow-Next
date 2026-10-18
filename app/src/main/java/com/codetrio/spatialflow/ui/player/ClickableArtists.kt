package com.codetrio.spatialflow.ui.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

data class ParsedArtist(
    val name: String,
    val artistId: String?
)

fun parseArtistLine(rawArtist: String, primaryArtistId: String?): List<ParsedArtist> {
    if (rawArtist.isBlank()) return emptyList()

    val regex = Regex("[,;&/]|\\bfeat\\.\\b|\\bft\\.\\b", RegexOption.IGNORE_CASE)
    val tokens = rawArtist.split(regex).map { it.trim() }.filter { it.isNotEmpty() }

    if (tokens.isEmpty()) return listOf(ParsedArtist(rawArtist, primaryArtistId))

    return tokens.mapIndexed { index, name ->
        ParsedArtist(
            name = name,
            artistId = if (index == 0) primaryArtistId else null
        )
    }
}

/**
 * Renders an artist line where each artist name is individually tappable.
 * Uses AnnotatedString string annotations and TextLayoutResult hit testing
 * to resolve which artist segment was tapped.
 */
@Composable
fun ClickableArtists(
    rawArtist: String,
    primaryArtistId: String?,
    onArtistClick: (artistId: String?, artistName: String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLongClick: (() -> Unit)? = null
) {
    val parsedArtists = remember(rawArtist, primaryArtistId) {
        parseArtistLine(rawArtist, primaryArtistId)
    }

    val annotatedString = remember(parsedArtists) {
        buildAnnotatedString {
            parsedArtists.forEachIndexed { index, artist ->
                val idTag = artist.artistId ?: ""
                pushStringAnnotation(tag = "artist_name", annotation = artist.name)
                pushStringAnnotation(tag = "artist_id", annotation = idTag)
                append(artist.name)
                pop()
                pop()
                if (index != parsedArtists.lastIndex) {
                    append(" & ")
                }
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotatedString) {
            detectTapGestures(
                onTap = { offset ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val position = layout.getOffsetForPosition(offset)

                    val nameAnnotations = annotatedString.getStringAnnotations("artist_name", position, position)
                    val idAnnotations = annotatedString.getStringAnnotations("artist_id", position, position)

                    val tappedName = nameAnnotations.firstOrNull()?.item
                    val tappedId = idAnnotations.firstOrNull()?.item?.ifBlank { null }

                    if (!tappedName.isNullOrBlank()) {
                        onArtistClick(tappedId, tappedName)
                    } else if (parsedArtists.isNotEmpty()) {
                        onArtistClick(parsedArtists[0].artistId, parsedArtists[0].name)
                    }
                },
                onLongPress = onLongClick?.let { handler -> { handler() } }
            )
        }
    )
}
