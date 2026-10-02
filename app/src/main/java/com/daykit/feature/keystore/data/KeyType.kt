package com.daykit.feature.keystore.data

/**
 * How a field is entered, stored and shown. Kinds marked [isSecret] are masked until the
 * user taps the eye, and are never matched by search.
 */
enum class KeyFieldKind(val storageKey: String, val isSecret: Boolean) {
    Text("text", isSecret = false),
    Url("url", isSecret = false),
    Secret("secret", isSecret = true),

    /** Short numeric secret: CVV, ATM PIN, wallet PIN. */
    Pin("pin", isSecret = true),

    /** Digits only; grouped 4-4-4-4 (Amex 4-6-5); masked to the last four. */
    CardNumber("card_number", isSecret = true),

    /** Account / document number; masked to the last four. */
    MaskedId("masked_id", isSecret = true),

    /** Card expiry, stored as `MM/YY`. */
    Expiry("expiry", isSecret = false),

    /** Calendar date, stored as ISO `yyyy-MM-dd`. */
    Date("date", isSecret = false),

    /** One of [KeyFieldSpec.options]. */
    Choice("choice", isSecret = false),

    /** Whitespace-separated words. */
    SeedPhrase("seed_phrase", isSecret = true),

    /** One code per line; see [RecoveryCodes]. */
    RecoveryCodes("recovery_codes", isSecret = true),
    ;

    companion object {
        /** Unknown kinds (a backup from a newer version) fall back to masked, never to plain. */
        fun fromStorageKey(key: String?): KeyFieldKind =
            entries.firstOrNull { it.storageKey == key } ?: Secret
    }
}

data class KeyFieldSpec(
    val key: String,
    val label: String,
    val kind: KeyFieldKind,
    val options: List<String> = emptyList(),
    /** Offer the password generator under this field. */
    val generator: Boolean = false,
    val uppercase: Boolean = false,
)

// Top-level so the enum entries can use them: the companion isn't initialised yet there.
private const val VALUE_KEY = "value"
private const val CODES_KEY = "codes"

/**
 * The shape of an entry. A type only chooses which fields the form starts with; every
 * entry can carry extra user-added fields on top. [storageKey] is persisted (encrypted)
 * and in backups — never rename it.
 */
enum class KeyType(
    val storageKey: String,
    val title: String,
    val nameLabel: String,
    val fields: List<KeyFieldSpec>,
    /** Fields the list row's copy button may copy, in priority order. */
    val quickCopyKeys: List<String>,
) {
    Simple(
        storageKey = "key",
        title = "Key",
        nameLabel = "Key name",
        fields = listOf(KeyFieldSpec(VALUE_KEY, "Value", KeyFieldKind.Secret, generator = true)),
        quickCopyKeys = listOf(VALUE_KEY),
    ),
    Login(
        storageKey = "login",
        title = "Login",
        nameLabel = "Site or app",
        fields = listOf(
            KeyFieldSpec("username", "Username or email", KeyFieldKind.Text),
            KeyFieldSpec("password", "Password", KeyFieldKind.Secret, generator = true),
            KeyFieldSpec("website", "Website", KeyFieldKind.Url),
        ),
        quickCopyKeys = listOf("password", "username"),
    ),
    Card(
        storageKey = "card",
        title = "Card",
        nameLabel = "Card name",
        fields = listOf(
            KeyFieldSpec("number", "Card number", KeyFieldKind.CardNumber),
            KeyFieldSpec("holder", "Name on card", KeyFieldKind.Text, uppercase = true),
            KeyFieldSpec("expiry", "Expiry (MM/YY)", KeyFieldKind.Expiry),
            KeyFieldSpec("cvv", "CVV", KeyFieldKind.Pin),
            KeyFieldSpec("pin", "ATM PIN", KeyFieldKind.Pin),
        ),
        quickCopyKeys = listOf("number"),
    ),
    Bank(
        storageKey = "bank",
        title = "Bank",
        nameLabel = "Bank name",
        fields = listOf(
            KeyFieldSpec("account", "Account number", KeyFieldKind.MaskedId),
            KeyFieldSpec("holder", "Account holder", KeyFieldKind.Text),
            KeyFieldSpec("ifsc", "IFSC", KeyFieldKind.Text, uppercase = true),
            KeyFieldSpec("branch", "Branch", KeyFieldKind.Text),
            KeyFieldSpec("upi", "UPI ID", KeyFieldKind.Text),
            KeyFieldSpec("customer_id", "Customer ID", KeyFieldKind.Text),
        ),
        quickCopyKeys = listOf("account"),
    ),
    Identity(
        storageKey = "identity",
        title = "ID",
        nameLabel = "Document name",
        fields = listOf(
            KeyFieldSpec(
                "doc_type",
                "Document",
                KeyFieldKind.Choice,
                options = listOf("Aadhaar", "PAN", "Passport", "Driving licence", "Voter ID", "Other"),
            ),
            KeyFieldSpec("number", "Document number", KeyFieldKind.MaskedId, uppercase = true),
            KeyFieldSpec("holder", "Name on document", KeyFieldKind.Text),
            KeyFieldSpec("issued", "Issued on", KeyFieldKind.Date),
            KeyFieldSpec("expires", "Valid until", KeyFieldKind.Date),
        ),
        quickCopyKeys = listOf("number"),
    ),
    Recovery(
        storageKey = "recovery_codes",
        title = "Recovery codes",
        nameLabel = "Account",
        fields = listOf(KeyFieldSpec(CODES_KEY, "Codes (one per line)", KeyFieldKind.RecoveryCodes)),
        // The list row copies the next unused code instead; see [KeyStoreEntry.quickCopyValue].
        quickCopyKeys = emptyList(),
    ),
    Crypto(
        storageKey = "crypto",
        title = "Crypto wallet",
        nameLabel = "Wallet name",
        fields = listOf(
            KeyFieldSpec("seed", "Recovery phrase", KeyFieldKind.SeedPhrase),
            KeyFieldSpec("passphrase", "Passphrase", KeyFieldKind.Secret),
            KeyFieldSpec("address", "Wallet address", KeyFieldKind.Text),
            KeyFieldSpec("pin", "Wallet PIN", KeyFieldKind.Pin),
        ),
        // Only the public address: a one-tap seed-phrase copy from the list is too easy to misfire.
        quickCopyKeys = listOf("address"),
    ),
    ;

    fun spec(key: String): KeyFieldSpec? = fields.firstOrNull { it.key == key }

    companion object {
        const val VALUE = VALUE_KEY
        const val CODES = CODES_KEY
        const val CUSTOM_PREFIX = "x_"

        fun fromStorageKey(key: String?): KeyType = entries.firstOrNull { it.storageKey == key } ?: Simple
    }
}

/**
 * One stored value. Template fields use their spec's [key]; user-added fields use a
 * [KeyType.CUSTOM_PREFIX] key and carry their own [label] and [kind] (Text or Secret).
 */
data class KeyField(
    val key: String,
    val label: String,
    val kind: KeyFieldKind,
    val value: String,
) {
    val isCustom: Boolean get() = key.startsWith(KeyType.CUSTOM_PREFIX)
}
