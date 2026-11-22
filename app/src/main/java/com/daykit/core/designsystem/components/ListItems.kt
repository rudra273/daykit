package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors

/** Standard text-aligned inset for dividers in rows that carry a 32dp leading icon tile (`16 + 32 + 12 = 60dp`). */
val RowDividerIconIndent = 60.dp

/** A tappable row with optional vibrant accent icon tile, headline + supporting text, optional trailing value, and trailing slot. */
@Composable
fun AppListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    leadingAccent: Color = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
    trailingValue: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Global 56dp floor; longer text may increase the measured height.
            .defaultMinSize(minHeight = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(Spacing.md))
            }
            leadingIcon != null -> {
                AccentIconTile(icon = leadingIcon, accent = leadingAccent)
                Spacer(Modifier.width(Spacing.md))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingValue != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(if (trailingValue != null) Spacing.xs else Spacing.md))
            trailing()
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = Spacing.sm,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.extendedColors.textMuted,
        // No start inset: callers already pad their content region, so the label
        // aligns flush with the cards' left edge instead of double-indenting.
        // Marked as an accessibility heading so screen readers can jump by section.
        modifier = modifier
            .semantics { heading() }
            .padding(end = Spacing.lg, top = topPadding, bottom = Spacing.xs),
    )
}

@Composable
fun RowDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = RowDividerIconIndent,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.5.dp)
            .background(MaterialTheme.extendedColors.divider),
    )
}
