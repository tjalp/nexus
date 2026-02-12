package net.tjalp.nexus.profile.attachment

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*

/**
 * Registry for attachment providers.
 * This object maintains a mapping of attachment keys to their corresponding providers.
 */
object AttachmentRegistry {

    private val providers = mutableSetOf<AttachmentProvider<out ProfileAttachment>>()

    /**
     * Registers the given attachment provider to the registry.
     *
     * @param provider The attachment provider to be registered.
     */
    fun <T : ProfileAttachment> register(provider: AttachmentProvider<T>) {
        providers += provider
    }

    /**
     * Unregisters the given attachment provider from the registry.
     *
     * @param provider The attachment provider to be unregistered.
     */
    fun <T : ProfileAttachment> unregister(provider: AttachmentProvider<T>) {
        providers -= provider
    }

    /**
     * Retrieves all attachments for the given profile.
     *
     * @param db The database instance to use for loading attachments.
     * @param id The unique identifier of the profile to retrieve attachments for.
     * @return A collection of all attachments associated with the profile.
     */
    suspend fun getAttachments(db: Database, id: UUID): Collection<ProfileAttachment> = coroutineScope {
        return@coroutineScope providers.map { provider ->
            async {
                provider.load(db, id)
            }
        }.awaitAll().filterNotNull()
    }
}