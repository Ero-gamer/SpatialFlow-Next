package com.codetrio.spatialflow.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SnackbarEvent(
    val message: String,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val displayDurationMs: Long = 1800L,
    val iconResId: Int? = null,
    val iconVector: ImageVector? = null,
    val actionLabel: String? = null,
    val onActionClick: (() -> Unit)? = null
)

class CustomSnackbarVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    val displayDurationMs: Long = 1800L,
    val iconResId: Int? = null,
    val iconVector: ImageVector? = null,
    val onActionClick: (() -> Unit)? = null
) : SnackbarVisuals

object SnackbarController {
    private val _events = MutableSharedFlow<SnackbarEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    fun sendEvent(event: SnackbarEvent) {
        _events.tryEmit(event)
    }

    @JvmOverloads
    fun showMessage(
        message: String,
        iconResId: Int? = null,
        iconVector: ImageVector? = null,
        displayDurationMs: Long = 1800L,
        actionLabel: String? = null,
        onActionClick: (() -> Unit)? = null
    ) {
        sendEvent(
            SnackbarEvent(
                message = message,
                duration = SnackbarDuration.Short,
                displayDurationMs = displayDurationMs,
                iconResId = iconResId,
                iconVector = iconVector,
                actionLabel = actionLabel,
                onActionClick = onActionClick
            )
        )
    }
}
