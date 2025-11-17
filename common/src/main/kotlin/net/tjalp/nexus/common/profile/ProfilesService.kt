package net.tjalp.nexus.common.profile

import kotlinx.coroutines.flow.SharedFlow
import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.statements.UpsertStatement

interface ProfilesService {

    val updates: SharedFlow<ProfileEvent.Updated>

    suspend fun get(
        id: ProfileId,
        cache: Boolean = false,
        bypassCache: Boolean = false,
        allowCreation: Boolean = false
    ): ProfileSnapshot?

    suspend fun upsert(
        profile: ProfileSnapshot,
        cache: Boolean = true,
        statement: ProfilesTable.(UpsertStatement<Long>) -> Unit = {},
        vararg additionalStatements: () -> Unit
    ): ProfileSnapshot

    fun uncache(id: ProfileId): ProfileSnapshot?
}