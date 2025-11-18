package net.tjalp.nexus.profile

import kotlinx.coroutines.flow.SharedFlow
import net.tjalp.nexus.profile.model.ProfileEntity
import java.util.*

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
     * @return The [ProfileEntity] if found or created, otherwise null.
     */
    suspend fun get(
        id: UUID,
        cache: Boolean = false,
        bypassCache: Boolean = false,
        allowCreation: Boolean = false
    ): ProfileEntity?

    /**
     * Inserts or updates a profile in the database.
     *
     * @param profile The profile snapshot to upsert.
     * @param cache Whether to cache the profile after upserting.
     * @param statements Additional custom statements to execute after the upsert.
     * @return The upserted [ProfileEntity].
     */
    suspend fun upsert(
        profile: ProfileEntity,
        cache: Boolean = true,
        vararg statements: () -> Unit
    ): ProfileEntity

    /**
     * Removes a profile from the cache.
     *
     * @param id The unique identifier of the profile to uncache.
     * @return The uncached [ProfileEntity] if it was present in the cache, otherwise null
     */
    fun uncache(id: UUID): ProfileEntity?
}