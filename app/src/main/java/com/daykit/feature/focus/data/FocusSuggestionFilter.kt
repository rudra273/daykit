package com.daykit.feature.focus.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Apps the "Suggest apps to block" scan never offers, however heavily they're
 * used: communication, payments, 2FA, navigation and system plumbing. Locking
 * one of these by accident does real harm, and it's never what the user meant
 * by "distracting". This only shapes suggestions — the pickers still list
 * every app, so a user who really wants WhatsApp blocked can pick it.
 */
object FocusSuggestionFilter {

    /** Everything to hide from suggestions on this device. Touches PackageManager; call off the main thread. */
    fun excludedPackages(context: Context): Set<String> = buildSet {
        addAll(ESSENTIAL_PACKAGES)
        add(context.packageName)
        addAll(homeScreenPackages(context))
        // Role holders differ per OEM, so ask the system rather than guessing.
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
            .getOrNull()?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }
            .getOrNull()?.let(::add)
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.map { it.packageName }
        }.getOrNull()?.let(::addAll)
    }

    /** Launchers. Their "usage" is just time on the home screen, so it's never meaningful. */
    fun homeScreenPackages(context: Context): Set<String> =
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_ALL,
            )
            .map { it.activityInfo.packageName }
            .toSet()

    val ESSENTIAL_PACKAGES: Set<String> = setOf(
        // Messaging
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.thoughtcrime.securesms",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        // Phone, contacts
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        "com.android.contacts",
        // Work communication
        "com.Slack",
        "com.microsoft.teams",
        "com.microsoft.office.outlook",
        "com.google.android.gm",
        "us.zoom.videomeetings",
        "com.google.android.apps.tachyon",
        "com.google.android.apps.meetings",
        // Payments
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.dreamplug.androidapp",
        "com.paypal.android.p2pmobile",
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        // Authenticators
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.authy.authy",
        // Navigation, rides
        "com.google.android.apps.maps",
        "com.waze",
        "com.ubercab",
        "com.olacabs.customer",
        "com.rapido.passenger",
        // Utilities
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.google.android.calendar",
        "com.samsung.android.calendar",
        "com.google.android.GoogleCamera",
        "com.sec.android.app.camera",
        "com.google.android.apps.nbu.files",
        "com.google.android.calculator",
        // System, accessibility
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.marvin.talkback",
        "com.google.android.apps.safetyhub",
    )
}
