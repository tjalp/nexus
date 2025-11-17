package net.tjalp.nexus.common.profile

import kotlinx.coroutines.flow.SharedFlow
import net.tjalp.nexus.common.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.statements.UpsertStatement

/**
 * Service interface for managing profiles.
 */
interface ProfilesService {

    /**
     * A flow of profile update events. Subscribe to/Collect this to receive updates when profiles are modified.
     */
    val updates: SharedFlow<ProfileEvent.Updated>

    /**
     * Retrieves a profile by its ID.
     *
     * @param id The unique identifier of the profile.
     * @param cache Whether to cache the profile after retrieval.
     * @param bypassCache Whether to bypass the cache when retrieving the profile.
     * @param allowCreation Whether to create the profile if it does not exist.
     * @return The [ProfileSnapshot] if found or created, otherwise null.
     */
    suspend fun get(
        id: ProfileId,
        cache: Boolean = false,
        bypassCache: Boolean = false,
        allowCreation: Boolean = false
    ): ProfileSnapshot?

    /**
     * Inserts or updates a profile in the database.
     *
     * @param profile The profile snapshot to upsert.
     * @param cache Whether to cache the profile after upserting.
     * @param statement Additional statements to apply during the upsert operation.
     * @param additionalStatements Additional custom statements to execute after the upsert.
     * @return The upserted [ProfileSnapshot].
     */
    suspend fun upsert(
        profile: ProfileSnapshot,
        cache: Boolean = true,
        statement: ProfilesTable.(UpsertStatement<Long>) -> Unit = {},
        vararg additionalStatements: () -> Unit
    ): ProfileSnapshot

    /**
     * Removes a profile from the cache.
     *
     * @param id The unique identifier of the profile to uncache.
     * @return The uncached [ProfileSnapshot] if it was present in the cache, otherwise null
     */
    fun uncache(id: ProfileId): ProfileSnapshot?
}