package com.daykit.feature.focus.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FocusGroupRepository(
    private val dao: FocusGroupDao,
) {
    fun observeGroups(): Flow<List<FocusGroup>> =
        dao.observeGroups().map { rows -> rows.map { it.toGroup() } }

    suspend fun getGroups(): List<FocusGroup> = dao.getGroups().map { it.toGroup() }

    suspend fun getGroup(groupId: String): FocusGroup? = dao.getGroup(groupId)?.toGroup()

    /** Creates a group when [groupId] is null, otherwise updates that one in place. */
    suspend fun saveGroup(
        groupId: String? = null,
        name: String,
        colorIndex: Int,
        packageNames: List<String>,
    ): String {
        val now = System.currentTimeMillis()
        val id = groupId ?: UUID.randomUUID().toString()
        val existing = groupId?.let { dao.getGroup(it) }
        dao.upsertGroup(
            FocusGroupEntity(
                id = existing?.id ?: 0,
                groupId = id,
                name = name.trim(),
                colorIndex = colorIndex,
                packageNames = packageNames.distinct().joinToString("\n"),
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
            ),
        )
        return id
    }

    suspend fun deleteGroup(groupId: String) = dao.deleteGroup(groupId)
}
