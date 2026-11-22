package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors

/**
 * App top bar with an Apple-aligned margin and translucent scrim for content that scrolls beneath it.
 */
/** Standard header height, excluding the status-bar inset. Every screen uses this via [AppTopBar]. */
val AppTopBarHeight = 52.dp

/**
 * Header height for top-level tabs whose title is a single line. Aligned flush with
 * the 16dp ([Spacing.lg]) content grid below it.
 */
val AppTopBarCompactHeight = 44.dp

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    height: Dp = AppTopBarHeight,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isTopLevel = onBack == null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .headerBackground()
            // Keep scrolled content visible without allowing taps through the header.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(height)
                .padding(
                    start = if (isTopLevel) Spacing.lg else Spacing.xs,
                    end = Spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                AppBackButton(onClick = onBack)
                Spacer(Modifier.width(Spacing.xs))
            }
            if (titleContent != null) {
                Box(modifier = Modifier.weight(1f)) { titleContent() }
            } else if (subtitle != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = if (isTopLevel) {
                            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = title,
                    style = if (isTopLevel) {
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            actions()
        }
    }
}

/**
 * Top bar with an Apple-style inline capsule search bar. At rest it shows [title] and
 * a search icon; tapping the icon morphs the header into a rounded capsule search pill
 * with a leading magnifying glass, inline clear button, and trailing "Cancel" action.
 */
@Composable
fun SearchAppTopBar(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    searchPlaceholder: String = "Search",
    titleContent: (@Composable () -> Unit)? = null,
    height: Dp = AppTopBarHeight,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }
    if (searchActive) {
        AppTopBar(
            modifier = modifier,
            title = title,
            onBack = onBack,
            height = height.coerceAtLeast(48.dp),
            titleContent = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.extendedColors.inputField)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = searchPlaceholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.extendedColors.textTertiary,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.extendedColors.textTertiary,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { onQueryChange("") },
                        )
                    }
                }
            },
            actions = {
                AppTextButton(
                    text = "Cancel",
                    onClick = {
                        onQueryChange("")
                        onSearchActiveChange(false)
                    },
                )
            },
        )
    } else {
        AppTopBar(
            modifier = modifier,
            title = title,
            onBack = onBack,
            titleContent = titleContent,
            height = height,
            actions = {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                actions()
            },
        )
    }
}

/** Apple-style circular back chevron button with a full 48dp touch target. */
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(MinTouchTarget)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.extendedColors.inputField.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBackIos,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .size(16.dp),
            )
        }
    }
}
