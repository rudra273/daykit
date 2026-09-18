package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors

/**
 * App top bar with a translucent scrim for content that scrolls beneath it.
 */
/** Standard header height, excluding the status-bar inset. Every screen uses this via [AppTopBar]. */
val AppTopBarHeight = 48.dp

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(glassChromeBrush())
            // Keep scrolled content visible without allowing taps through the header.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(AppTopBarHeight)
                .padding(horizontal = Spacing.sm),
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
                        style = MaterialTheme.typography.titleLarge,
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
                    style = MaterialTheme.typography.titleLarge,
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
 * Top bar with a built-in search affordance. At rest it shows [title] and a search
 * icon; tapping the icon morphs the title area into an autofocused inline text field
 * with a clear/close button. Filtering is driven by [query]/[onQueryChange]; the
 * caller decides what to do with the query.
 *
 * [titleContent] replaces the plain [title] text while search is inactive. [title] is
 * still required as the accessible name.
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
            titleContent = {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = searchPlaceholder,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.extendedColors.textMuted,
                                )
                            }
                            inner()
                        }
                    },
                )
            },
            actions = {
                IconButton(
                    onClick = {
                        if (query.isEmpty()) onSearchActiveChange(false) else onQueryChange("")
                    },
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    } else {
        AppTopBar(
            modifier = modifier,
            title = title,
            onBack = onBack,
            titleContent = titleContent,
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

/** Plain back arrow with a full 48dp touch target. */
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(MinTouchTarget)) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}
