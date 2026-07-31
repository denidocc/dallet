package com.dallet.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .clip(CardShape)
        .background(MaterialTheme.colorScheme.surface)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(16.dp)

    Column(modifier = base, content = content)
}
