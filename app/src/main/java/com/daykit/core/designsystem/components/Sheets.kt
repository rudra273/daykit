package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.daykit.core.designsystem.AppleSystemGrays
import com.daykit.core.designsystem.SolidSurface
import com.daykit.core.designsystem.extendedColors

/**
 * Modal bottom sheet wrapper: elevated Apple modal surface, 28dp concentric top corners,
 * slim iOS-proportioned drag indicator, and subtle top specular hairline in Dark Mode.
 * Always solid: dense content (forms, lists) reads best on an opaque sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val ext = MaterialTheme.extendedColors
    // In Dark Mode, use systemGray6HighContrast (#242426) for the sheet base so inner
    // AppCards (#2C2C2E) and inputs (#3A3A3C) retain crisp elevation inside the sheet
    // while the sheet itself lifts clearly above the #1C1C1E page background.
    val sheetContainer = if (ext.isDark) AppleSystemGrays.Gray6DarkHighContrast else ext.elevated
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = sheetContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = sheetShape,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (ext.isDark) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.14f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(ext.textTertiary.copy(alpha = 0.45f)),
                )
            }
        },
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        SolidSurface(page = sheetContainer) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                content = content,
            )
        }
    }
}

/**
 * Alert dialog with Apple elevated surface (`systemGray5`/`systemGray4` in Dark Mode),
 * subtle inner rim, and platform cross-window blur behind (falls back to standard dim).
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissText: String? = "Cancel",
    destructiveConfirm: Boolean = false,
) {
    val ext = MaterialTheme.extendedColors
    val dialogShape = MaterialTheme.shapes.large
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = text?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = ext.textMuted) } },
        containerColor = if (ext.isDark) ext.card else ext.elevated,
        shape = dialogShape,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructiveConfirm) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismissRequest) {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = ext.textMuted)
                }
            }
        },
        modifier = modifier.then(
            if (ext.isDark) {
                Modifier.innerBevel(
                    dialogShape,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)),
                    ),
                )
            } else Modifier,
        ),
    )
    BlurBehindDialogWindow()
}

/** Enables platform FLAG_BLUR_BEHIND on the enclosing dialog window when supported. */
@Composable
private fun BlurBehindDialogWindow() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val wm = view.context.getSystemService(android.view.WindowManager::class.java)
    if (wm?.isCrossWindowBlurEnabled == true) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = 32 }
    }
}
