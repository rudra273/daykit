package com.daykit.feature.keystore.data

data class KeyStoreEntry(
    val id: Long,
    val entryId: String,
    val name: String,
    val label: String,
    val type: KeyType,
    val fields: List<KeyField>,
    /** Recovery codes the user has ticked off as used. */
    val usedCodes: Set<String> = emptySet(),
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
) {
    fun value(key: String): String = fields.firstOrNull { it.key == key }?.value.orEmpty()

    val recoveryCodes: List<String> get() = RecoveryCodes.parse(value(KeyType.CODES))

    /** What the list row's copy button copies, or null to hide the button. */
    val quickCopyValue: String?
        get() = if (type == KeyType.Recovery) {
            recoveryCodes.firstOrNull { it !in usedCodes }
        } else {
            type.quickCopyKeys.map(::value).firstOrNull { it.isNotBlank() }
        }
}

/** What the editor hands the repository; ids, versions and timestamps are the repository's. */
data class KeyDraft(
    val name: String,
    val label: String,
    val type: KeyType,
    val fields: List<KeyField>,
    val usedCodes: Set<String> = emptySet(),
)

object RecoveryCodes {
    /** One code per line. Lines, not spaces: some providers print codes as `1234 5678`. */
    fun parse(raw: String): List<String> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
