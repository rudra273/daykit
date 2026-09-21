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

    /**
     * Adds the built-in Social group once a supported social app is present.
     *
     * This deliberately only seeds a group that does not already exist: after
     * that, Social behaves exactly like every other group and any edits the user
     * makes are kept. Keeping a stable id also means an app upgrade or a return
     * visit to Focus cannot create duplicate default groups.
     */
    suspend fun ensureDefaultSocialGroup(installedPackageNames: Collection<String>) {
        if (dao.getGroup(FocusDefaultGroups.SOCIAL_ID) != null) return

        val socialPackages = FocusDefaultGroups.socialPackages(installedPackageNames)
        if (socialPackages.isEmpty()) return

        val now = System.currentTimeMillis()
        dao.upsertGroup(
            FocusGroupEntity(
                groupId = FocusDefaultGroups.SOCIAL_ID,
                name = FocusDefaultGroups.SOCIAL_NAME,
                colorIndex = FocusDefaultGroups.SOCIAL_COLOR_INDEX,
                packageNames = socialPackages.joinToString("\n"),
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

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
