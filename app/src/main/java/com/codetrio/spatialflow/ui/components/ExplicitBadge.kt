package com.codetrio.spatialflow.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.R

@Composable
fun ExplicitBadge(
    modifier: Modifier = Modifier,
    size: Dp = 13.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_explicit),
        contentDescription = "Explicit Content",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier.size(size)
    )
}
