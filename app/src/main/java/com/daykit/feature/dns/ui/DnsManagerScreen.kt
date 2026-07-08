package com.daykit.feature.dns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daykit.core.ui.AppBackButton
import com.daykit.core.ui.Cyan
import com.daykit.core.ui.GlassBackground
import com.daykit.core.ui.MutedText
import com.daykit.core.ui.PrimaryButton
import com.daykit.core.ui.SecondaryButton
import com.daykit.core.ui.SoftText
import com.daykit.core.ui.glassSurface

private const val PRIVATE_DNS_SETTINGS_ACTION = "android.settings.PRIVATE_DNS_SETTINGS"

private data class DnsProvider(
    val name: String,
    val hostname: String,
    val note: String,
)

private val DnsProviders = listOf(
    DnsProvider(
        name = "AdGuard DNS",
        hostname = "dns.adguard-dns.com",
        note = "Blocks many ads, trackers, and malicious domains.",
    ),
    DnsProvider(
        name = "Cloudflare DNS",
        hostname = "one.one.one.one",
        note = "Fast privacy-focused DNS without ad blocking.",
    ),
    DnsProvider(
        name = "Google DNS",
        hostname = "dns.google",
        note = "Reliable public DNS from Google.",
    ),
)

@Composable
fun DnsManagerScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var customDns by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Tap a provider to copy it and open Private DNS settings.") }

    fun copyDns(hostname: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, hostname))
        message = "$label copied. Paste it into Private DNS provider hostname."
    }

    fun openPrivateDnsSettings() {
        message = if (context.openSettings(PRIVATE_DNS_SETTINGS_ACTION)) {
            "Private DNS settings opened."
        } else if (context.openSettings(Settings.ACTION_WIRELESS_SETTINGS)) {
            "Network settings opened. Look for Private DNS."
        } else {
            context.openSettings(Settings.ACTION_SETTINGS)
            "Settings opened. Search for Private DNS."
        }
    }

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
                    Text(
                        text = "DNS Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
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
                GlassPanel(selected = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = Cyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Private DNS setup",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "No VPN, no proxy, no traffic capture. Android applies DNS only after you confirm it in Settings.",
                                color = MutedText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Text(
                    text = "Available DNS",
                    color = SoftText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )

                DnsProviders.forEach { provider ->
                    DnsProviderRow(
                        provider = provider,
                        onCopy = { copyDns(provider.hostname, provider.name) },
                        onOpenSettings = ::openPrivateDnsSettings,
                    )
                }

                GlassPanel(selected = customDns.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Dns, contentDescription = null, tint = Cyan, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Custom DNS",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Use any Private DNS hostname you trust.",
                                    color = MutedText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = customDns,
                            onValueChange = { customDns = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Provider hostname") },
                            placeholder = { Text("dns.example.com") },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Cyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Cyan,
                                focusedLabelColor = Cyan,
                                unfocusedLabelColor = MutedText,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                text = "Copy",
                                enabled = customDns.isNotBlank(),
                                leadingIcon = {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                },
                                onClick = { copyDns(customDns, "Custom DNS") },
                            )
                            PrimaryButton(
                                text = "Open Settings",
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                },
                                onClick = ::openPrivateDnsSettings,
                            )
                        }
                    }
                }

                Text(
                    text = message,
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DnsProviderRow(
    provider: DnsProvider,
    onCopy: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    GlassPanel(selected = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                .clickable(
                    onClick = {
                        onCopy()
                        onOpenSettings()
                    },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Dns, contentDescription = null, tint = Cyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = provider.hostname,
                        color = SoftText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = provider.note,
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = SoftText, modifier = Modifier.size(19.dp))
                }
            }
            PrimaryButton(
                text = "Open Private DNS Settings",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                },
                onClick = {
                    onCopy()
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun GlassPanel(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(18.dp),
                selected = selected,
                tintStrength = 0.08f,
                shadowElevation = 2f,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        content()
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
