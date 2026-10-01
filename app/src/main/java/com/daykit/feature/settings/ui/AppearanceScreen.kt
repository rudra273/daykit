package com.daykit.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daykit.AppContainer
import com.daykit.core.designsystem.HapticStore
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.ThemeMode
import com.daykit.core.designsystem.ThemeModeStore
import com.daykit.core.designsystem.background.CardStyle
import com.daykit.core.designsystem.background.CardStyleStore
import com.daykit.core.designsystem.background.CustomWallpaper
import com.daykit.core.designsystem.background.GeneratedArt
import com.daykit.core.designsystem.background.PageBackground
import com.daykit.core.designsystem.background.PageBackgroundKind
import com.daykit.core.designsystem.background.PageBackgroundStore
import com.daykit.core.designsystem.background.meshPalette
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppRadioButton
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.isAppInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppearanceScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val mode by ThemeModeStore.rememberThemeMode()
    val hapticsEnabled by HapticStore.rememberHapticsEnabled()
    val cardStyle by CardStyleStore.rememberState()
    val background by PageBackgroundStore.rememberState()
    val wallpaperRevision by CustomWallpaper.revision.collectAsState()
    val hasPhoto = remember(wallpaperRevision) { CustomWallpaper.exists(context) }
    var importing by remember { mutableStateOf(false) }
    val accents = MaterialTheme.extendedColors.accents

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            runCatching { CustomWallpaper.import(context, uri) }
                .onSuccess { PageBackgroundStore.set(context, PageBackgroundKind.Custom) }
                .onFailure { snackbar.showSnackbar("Couldn't use that photo.") }
            importing = false
        }
    }
    fun choosePhoto() {
        // The picker backgrounds DayKit; keep this screen able to take the result.
        container.sensitiveKeyManager.expectingActivityResult = true
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val themeOptions = listOf(
        Triple(ThemeMode.SYSTEM, "System default", Icons.Rounded.BrightnessAuto to accents.blue),
        Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode to accents.orange),
        Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode to accents.indigo),
    )
    val styleOptions = listOf(
        Triple(CardStyle.Flat, "Clean cards that sit on the page by color alone.", Icons.Rounded.CropSquare to accents.blue),
        Triple(CardStyle.Clay, "A soft lift and edge, like pressed clay.", Icons.Rounded.Layers to accents.orange),
        Triple(CardStyle.Glass, "See-through frosted cards. Best on a colorful background.", Icons.Rounded.AutoAwesome to accents.indigo),
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = "Appearance", onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
            ) {
                SectionHeader("Theme")
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    themeOptions.forEachIndexed { index, (value, label, iconAccent) ->
                        AppListRow(
                            headline = label,
                            leadingIcon = iconAccent.first,
                            leadingAccent = iconAccent.second,
                            modifier = Modifier.selectable(
                                selected = mode == value,
                                onClick = { ThemeModeStore.set(context, value) },
                                role = Role.RadioButton,
                            ),
                            trailing = { AppRadioButton(selected = mode == value, onClick = null) },
                        )
                        if (index < themeOptions.lastIndex) RowDivider(startIndent = Spacing.lg)
                    }
                }

                SectionHeader("Background")
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    PageBackgroundKind.entries.forEach { kind ->
                        BackgroundSwatch(
                            kind = kind,
                            selected = background == kind,
                            hasPhoto = hasPhoto,
                            photoRevision = wallpaperRevision,
                            busy = importing && kind == PageBackgroundKind.Custom,
                            onClick = {
                                if (kind == PageBackgroundKind.Custom && !hasPhoto) {
                                    choosePhoto()
                                } else {
                                    PageBackgroundStore.set(context, kind)
                                }
                            },
                        )
                    }
                }
                if (hasPhoto) {
                    Spacer(Modifier.height(Spacing.sm))
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        AppListRow(
                            headline = "Change photo",
                            leadingIcon = Icons.Rounded.PhotoLibrary,
                            leadingAccent = accents.blue,
                            onClick = ::choosePhoto,
                        )
                        RowDivider(startIndent = Spacing.lg)
                        AppListRow(
                            headline = "Remove photo",
                            leadingIcon = Icons.Rounded.Delete,
                            leadingAccent = accents.red,
                            onClick = {
                                if (background == PageBackgroundKind.Custom) {
                                    PageBackgroundStore.set(context, PageBackgroundKind.Plain)
                                }
                                CustomWallpaper.delete(context)
                            },
                        )
                    }
                }

                SectionHeader("Card style")
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    styleOptions.forEachIndexed { index, (style, description, iconAccent) ->
                        AppListRow(
                            headline = style.label,
                            supporting = description,
                            leadingIcon = iconAccent.first,
                            leadingAccent = iconAccent.second,
                            modifier = Modifier.selectable(
                                selected = cardStyle == style,
                                onClick = { CardStyleStore.set(context, style) },
                                role = Role.RadioButton,
                            ),
                            trailing = { AppRadioButton(selected = cardStyle == style, onClick = null) },
                        )
                        if (index < styleOptions.lastIndex) RowDivider(startIndent = Spacing.lg)
                    }
                }

                SectionHeader("Feedback")
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        headline = "Haptic feedback",
                        leadingIcon = Icons.Rounded.Vibration,
                        leadingAccent = accents.green,
                        modifier = Modifier.toggleable(
                            value = hapticsEnabled,
                            onValueChange = { HapticStore.set(context, it) },
                            role = Role.Switch,
                        ),
                        trailing = { AppSwitch(checked = hapticsEnabled, onCheckedChange = null) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** A phone-shaped live preview of one background, labelled underneath. */
@Composable
private fun BackgroundSwatch(
    kind: PageBackgroundKind,
    selected: Boolean,
    hasPhoto: Boolean,
    photoRevision: Long,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val dark = isAppInDarkTheme()
    val plain = MaterialTheme.colorScheme.background
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.divider
    val shape = MaterialTheme.shapes.medium
    val preview = remember(kind, dark, plain) {
        meshPalette(kind, dark)?.let(PageBackground::Mesh) ?: PageBackground.Plain(plain)
    }
    val label = if (kind == PageBackgroundKind.Custom && !hasPhoto) "Add photo" else kind.label

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = "$label background" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 64.dp, height = 112.dp)
                .clip(shape)
                .drawBehind { with(preview) { drawRegion(Offset.Zero, size, frosted = false) } }
                .border(if (selected) 2.dp else 1.dp, outline, shape),
        ) {
            if (GeneratedArt.isGenerated(kind)) {
                rememberArtThumbnail(kind, dark)?.let { art ->
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            if (kind == PageBackgroundKind.Custom) {
                val thumbnail = rememberPhotoThumbnail(hasPhoto, photoRevision)
                if (thumbnail != null) {
                    Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = if (busy) MaterialTheme.extendedColors.textMuted else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .background(MaterialTheme.extendedColors.card, MaterialTheme.shapes.extraLarge),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** A small decode of the stored photo for its swatch; reloads when the photo changes. */
@Composable
private fun rememberPhotoThumbnail(hasPhoto: Boolean, revision: Long): ImageBitmap? {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, hasPhoto, revision) {
        value = if (!hasPhoto) null else withContext(Dispatchers.IO) {
            CustomWallpaper.thumbnail(context, maxEdge = 256)?.asImageBitmap()
        }
    }
    return thumbnail
}

/** A small render of a generated background at the swatch's size (2× for sharpness). */
@Composable
private fun rememberArtThumbnail(kind: PageBackgroundKind, dark: Boolean): ImageBitmap? {
    val art by produceState<ImageBitmap?>(null, kind, dark) {
        value = withContext(Dispatchers.Default) { GeneratedArt.render(kind, dark, 192, 336).asImageBitmap() }
    }
    return art
}
