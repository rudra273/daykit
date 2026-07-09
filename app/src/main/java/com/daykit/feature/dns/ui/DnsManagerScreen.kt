package com.daykit.feature.dns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import kotlinx.coroutines.launch

private const val PRIVATE_DNS_SETTINGS_ACTION = "android.settings.PRIVATE_DNS_SETTINGS"

private data class DnsProvider(
    val name: String,
    val hostname: String,
    val note: String,
    val accent: @Composable () -> Color,
)

private val HowToSteps = listOf(
    "Copy a provider hostname below.",
    "Tap \"Open Private DNS settings\".",
    "Choose \"Private DNS provider hostname\".",
    "Paste the hostname and save.",
)

@Composable
fun DnsManagerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrolledUnder by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4 }
    }
    var customDns by rememberSaveable { mutableStateOf("") }

    val providers = listOf(
        DnsProvider(
            name = "AdGuard DNS",
            hostname = "dns.adguard-dns.com",
            note = "Blocks many ads, trackers, and malicious domains.",
            accent = { MaterialTheme.extendedColors.accents.green },
        ),
        DnsProvider(
            name = "Cloudflare DNS",
            hostname = "one.one.one.one",
            note = "Fast privacy-focused DNS without ad blocking.",
            accent = { MaterialTheme.extendedColors.accents.orange },
        ),
        DnsProvider(
            name = "Google DNS",
            hostname = "dns.google",
            note = "Reliable public DNS from Google.",
            accent = { MaterialTheme.extendedColors.accents.blue },
        ),
    )

    fun snack(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun copyDns(hostname: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, hostname))
        snack("$label copied — paste it into Private DNS.")
    }

    fun openPrivateDnsSettings() {
        val text = if (context.openSettings(PRIVATE_DNS_SETTINGS_ACTION)) {
            "Private DNS settings opened."
        } else if (context.openSettings(Settings.ACTION_WIRELESS_SETTINGS)) {
            "Network settings opened. Look for Private DNS."
        } else {
            context.openSettings(Settings.ACTION_SETTINGS)
            "Settings opened. Search for Private DNS."
        }
        snack(text)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg, end = Spacing.lg,
                    top = Spacing.sm, bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Hero explainer
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccentIconTile(
                                icon = Icons.Rounded.Security,
                                accent = MaterialTheme.colorScheme.primary,
                                size = 44.dp,
                                iconSize = 24.dp,
                            )
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Private DNS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "No VPN, no proxy, no traffic capture. Android applies DNS only after you confirm it in Settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.extendedColors.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        PrimaryButton(
                            text = "Open Private DNS settings",
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = ::openPrivateDnsSettings,
                        )
                    }
                }

                item {
                    Text(
                        text = "Providers",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.extendedColors.textMuted,
                        modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
                    )
                }

                items(providers, key = { it.hostname }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onCopy = { copyDns(provider.hostname, provider.name) },
                    )
                }

                // Custom DNS
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccentIconTile(
                                icon = Icons.Rounded.Dns,
                                accent = MaterialTheme.extendedColors.accents.purple,
                                size = 40.dp,
                                iconSize = 22.dp,
                            )
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Custom DNS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Use any Private DNS hostname you trust.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.extendedColors.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        AppTextField(
                            value = customDns,
                            onValueChange = { customDns = it.trim() },
                            placeholder = "dns.example.com",
                            label = "Provider hostname",
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SecondaryButton(
                                text = "Copy",
                                enabled = customDns.isNotBlank(),
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = { copyDns(customDns, "Custom DNS") },
                            )
                            PrimaryButton(
                                text = "Open settings",
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                onClick = ::openPrivateDnsSettings,
                            )
                        }
                    }
                }

                // How to enable
                item {
                    AppCard(modifier = Modifier.padding(top = Spacing.sm)) {
                        Text(
                            text = "How to enable",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        HowToSteps.forEachIndexed { index, step ->
                            if (index > 0) Spacer(Modifier.height(Spacing.md))
                            StepRow(number = index + 1, text = step)
                        }
                    }
                }
            }

            AppTopBar(title = "DNS Manager", onBack = onBack, scrolledUnder = scrolledUnder)
        }
    }
}

@Composable
private fun ProviderCard(
    provider: DnsProvider,
    onCopy: () -> Unit,
) {
    val accent = provider.accent()
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(icon = Icons.Rounded.Dns, accent = accent, size = 40.dp, iconSize = 22.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = provider.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.extendedColors.inputField, MaterialTheme.shapes.small)
                .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = provider.hostname,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy ${provider.hostname}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primary.asAccentContainer(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun Context.openSettings(action: String): Boolean {
    return runCatching {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.fold(
        onSuccess = { true },
        onFailure = { false },
    )
}
