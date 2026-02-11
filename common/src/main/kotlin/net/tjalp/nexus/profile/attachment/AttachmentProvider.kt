package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.profile.model.ProfileSnapshot
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*

/**
 * Provider interface for managing attachments associated with profiles.
 *
 * @param T The type of the attachment.
 */
interface AttachmentProvider<T> {

    /**
     * Initializes the attachment provider.
     */
    suspend fun init() {
        // optional
    }

    /**
     * Loads the attachment for the given profile.
     *
     * @param db The database instance to use for loading the attachment.
     * @param profile The [ProfileSnapshot] to load the attachment for.
     * @return The loaded attachment, or null if not found.
     */
    suspend fun load(db: Database, id: UUID): T?

    /**
     * Saves the attachment for the given profile.
     *
     * @param db The database instance to use for saving the attachment.
     * @param profile The [ProfileSnapshot] to save the attachment for.
     * @param value The attachment value to be saved.
     */
    suspend fun save(db: Database, id: UUID, value: T) {
        // optional
    }
}