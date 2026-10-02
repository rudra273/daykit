package com.daykit.feature.keystore.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * When a card or ID document expires, and when to remind about it. Pure, so the dates are
 * unit-tested; [com.daykit.feature.keystore.notification.KeyExpiryReminders] schedules them.
 */
object KeyExpiry {
    enum class Kind(val storageKey: String) {
        Card("card"),
        Document("document"),
        ;

        companion object {
            fun fromStorageKey(key: String?): Kind? = entries.firstOrNull { it.storageKey == key }
        }
    }

    data class Reminder(
        val kind: Kind,
        /** Last day the card or document is valid. */
        val expiresOn: LocalDate,
        /** Day the notification fires. */
        val remindOn: LocalDate,
    )

    /** ID documents get a month's notice. */
    const val DOCUMENT_NOTICE_DAYS = 30L

    /** Many countries refuse entry on a passport with under six months left, so warn earlier. */
    const val PASSPORT_NOTICE_MONTHS = 6L

    /** The last valid day, or null when the entry has no (valid) expiry. */
    fun expiresOn(entry: KeyStoreEntry): LocalDate? = when (entry.type) {
        KeyType.Card -> cardExpiry(entry.value("expiry"))
        KeyType.Identity -> KeyFieldFormat.parseDate(entry.value("expires"))
        else -> null
    }

    fun isExpired(entry: KeyStoreEntry, today: LocalDate = LocalDate.now()): Boolean =
        expiresOn(entry)?.isBefore(today) == true

    /** A card printed `08/28` is valid through the last day of August 2028. */
    fun cardExpiry(stored: String): LocalDate? {
        if (!KeyFieldFormat.isValidExpiry(stored)) return null
        val (month, year) = stored.split('/').map { it.toInt() }
        return YearMonth.of(2000 + year, month).atEndOfMonth()
    }

    /**
     * The reminder to schedule, or null when there's nothing to remind about: no expiry, or
     * the reminder day has already passed (the user is looking at the entry right now, or
     * was already told), so saving never fires an instant notification.
     */
    fun reminderFor(entry: KeyStoreEntry, today: LocalDate = LocalDate.now()): Reminder? {
        if (entry.deletedAtMillis != null) return null
        val expiresOn = expiresOn(entry) ?: return null
        val reminder = when (entry.type) {
            // New cards usually arrive during the expiry month, so remind on its first day.
            KeyType.Card -> Reminder(Kind.Card, expiresOn, expiresOn.withDayOfMonth(1))
            KeyType.Identity -> {
                val remindOn = if (entry.value("doc_type") == "Passport") {
                    expiresOn.minusMonths(PASSPORT_NOTICE_MONTHS)
                } else {
                    expiresOn.minusDays(DOCUMENT_NOTICE_DAYS)
                }
                Reminder(Kind.Document, expiresOn, remindOn)
            }
            else -> return null
        }
        return reminder.takeIf { it.remindOn.isAfter(today) }
    }
}
