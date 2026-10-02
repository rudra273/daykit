package com.daykit.feature.keystore.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The plaintext shape of an entry's encrypted value column.
 *
 * [FORMAT_SINGLE] rows (every entry before typed entries existed, and any plain one-value
 * Key still) store the bare value string. [FORMAT_DOCUMENT] rows store this JSON:
 * `{"type":…, "fields":[{key,label,kind,value}], "usedCodes":[…]}`. The type lives inside
 * the ciphertext so the database doesn't reveal which entries are cards or wallets.
 */
data class KeyEntryDocument(
    val type: KeyType,
    val fields: List<KeyField>,
    val usedCodes: Set<String> = emptySet(),
) {
    /** True when this can be stored as a bare string, readable by older app versions too. */
    val isSingleValue: Boolean
        get() = type == KeyType.Simple &&
            usedCodes.isEmpty() &&
            fields.filter { it.value.isNotBlank() }.map { it.key } == listOf(KeyType.VALUE)

    fun toJson(): String = JSONObject()
        .put("type", type.storageKey)
        .put("fields", fieldsToJson(fields))
        .put("usedCodes", JSONArray(usedCodes.toList()))
        .toString()

    companion object {
        const val FORMAT_SINGLE = 1
        const val FORMAT_DOCUMENT = 2

        fun single(value: String) = KeyEntryDocument(
            type = KeyType.Simple,
            fields = normalize(
                KeyType.Simple,
                listOf(KeyField(KeyType.VALUE, "", KeyFieldKind.Secret, value)),
            ),
        )

        fun fromJson(json: String): KeyEntryDocument {
            val root = JSONObject(json)
            val type = KeyType.fromStorageKey(root.optString("type"))
            return KeyEntryDocument(
                type = type,
                fields = normalize(type, fieldsFromJson(root.optJSONArray("fields"))),
                usedCodes = stringSet(root.optJSONArray("usedCodes")),
            )
        }

        /** Blank fields are dropped: the template re-adds empty slots on read. */
        fun fieldsToJson(fields: List<KeyField>): JSONArray {
            val array = JSONArray()
            fields.filter { it.value.isNotBlank() }.forEach { field ->
                array.put(
                    JSONObject()
                        .put("key", field.key)
                        .put("label", field.label)
                        .put("kind", field.kind.storageKey)
                        .put("value", field.value),
                )
            }
            return array
        }

        fun fieldsFromJson(array: JSONArray?): List<KeyField> {
            if (array == null) return emptyList()
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                KeyField(
                    key = item.getString("key"),
                    label = item.optString("label"),
                    kind = KeyFieldKind.fromStorageKey(item.optString("kind")),
                    value = item.optString("value"),
                )
            }
        }

        fun stringSet(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()
            return (0 until array.length()).map { array.getString(it) }.toSet()
        }

        /**
         * Every template field first, in template order, with the template's current label and
         * kind (empty if unset); then every other field as stored. Keeps old entries working
         * when a template gains a field, and keeps fields that no template claims.
         */
        fun normalize(type: KeyType, fields: List<KeyField>): List<KeyField> {
            val byKey = fields.associateBy { it.key }
            val template = type.fields.map { spec ->
                KeyField(spec.key, spec.label, spec.kind, byKey[spec.key]?.value.orEmpty())
            }
            val templateKeys = type.fields.map { it.key }.toSet()
            return template + fields.filter { it.key !in templateKeys }
        }
    }
}
