package com.daykit.core.designsystem.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.extendedColors

private val TILE_SIZE = 36.dp
private val TILE_SHAPE = RoundedCornerShape(10.dp)

/** A launcher icon rendered as a rounded tile, for rows that list installed apps. */
@Composable
fun AppIconTile(icon: Drawable) {
    val bitmap = androidx.compose.runtime.remember(icon) {
        icon.toBitmap(width = 96, height = 96).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
            .size(TILE_SIZE)
            .clip(TILE_SHAPE),
    )
}

/** Fallback tile for an app with no loadable icon — a letter on an accent wash. */
@Composable
fun AppMonogramTile(letter: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(TILE_SIZE)
            .background(color = accent.asAccentContainer(), shape = TILE_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Renders [icon] when present, else a monogram from [label]. The monogram accent
 * is picked deterministically from [packageName] so the same app keeps the same
 * color across screens.
 */
@Composable
fun AppIconOrMonogram(icon: Drawable?, label: String, packageName: String) {
    if (icon != null) {
        AppIconTile(icon = icon)
        return
    }
    val accents = MaterialTheme.extendedColors.accents
    val palette = listOf(
        accents.blue,
        accents.teal,
        accents.green,
        accents.red,
        accents.orange,
        accents.yellow,
        accents.purple,
        accents.pink,
        accents.indigo,
    )
    AppMonogramTile(
        letter = label.firstOrNull()?.uppercase() ?: "#",
        accent = palette[Math.floorMod(packageName.hashCode(), palette.size)],
    )
}
