package com.rudra.daykit.feature.settings.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rudra.daykit.core.ui.AppBackground
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.AppTheme
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.GlassMorphSettings
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.PREF_APP_BACKGROUND
import com.rudra.daykit.core.ui.PREF_APP_THEME
import com.rudra.daykit.core.ui.PREF_GLASS_BLUR
import com.rudra.daykit.core.ui.PREF_GLASS_OPACITY
import com.rudra.daykit.core.ui.SoftText
import com.rudra.daykit.core.ui.glassSurface
import kotlin.math.roundToInt

private fun AppTheme.displayName(): String = when (this) {
    AppTheme.SPACE_BLACK -> "Space Dark"
    AppTheme.SKY_BLUE -> "Sky Blue"
    AppTheme.ZINC_ROSE -> "Zinc Rose"
}

private fun AppBackground.displayName(): String = when (this) {
    AppBackground.DARK_GLASS -> "Dark Glass"
    AppBackground.LIGHT_GLASS -> "Light Glass"
    AppBackground.AURORA -> "Aurora"
}

@Composable
fun ThemeScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val defaultGlass = remember { GlassMorphSettings() }
    var currentTheme by remember {
        mutableStateOf(sharedPrefs.getString(PREF_APP_THEME, AppTheme.SPACE_BLACK.name) ?: AppTheme.SPACE_BLACK.name)
    }
    var currentBackground by remember {
        mutableStateOf(sharedPrefs.getString(PREF_APP_BACKGROUND, AppBackground.AURORA.name) ?: AppBackground.AURORA.name)
    }
    var glassOpacity by remember { mutableStateOf(sharedPrefs.getFloat(PREF_GLASS_OPACITY, defaultGlass.opacity)) }
    var glassBlur by remember { mutableStateOf(sharedPrefs.getFloat(PREF_GLASS_BLUR, defaultGlass.blur)) }
    val selectedTheme = runCatching { AppTheme.valueOf(currentTheme) }.getOrDefault(AppTheme.SPACE_BLACK)
    val selectedBackground = runCatching { AppBackground.valueOf(currentBackground) }.getOrDefault(AppBackground.AURORA)

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBackButton(onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text("Theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "THEME SETTINGS",
                    color = SoftText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            selected = true,
                            tintStrength = 0.08f,
                            shadowElevation = 2f,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = Cyan,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Theme",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = selectedTheme.displayName(),
                                color = MutedText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(AppTheme.SPACE_BLACK, AppTheme.SKY_BLUE, AppTheme.ZINC_ROSE).forEach { theme ->
                            val selected = currentTheme == theme.name
                            Button(
                                onClick = {
                                    sharedPrefs.edit().putString(PREF_APP_THEME, theme.name).apply()
                                    currentTheme = theme.name
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Cyan.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.06f),
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else SoftText,
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                            ) {
                                Text(
                                    text = theme.displayName(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Text(
                    "BACKGROUND",
                    color = SoftText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            selected = false,
                            tintStrength = 0.08f,
                            shadowElevation = 2f,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = selectedBackground.displayName(),
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(AppBackground.AURORA, AppBackground.DARK_GLASS, AppBackground.LIGHT_GLASS).forEach { background ->
                            val selected = currentBackground == background.name
                            Button(
                                onClick = {
                                    sharedPrefs.edit().putString(PREF_APP_BACKGROUND, background.name).apply()
                                    currentBackground = background.name
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Cyan.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.06f),
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else SoftText,
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                            ) {
                                Text(
                                    text = background.displayName(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Text(
                    "GLASS FINISH",
                    color = SoftText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            selected = false,
                            tintStrength = 0.08f,
                            shadowElevation = 2f,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassControlSlider(
                        title = "Opacity",
                        value = glassOpacity,
                        onValueChange = { value ->
                            glassOpacity = value
                            sharedPrefs.edit().putFloat(PREF_GLASS_OPACITY, value).apply()
                        },
                        startLabel = "Clear",
                        endLabel = "Solid",
                    )
                    GlassControlSlider(
                        title = "Blur",
                        value = glassBlur,
                        onValueChange = { value ->
                            glassBlur = value
                            sharedPrefs.edit().putFloat(PREF_GLASS_BLUR, value).apply()
                        },
                        startLabel = "Sharp",
                        endLabel = "Frosted",
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun GlassControlSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    startLabel: String,
    endLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = SoftText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${value.roundToInt()}%",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Slider(
            value = value.coerceIn(0f, 100f),
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Cyan,
                activeTrackColor = Cyan,
                inactiveTrackColor = SoftText.copy(alpha = 0.18f),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = startLabel,
                color = MutedText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = endLabel,
                color = MutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
